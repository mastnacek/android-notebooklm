package dev.jara.notebooklm.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jara.notebooklm.auth.AuthManager
import dev.jara.notebooklm.rpc.NotebookLmApi
import dev.jara.notebooklm.search.EmbeddingDb
import dev.jara.notebooklm.search.OpenRouterEmbedding
import android.webkit.CookieManager
import io.ktor.client.*
import kotlinx.serialization.json.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Navigacni stav aplikace */
sealed class Screen {
    object Login : Screen()
    object NotebookList : Screen()
    data class NotebookDetail(val notebook: NotebookLmApi.Notebook) : Screen()
}

/** Tab v detailu notebooku */
enum class DetailTab { CHAT, SOURCES, ARTIFACTS, NOTES }

/** Razeni seznamu sesitu — jako Rust NotebookSort */
enum class NotebookSort(val label: String) {
    DEFAULT("datum"),
    NAME_ASC("A-Z"),
    NAME_DESC("Z-A"),
    CATEGORY("kat.");

    fun next(): NotebookSort = entries[(ordinal + 1) % entries.size]
}

/** Stav audio prehravace */
data class AudioPlayerState(
    val url: String,
    val title: String,
    val cookies: String,
)

/** Stav stahovani artefaktu */
data class DownloadState(
    val artifactId: String,
    val progress: Float, // 0..1, -1 = indeterminate
    val error: String? = null,
    val done: Boolean = false,
    val filePath: String? = null,
)

/** Skupina duplikatu v jednom sesitu */
data class DuplicateGroup(
    val title: String,
    val count: Int,
    val deleteIds: List<String>, // ID zdrojů ke smazání (všechny kromě prvního)
)

/** Stav deduplikace */
data class DeduplicationState(
    val running: Boolean = false,
    val currentNotebook: String = "",
    val progress: String = "",
    val groups: List<DuplicateGroup> = emptyList(),
    val totalDeleted: Int = 0,
    val done: Boolean = false,
    val error: String? = null,
)

/** Stav AI klasifikace */
data class ClassificationState(
    val running: Boolean = false,
    val progress: String = "",
    val results: Map<String, String> = emptyMap(), // notebookId -> kategorie
    val done: Boolean = false,
    val error: String? = null,
)

data class DetailState(
    val sources: List<NotebookLmApi.Source> = emptyList(),
    val summary: String? = null,
    val loading: Boolean = true,
    val artifacts: List<NotebookLmApi.Artifact> = emptyList(),
    val notes: List<NotebookLmApi.Note> = emptyList(),
    val chatMessages: List<NotebookLmApi.ChatMessage> = emptyList(),
    val chatAnswering: Boolean = false,
    val conversationId: String = java.util.UUID.randomUUID().toString(),
    val tab: DetailTab = DetailTab.CHAT,
    val audioPlayer: AudioPlayerState? = null,
    val downloads: Map<String, DownloadState> = emptyMap(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "AppViewModel"
    }

    private val authManager = AuthManager(app)
    private val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 120_000  // 2 min — chat muze trvat dlouho
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }
    private val embeddingDb = EmbeddingDb(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val favPrefs = app.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", 0)) { ThemeMode.SYSTEM }
    )
    val themeMode: StateFlow<ThemeMode> get() = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
    }

    private val _notebookSort = MutableStateFlow(NotebookSort.DEFAULT)
    val notebookSort: StateFlow<NotebookSort> get() = _notebookSort

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> get() = _favorites

    private val _semanticResults = MutableStateFlow<List<String>?>(null)
    val semanticResults: StateFlow<List<String>?> get() = _semanticResults

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> get() = _searchLoading

    private val _embeddingStatus = MutableStateFlow<String?>(null)
    val embeddingStatus: StateFlow<String?> get() = _embeddingStatus

    private val _dedup = MutableStateFlow(DeduplicationState())
    val dedup: StateFlow<DeduplicationState> get() = _dedup

    private val _detailDedup = MutableStateFlow(DeduplicationState())
    val detailDedup: StateFlow<DeduplicationState> get() = _detailDedup

    private val _classify = MutableStateFlow(ClassificationState())
    val classify: StateFlow<ClassificationState> get() = _classify

    // Kategorie notebooku (persisted v SharedPreferences)
    private val catPrefs = app.getSharedPreferences("categories", Context.MODE_PRIVATE)
    private val _categories = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _screen = MutableStateFlow<Screen>(
        if (authManager.isLoggedIn()) Screen.NotebookList else Screen.Login
    )
    val screen: StateFlow<Screen> get() = _screen

    private val _notebooks = MutableStateFlow<List<NotebookLmApi.Notebook>>(emptyList())
    val notebooks: StateFlow<List<NotebookLmApi.Notebook>> get() = _notebooks

    private val _notebooksLoading = MutableStateFlow(false)
    val notebooksLoading: StateFlow<Boolean> get() = _notebooksLoading

    private val _detail = MutableStateFlow(DetailState())
    val detail: StateFlow<DetailState> get() = _detail

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> get() = _error

    private val _interactiveHtml = MutableStateFlow<String?>(null)
    val interactiveHtml: StateFlow<String?> get() = _interactiveHtml

    fun dismissInteractiveHtml() { _interactiveHtml.value = null }

    val categories: StateFlow<Map<String, String>> get() = _categories

    init {
        // Nacti oblibene z prefs
        _favorites.value = favPrefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
        // Nacti kategorie (normalizuj case)
        val rawCats = catPrefs.all.mapNotNull { (k, v) ->
            if (v is String) k to v.trim().lowercase().replaceFirstChar { it.uppercase() } else null
        }.toMap()
        _categories.value = rawCats
        // Oprav data v prefs pokud se lisi
        val editor = catPrefs.edit()
        for ((k, normalized) in rawCats) {
            val original = catPrefs.getString(k, null)
            if (original != null && original != normalized) {
                editor.putString(k, normalized)
            }
        }
        editor.apply()
        if (authManager.isLoggedIn()) {
            loadNotebooks()
        }
    }

    fun onLoginSuccess(cookies: String) {
        authManager.saveCookies(cookies)
        viewModelScope.launch {
            try {
                val tokens = authManager.fetchTokens(httpClient)
                if (tokens != null) {
                    _screen.value = Screen.NotebookList
                    loadNotebooks()
                } else {
                    _error.value = "Nelze extrahovat tokeny. Zkus znovu."
                }
            } catch (e: Exception) {
                _error.value = "Auth chyba: ${e.message}"
            }
        }
    }

    fun onLoginFailed(reason: String) {
        _error.value = reason
    }

    fun loadNotebooks() {
        val tokens = authManager.loadTokens() ?: return
        _notebooksLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                _notebooks.value = api.listNotebooks()
            } catch (e: Exception) {
                Log.e(TAG, "loadNotebooks", e)
                _error.value = "Chyba: ${e.message}"
            } finally {
                _notebooksLoading.value = false
            }
        }
    }

    fun openNotebook(nb: NotebookLmApi.Notebook) {
        val tokens = authManager.loadTokens() ?: return
        _screen.value = Screen.NotebookDetail(nb)
        _detail.value = DetailState(loading = true)

        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                val sources = api.getSources(nb.id)
                val summary = api.getSummary(nb.id)
                val artifacts = try { api.listArtifacts(nb.id) } catch (e: Exception) {
                    Log.w(TAG, "listArtifacts: ${e.message}")
                    emptyList()
                }
                val notes = try { api.listNotes(nb.id) } catch (e: Exception) {
                    Log.w(TAG, "listNotes: ${e.message}")
                    emptyList()
                }
                // Detekuj uz stazene artefakty
                val existingDownloads = detectDownloadedArtifacts(artifacts)

                _detail.value = DetailState(
                    sources = sources,
                    summary = summary,
                    loading = false,
                    artifacts = artifacts,
                    notes = notes,
                    downloads = existingDownloads,
                )
            } catch (e: Exception) {
                Log.e(TAG, "openNotebook", e)
                _detail.value = DetailState(loading = false)
                _error.value = "Chyba: ${e.message}"
            }
        }
    }

    fun switchTab(tab: DetailTab) {
        _detail.value = _detail.value.copy(tab = tab)
    }

    fun sendChat(question: String) {
        val tokens = authManager.loadTokens() ?: return
        val current = _detail.value
        if (question.isBlank() || current.chatAnswering) return

        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        val updatedMessages = current.chatMessages + NotebookLmApi.ChatMessage(
            NotebookLmApi.ChatRole.USER, question
        )
        _detail.value = current.copy(chatMessages = updatedMessages, chatAnswering = true)

        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                val answer = api.sendChat(
                    notebookId = nb.id,
                    sources = current.sources,
                    question = question,
                    history = updatedMessages,
                    conversationId = current.conversationId,
                )
                val withAnswer = updatedMessages + NotebookLmApi.ChatMessage(
                    NotebookLmApi.ChatRole.ASSISTANT, answer
                )
                _detail.value = _detail.value.copy(chatMessages = withAnswer, chatAnswering = false)
            } catch (e: Exception) {
                Log.e(TAG, "sendChat", e)
                _detail.value = _detail.value.copy(chatAnswering = false)
                _error.value = "Chat chyba: ${e.message}"
            }
        }
    }

    // ── Notes ──

    /** Ulozi text (typicky AI odpoved) jako poznamku do aktualniho notebooku */
    fun saveAsNote(text: String) {
        val tokens = authManager.loadTokens() ?: return
        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return

        // Titulek = prvnich ~50 znaku bez markdown
        val title = text.lineSequence()
            .map { it.trimStart('#', ' ', '*', '-') }
            .firstOrNull { it.isNotBlank() }
            ?.take(60)
            ?: "Poznámka"

        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                val noteId = api.createNote(nb.id, title, text)
                Log.i(TAG, "saveAsNote: created note $noteId in ${nb.id}")
                // Refresh notes
                val notes = try { api.listNotes(nb.id) } catch (_: Exception) { emptyList() }
                _detail.value = _detail.value.copy(notes = notes)
                _error.value = "Poznámka uložena: ${title.take(30)}"
            } catch (e: Exception) {
                Log.e(TAG, "saveAsNote", e)
                _error.value = "Chyba při ukládání poznámky: ${e.message}"
            }
        }
    }

    // ── Notebook management ──

    fun createNotebook(title: String, emoji: String = "") {
        val tokens = authManager.loadTokens() ?: return
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                val nb = api.createNotebook(title, emoji)
                if (nb != null) {
                    loadNotebooks()
                    _error.value = "Sešit vytvořen: ${nb.title}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "createNotebook", e)
                _error.value = "Chyba: ${e.message}"
            }
        }
    }

    fun deleteNotebook(notebookId: String) {
        val tokens = authManager.loadTokens() ?: return
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                api.deleteNotebook(notebookId)
                _notebooks.value = _notebooks.value.filter { it.id != notebookId }
                _error.value = "Sešit smazán"
            } catch (e: Exception) {
                Log.e(TAG, "deleteNotebook", e)
                _error.value = "Chyba: ${e.message}"
            }
        }
    }

    // ── Source management ──

    fun addSource(type: String, value: String, title: String = "") {
        val tokens = authManager.loadTokens() ?: return
        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                val sourceId = when (type) {
                    "url" -> api.addSourceUrl(nb.id, value)
                    "youtube" -> api.addSourceYoutube(nb.id, value)
                    "text" -> api.addSourceText(nb.id, title.ifBlank { "Poznámka" }, value)
                    else -> null
                }
                if (sourceId != null) {
                    // Refresh sources
                    val sources = api.getSources(nb.id)
                    _detail.value = _detail.value.copy(sources = sources)
                    _error.value = "Zdroj přidán"
                }
            } catch (e: Exception) {
                Log.e(TAG, "addSource", e)
                _error.value = "Chyba přidání zdroje: ${e.message}"
            }
        }
    }

    fun deleteSource(sourceId: String) {
        val tokens = authManager.loadTokens() ?: return
        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                api.deleteSource(nb.id, sourceId)
                _detail.value = _detail.value.copy(
                    sources = _detail.value.sources.filter { it.id != sourceId }
                )
            } catch (e: Exception) {
                Log.e(TAG, "deleteSource", e)
                _error.value = "Chyba: ${e.message}"
            }
        }
    }

    fun deleteSources(sourceIds: Set<String>) {
        val tokens = authManager.loadTokens() ?: return
        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        viewModelScope.launch {
            val api = NotebookLmApi(httpClient, tokens)
            for (id in sourceIds) {
                try {
                    api.deleteSource(nb.id, id)
                    _detail.value = _detail.value.copy(
                        sources = _detail.value.sources.filter { it.id != id }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "deleteSources: $id", e)
                }
            }
        }
    }

    fun dedupCurrentNotebook() {
        val tokens = authManager.loadTokens() ?: return
        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        _detailDedup.value = DeduplicationState(running = true, currentNotebook = nb.title, progress = "1/1")
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                val sources = api.getSources(nb.id)
                if (sources.size < 2) {
                    _detailDedup.value = DeduplicationState(done = true, totalDeleted = 0, progress = "hotovo")
                    return@launch
                }
                val byTitle = sources.groupBy { it.title }
                val groups = mutableListOf<DuplicateGroup>()
                var totalDeleted = 0

                for ((title, group) in byTitle) {
                    if (group.size <= 1) continue
                    val allText = group.all { it.type == NotebookLmApi.SourceType.TEXT }
                    if (allText) {
                        val hashGroups = mutableMapOf<String, MutableList<String>>()
                        for (src in group) {
                            val hash = try {
                                api.getSourceFulltext(nb.id, src.id).hashCode().toString(16)
                            } catch (_: Exception) { "unique_${src.id}" }
                            hashGroups.getOrPut(hash) { mutableListOf() }.add(src.id)
                        }
                        for ((_, ids) in hashGroups) {
                            if (ids.size > 1) groups.add(DuplicateGroup(title, ids.size, ids.drop(1)))
                        }
                    } else {
                        val ids = group.map { it.id }
                        groups.add(DuplicateGroup(title, ids.size, ids.drop(1)))
                    }
                }

                if (groups.isEmpty()) {
                    _detailDedup.value = DeduplicationState(done = true, totalDeleted = 0, progress = "hotovo")
                    return@launch
                }

                _detailDedup.value = _detailDedup.value.copy(groups = groups)
                for (g in groups) {
                    for (srcId in g.deleteIds) {
                        try {
                            api.deleteSource(nb.id, srcId)
                            totalDeleted++
                            _detailDedup.value = _detailDedup.value.copy(totalDeleted = totalDeleted)
                            _detail.value = _detail.value.copy(
                                sources = _detail.value.sources.filter { it.id != srcId }
                            )
                        } catch (_: Exception) {}
                    }
                }
                _detailDedup.value = DeduplicationState(done = true, totalDeleted = totalDeleted, progress = "hotovo")
            } catch (e: Exception) {
                _detailDedup.value = DeduplicationState(error = "Chyba: ${e.message}")
            }
        }
    }

    fun dismissDetailDedup() {
        _detailDedup.value = DeduplicationState()
    }

    // ── Artifact generation ──

    fun deleteArtifact(artifactId: String) {
        val tokens = authManager.loadTokens() ?: return
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                api.deleteArtifact(artifactId)
                _detail.value = _detail.value.copy(
                    artifacts = _detail.value.artifacts.filter { it.id != artifactId }
                )
            } catch (e: Exception) {
                Log.e(TAG, "deleteArtifact", e)
                _error.value = "Chyba mazání artefaktu: ${e.message}"
            }
        }
    }

    fun generateArtifact(
        type: NotebookLmApi.GenerateType,
        options: NotebookLmApi.GenerateOptions = NotebookLmApi.GenerateOptions(),
    ) {
        val tokens = authManager.loadTokens() ?: return
        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        val sources = _detail.value.sources
        if (sources.isEmpty()) {
            _error.value = "Žádné zdroje pro generování"
            return
        }
        viewModelScope.launch {
            try {
                _error.value = "Generuji ${type.label}..."
                val api = NotebookLmApi(httpClient, tokens)
                api.generateArtifact(nb.id, sources, type, options)
                // Refresh artifacts
                val artifacts = api.listArtifacts(nb.id)
                _detail.value = _detail.value.copy(artifacts = artifacts, tab = DetailTab.ARTIFACTS)
                _error.value = "${type.label} se generuje..."
            } catch (e: Exception) {
                Log.e(TAG, "generateArtifact", e)
                _error.value = "Chyba: ${e.message}"
            }
        }
    }

    fun openInteractiveHtml(artifactId: String) {
        val tokens = authManager.loadTokens() ?: return
        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                val html = api.getInteractiveHtml(nb.id, artifactId)
                if (html != null) {
                    _interactiveHtml.value = html
                } else {
                    _error.value = "HTML obsah nenalezen"
                }
            } catch (e: Exception) {
                Log.e(TAG, "openInteractiveHtml", e)
                _error.value = "Chyba: ${e.message}"
            }
        }
    }

    // ── Conversation history ──

    fun loadConversationHistory() {
        val tokens = authManager.loadTokens() ?: return
        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        val convId = _detail.value.conversationId
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                val messages = api.getConversationTurns(nb.id, convId)
                if (messages.isNotEmpty()) {
                    _detail.value = _detail.value.copy(chatMessages = messages)
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadConversationHistory: ${e.message}")
            }
        }
    }

    fun deleteNote(noteId: String) {
        val tokens = authManager.loadTokens() ?: return
        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                api.deleteNote(nb.id, noteId)
                _detail.value = _detail.value.copy(
                    notes = _detail.value.notes.filter { it.id != noteId }
                )
            } catch (e: Exception) {
                Log.e(TAG, "deleteNote", e)
                _error.value = "Chyba: ${e.message}"
            }
        }
    }

    // ── Favorites & Sort ──

    fun toggleFavorite(notebookId: String) {
        val current = _favorites.value.toMutableSet()
        if (notebookId in current) current.remove(notebookId) else current.add(notebookId)
        _favorites.value = current
        favPrefs.edit().putStringSet("fav_ids", current).apply()
    }

    fun isFavorite(notebookId: String): Boolean = notebookId in _favorites.value

    fun cycleSort() {
        _notebookSort.value = _notebookSort.value.next()
    }

    /** Serazeny seznam — oblibene nahore, pak podle aktualniho razeni */
    fun sortedNotebooks(notebooks: List<NotebookLmApi.Notebook>): List<NotebookLmApi.Notebook> {
        val favs = _favorites.value
        val cats = _categories.value
        val sorted = when (_notebookSort.value) {
            NotebookSort.DEFAULT -> notebooks
            NotebookSort.NAME_ASC -> notebooks.sortedBy { it.title.lowercase() }
            NotebookSort.NAME_DESC -> notebooks.sortedByDescending { it.title.lowercase() }
            NotebookSort.CATEGORY -> notebooks.sortedBy { (cats[it.id] ?: "zzz").lowercase() }
        }
        // Favority nahore (stabilni razeni) — ale ne pri category groupovani
        return if (_notebookSort.value == NotebookSort.CATEGORY) sorted
        else sorted.sortedByDescending { it.id in favs }
    }

    fun downloadArtifact(artifact: NotebookLmApi.Artifact) {
        val url = artifact.url ?: return
        val ext = when (artifact.type) {
            NotebookLmApi.ArtifactType.AUDIO -> ".mp3"
            NotebookLmApi.ArtifactType.VIDEO -> ".mp4"
            NotebookLmApi.ArtifactType.SLIDE_DECK -> ".pdf"
            NotebookLmApi.ArtifactType.INFOGRAPHIC -> ".png"
            else -> ""
        }
        val fileName = "${artifact.title}$ext".replace("/", "_")

        val downloads = _detail.value.downloads.toMutableMap()
        downloads[artifact.id] = DownloadState(artifact.id, 0f)
        _detail.value = _detail.value.copy(downloads = downloads)

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Manualni redirect follow s domain-aware cookies pres HttpURLConnection
                    // (bez Ktor — ten ma timeout problemy na velkych souborech)
                    val cookieMgr = CookieManager.getInstance()
                    val ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
                    var currentUrl = url

                    // 1) Resolve redirecty — najdi finalni URL
                    var finalConn: java.net.HttpURLConnection? = null
                    for (i in 0 until 10) {
                        val domainCookies = cookieMgr.getCookie(currentUrl) ?: ""
                        Log.i(TAG, "download: step $i, url=${currentUrl.take(80)}")
                        Log.i(TAG, "download: cookies ${domainCookies.length} chars")

                        val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                        conn.instanceFollowRedirects = false
                        conn.setRequestProperty("Cookie", domainCookies)
                        conn.setRequestProperty("User-Agent", ua)
                        conn.connectTimeout = 15_000
                        conn.readTimeout = 5 * 60_000 // 5 min pro velke soubory
                        conn.connect()

                        val code = conn.responseCode
                        Log.i(TAG, "download: status=$code, type=${conn.contentType}")

                        if (code in 301..303 || code == 307 || code == 308) {
                            val location = conn.getHeaderField("Location")
                            conn.disconnect()
                            if (location != null) {
                                currentUrl = if (location.startsWith("http")) location
                                else {
                                    val base = currentUrl.substringBefore("://") + "://" +
                                        currentUrl.substringAfter("://").substringBefore("/")
                                    "$base$location"
                                }
                                continue
                            }
                        }

                        if (code !in 200..299) {
                            conn.disconnect()
                            throw RuntimeException("HTTP $code")
                        }

                        finalConn = conn
                        break
                    }

                    val conn = finalConn ?: throw RuntimeException("Too many redirects")
                    val contentLength = conn.contentLength.toLong()
                    Log.i(TAG, "download: streaming $contentLength bytes")

                    // Zkontroluj ze to neni HTML
                    val contentType = conn.contentType ?: ""
                    if (contentType.contains("text/html", ignoreCase = true)) {
                        conn.disconnect()
                        throw RuntimeException("Server vratil HTML misto audio (auth problem)")
                    }

                    // 2) Streamuj s progress reportingem do vybrane slozky
                    val savedUri = getDownloadPath()
                    val outputUri: Uri
                    val ctx = getApplication<Application>()

                    if (savedUri.isNotEmpty()) {
                        // SAF slozka vybrana uzivatelem
                        val treeUri = Uri.parse(savedUri)
                        val dir = DocumentFile.fromTreeUri(ctx, treeUri)
                            ?: throw RuntimeException("Nelze otevrit vybranou slozku")
                        val mime = when {
                            fileName.endsWith(".mp3") -> "audio/mpeg"
                            fileName.endsWith(".mp4") -> "video/mp4"
                            fileName.endsWith(".pdf") -> "application/pdf"
                            fileName.endsWith(".png") -> "image/png"
                            else -> "application/octet-stream"
                        }
                        // Smaz existujici soubor se stejnym jmenem
                        dir.findFile(fileName)?.delete()
                        val docFile = dir.createFile(mime, fileName)
                            ?: throw RuntimeException("Nelze vytvorit soubor v $savedUri")
                        outputUri = docFile.uri
                    } else {
                        // Fallback: Downloads/notebooklm
                        val downloadsDir = java.io.File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS
                            ),
                            "notebooklm"
                        )
                        downloadsDir.mkdirs()
                        val outFile = java.io.File(downloadsDir, fileName)
                        outputUri = Uri.fromFile(outFile)
                    }

                    conn.inputStream.use { input ->
                        ctx.contentResolver.openOutputStream(outputUri)!!.use { output ->
                            val buffer = ByteArray(8192)
                            var totalRead = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                totalRead += read
                                val prog = if (contentLength > 0) totalRead.toFloat() / contentLength else -1f
                                withContext(Dispatchers.Main) {
                                    val d = _detail.value.downloads.toMutableMap()
                                    d[artifact.id] = DownloadState(artifact.id, prog)
                                    _detail.value = _detail.value.copy(downloads = d)
                                }
                            }
                        }
                    }
                    conn.disconnect()

                    withContext(Dispatchers.Main) {
                        val d = _detail.value.downloads.toMutableMap()
                        d[artifact.id] = DownloadState(artifact.id, 1f, done = true, filePath = outputUri.toString())
                        _detail.value = _detail.value.copy(downloads = d)
                    }
                    Log.i(TAG, "download: done -> $outputUri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "download", e)
                val d = _detail.value.downloads.toMutableMap()
                d[artifact.id] = DownloadState(artifact.id, 0f, error = e.message)
                _detail.value = _detail.value.copy(downloads = d)
            }
        }
    }

    fun playAudio(url: String, title: String) {
        val cookies = authManager.loadTokens()?.cookies ?: ""
        _detail.value = _detail.value.copy(audioPlayer = AudioPlayerState(url, title, cookies))
    }

    fun stopAudio() {
        _detail.value = _detail.value.copy(audioPlayer = null)
    }

    // ── Download path & detekce ──

    /** Najde uz stazene artefakty v download slozce */
    private fun detectDownloadedArtifacts(artifacts: List<NotebookLmApi.Artifact>): Map<String, DownloadState> {
        val result = mutableMapOf<String, DownloadState>()
        val ctx = getApplication<Application>()
        val savedUri = getDownloadPath()

        for (art in artifacts) {
            if (art.url == null) continue
            val ext = when (art.type) {
                NotebookLmApi.ArtifactType.AUDIO -> ".mp3"
                NotebookLmApi.ArtifactType.VIDEO -> ".mp4"
                NotebookLmApi.ArtifactType.SLIDE_DECK -> ".pdf"
                NotebookLmApi.ArtifactType.INFOGRAPHIC -> ".png"
                else -> ""
            }
            val fileName = "${art.title}$ext".replace("/", "_")

            if (savedUri.isNotEmpty()) {
                // SAF slozka
                try {
                    val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(savedUri))
                    val file = dir?.findFile(fileName)
                    if (file != null && file.exists() && file.length() > 0) {
                        result[art.id] = DownloadState(art.id, 1f, done = true, filePath = file.uri.toString())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "detectDownloads SAF: ${e.message}")
                }
            } else {
                // Fallback Downloads/notebooklm
                val file = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ),
                    "notebooklm/$fileName"
                )
                if (file.exists() && file.length() > 0) {
                    result[art.id] = DownloadState(art.id, 1f, done = true, filePath = Uri.fromFile(file).toString())
                }
            }
        }
        return result
    }

    fun getDownloadPath(): String = prefs.getString("download_path", "") ?: ""

    fun setDownloadPath(path: String) {
        prefs.edit().putString("download_path", path).apply()
    }

    fun getCookies(): String = authManager.loadTokens()?.cookies ?: ""

    fun goBack() {
        _screen.value = Screen.NotebookList
    }

    fun logout() {
        authManager.clear()
        _notebooks.value = emptyList()
        _screen.value = Screen.Login
    }

    fun dismissError() {
        _error.value = null
    }

    fun needsLogin(): Boolean = _screen.value is Screen.Login

    // ── OpenRouter API key ──

    fun getApiKey(): String = prefs.getString("openrouter_api_key", "") ?: ""

    fun setApiKey(key: String) {
        prefs.edit().putString("openrouter_api_key", key).apply()
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    fun getClassifyModel(): String =
        prefs.getString("classify_model", "google/gemini-3.1-flash-lite-preview") ?: "google/gemini-3.1-flash-lite-preview"

    fun setClassifyModel(model: String) {
        prefs.edit().putString("classify_model", model).apply()
    }

    // ── Deduplikace zdrojů ──

    /** Spusti deduplikaci vsech notebooku — scanuje zdroje a maze duplikaty */
    fun startDeduplication() {
        val tokens = authManager.loadTokens() ?: return
        val nbs = _notebooks.value.toList()
        if (nbs.isEmpty()) return

        _dedup.value = DeduplicationState(running = true)

        viewModelScope.launch {
            val api = NotebookLmApi(httpClient, tokens)
            var totalDeleted = 0

            for ((idx, nb) in nbs.withIndex()) {
                _dedup.value = _dedup.value.copy(
                    currentNotebook = nb.title,
                    progress = "${idx + 1}/${nbs.size}",
                    groups = emptyList(),
                )

                try {
                    val sources = api.getSources(nb.id)
                    if (sources.size < 2) continue

                    // Seskup podle nazvu
                    val byTitle = sources.groupBy { it.title }
                    val groups = mutableListOf<DuplicateGroup>()

                    for ((title, group) in byTitle) {
                        if (group.size <= 1) continue

                        val allText = group.all { it.type == NotebookLmApi.SourceType.TEXT }
                        if (allText) {
                            // Content-aware deduplikace — hash fulltext obsahu
                            val hashGroups = mutableMapOf<String, MutableList<String>>()
                            for (src in group) {
                                val hash = try {
                                    val content = api.getSourceFulltext(nb.id, src.id)
                                    content.hashCode().toString(16)
                                } catch (_: Exception) {
                                    "unique_${src.id}"
                                }
                                hashGroups.getOrPut(hash) { mutableListOf() }.add(src.id)
                            }
                            for ((_, ids) in hashGroups) {
                                if (ids.size > 1) {
                                    groups.add(DuplicateGroup(title, ids.size, ids.drop(1)))
                                }
                            }
                        } else {
                            // Non-text — deduplikace jen podle nazvu
                            val ids = group.map { it.id }
                            groups.add(DuplicateGroup(title, ids.size, ids.drop(1)))
                        }
                    }

                    if (groups.isEmpty()) continue

                    groups.sortByDescending { it.count }
                    _dedup.value = _dedup.value.copy(groups = groups)

                    // Smaz duplikaty
                    for (g in groups) {
                        for (srcId in g.deleteIds) {
                            try {
                                api.deleteSource(nb.id, srcId)
                                totalDeleted++
                                _dedup.value = _dedup.value.copy(totalDeleted = totalDeleted)
                            } catch (e: Exception) {
                                Log.w(TAG, "dedup delete: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "dedup scan ${nb.title}: ${e.message}")
                }
            }

            _dedup.value = DeduplicationState(
                done = true,
                totalDeleted = totalDeleted,
                progress = "hotovo",
            )
        }
    }

    fun dismissDedup() {
        _dedup.value = DeduplicationState()
    }

    // ── AI Klasifikace ──

    /** Davkova klasifikace notebooku pres OpenRouter LLM (ids=null -> vsechny) */
    fun startClassification(ids: Set<String>? = null) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            _error.value = "Nastav OpenRouter API klic v nastaveni"
            return
        }
        val allNbs = _notebooks.value.toList()
        val nbs = if (ids != null) allNbs.filter { it.id in ids } else allNbs
        if (nbs.isEmpty()) return

        val model = getClassifyModel()
        _classify.value = ClassificationState(running = true)

        viewModelScope.launch {
            try {
                // Aktualizovany set kategorii — roste s kazdym batchem
                val knownCats = _categories.value.values.distinct().toMutableSet()
                val allResults = mutableMapOf<String, String>()

                // Zpracuj po batchich po 10
                val batches = nbs.chunked(10)
                for ((batchIdx, batch) in batches.withIndex()) {
                    _classify.value = _classify.value.copy(
                        progress = "batch ${batchIdx + 1}/${batches.size}",
                    )

                    val nbLines = batch.mapIndexed { i, nb ->
                        val prefix = if (nb.emoji.isNotEmpty()) "${nb.emoji} " else ""
                        "${i + 1}. [${nb.id}] $prefix${nb.title}"
                    }.joinToString("\n")

                    val sortedCats = knownCats.sorted()
                    val catsContext = if (sortedCats.isEmpty())
                        "Zatim zadne kategorie neexistuji — vytvor nove."
                    else
                        "Existujici kategorie (MUSIS pouzit PRESNE tyto nazvy, zadne varianty): ${sortedCats.joinToString(", ")}"

                    val prompt = """
Jsi organizacni asistent. Prirad kazdemu notebooku jednu kategorii.

PRAVIDLA:
1. Kategorie je MAXIMALNE 2 slova, cesky, strucne, lowercase (prvni pismeno velke)
2. Kategorie MUSI byt OBECNE — zastresujici temata, ne konkretni nastroje nebo produkty
   SPATNE: "Claude Code", "Gemini agent", "React hooks", "Python scripty"
   SPRAVNE: "Programovani", "Ai nastroje", "Webovy vyvoj", "Automatizace"
3. KONZISTENCE: Pokud dva notebooky patri do stejne oblasti, MUSI mit STEJNOU kategorii
   Napr. notebook o Claude a notebook o Gemini → oba "Ai nastroje", NE dve ruzne kategorie
4. MUSIS pouzit existujici kategorie pokud jen trochu sedi — novou vytvor JEN kdyz ZADNA existujici nesedi
5. Cil je 5-15 kategorii celkem pro VSECHNY notebooky, ne unikatni kategorie pro kazdy
6. Mysli v urovni "police v knihovne" — obecne tema, ne konkretni kniha

$catsContext

NOTEBOOKY:
$nbLines

Odpovez POUZE platnym JSON polem — zadny jiny text:
[{"id": "notebook_id", "category": "kategorie"}]
""".trim()

                    val response = httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
                        header("Authorization", "Bearer $apiKey")
                        header("Content-Type", "application/json")
                        setBody(kotlinx.serialization.json.buildJsonObject {
                            put("model", JsonPrimitive(model))
                            putJsonArray("messages") {
                                addJsonObject {
                                    put("role", JsonPrimitive("user"))
                                    put("content", JsonPrimitive(prompt))
                                }
                            }
                            put("max_tokens", JsonPrimitive(1000))
                            put("temperature", JsonPrimitive(0.3))
                        }.toString())
                    }

                    val body = response.bodyAsText()
                    val json = kotlinx.serialization.json.Json.parseToJsonElement(body)
                    val content = json.jsonObject["choices"]?.jsonArray
                        ?.getOrNull(0)?.jsonObject
                        ?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.contentOrNull ?: continue

                    // Parsuj JSON — muze byt obalene v ```json ... ```
                    val clean = content.trim()
                        .removePrefix("```json").removePrefix("```")
                        .removeSuffix("```").trim()

                    try {
                        val arr = kotlinx.serialization.json.Json.parseToJsonElement(clean).jsonArray
                        for (item in arr) {
                            val id = item.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: continue
                            val cat = item.jsonObject["category"]?.jsonPrimitive?.contentOrNull ?: continue
                            // Normalizuj — lowercase + first uppercase
                            val normalized = cat.trim().lowercase().replaceFirstChar { it.uppercase() }
                            allResults[id] = normalized
                            catPrefs.edit().putString(id, normalized).apply()
                            // Pridej do znamych kategorii pro dalsi batche
                            knownCats.add(normalized)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "classify parse: ${e.message}, raw=$clean")
                    }

                    _classify.value = _classify.value.copy(results = allResults.toMap())
                }

                _categories.value = catPrefs.all.mapNotNull { (k, v) ->
                    if (v is String) k to v else null
                }.toMap()

                _classify.value = ClassificationState(
                    done = true,
                    results = allResults,
                    progress = "hotovo — ${allResults.size} klasifikovano",
                )
            } catch (e: Exception) {
                Log.e(TAG, "classify", e)
                _classify.value = ClassificationState(error = e.message)
            }
        }
    }

    fun dismissClassify() {
        _classify.value = ClassificationState()
    }

    // ── Semantic search ──

    /** Embeddne vybrane notebooky (nebo vsechny, kdyz ids je null) */
    fun embedNotebooks(ids: Set<String>? = null) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            _error.value = "Nastav OpenRouter API klic v nastaveni"
            return
        }
        val allNbs = _notebooks.value
        val nbs = if (ids != null) allNbs.filter { it.id in ids } else allNbs
        if (nbs.isEmpty()) return

        val tokens = authManager.loadTokens() ?: return
        _embeddingStatus.value = "Stahuji popisy ${nbs.size} sesitu..."
        viewModelScope.launch {
            try {
                val api = NotebookLmApi(httpClient, tokens)
                val embedding = OpenRouterEmbedding(httpClient, apiKey)

                // Stahni summary pro kazdy notebook (jako Rust impl)
                val descriptions = mutableMapOf<String, String>()
                for ((idx, nb) in nbs.withIndex()) {
                    _embeddingStatus.value = "Popis ${idx + 1}/${nbs.size}: ${nb.title.take(20)}..."
                    try {
                        val summary = api.getSummary(nb.id)
                        if (summary != null) descriptions[nb.id] = summary
                    } catch (e: Exception) {
                        Log.w(TAG, "getSummary failed for ${nb.id}: ${e.message}")
                    }
                }

                withContext(Dispatchers.IO) {
                    // Text pro embedding = title + summary (jako Rust)
                    val toEmbed = nbs.filter { nb ->
                        val desc = descriptions[nb.id] ?: ""
                        val text = if (desc.isEmpty()) nb.title else "${nb.title} $desc"
                        embeddingDb.needsUpdate(nb.id, text)
                    }

                    if (toEmbed.isEmpty()) {
                        _embeddingStatus.value = null
                        return@withContext
                    }

                    _embeddingStatus.value = "Embedduji ${toEmbed.size} sesitu..."

                    // Batch po 20
                    for (chunk in toEmbed.chunked(20)) {
                        val texts = chunk.map { nb ->
                            val desc = descriptions[nb.id] ?: ""
                            if (desc.isEmpty()) nb.title else "${nb.title} $desc"
                        }
                        val embeddings = embedding.embed(texts)
                        for ((i, nb) in chunk.withIndex()) {
                            val desc = descriptions[nb.id] ?: ""
                            embeddingDb.upsertEmbedding(nb.id, nb.title, desc, embeddings[i])
                        }
                    }

                    // Prune smazane
                    embeddingDb.pruneDeleted(nbs.map { it.id }.toSet())
                }
                _embeddingStatus.value = null
                Log.i(TAG, "embedNotebooks: done, ${descriptions.size} popisů")
            } catch (e: Exception) {
                Log.e(TAG, "embedNotebooks", e)
                _embeddingStatus.value = null
                _error.value = "Embedding chyba: ${e.message}"
            }
        }
    }

    /** Semanticke vyhledavani — embeddne query a KNN */
    fun semanticSearch(query: String) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            _error.value = "Nastav OpenRouter API klic"
            return
        }
        if (query.isBlank()) {
            _semanticResults.value = null
            return
        }

        _searchLoading.value = true
        viewModelScope.launch {
            try {
                val embedding = OpenRouterEmbedding(httpClient, apiKey)
                val queryEmb = embedding.embedSingle(query)
                Log.i(TAG, "semanticSearch: queryEmb dim=${queryEmb.size}, first3=${queryEmb.take(3)}")
                val results = withContext(Dispatchers.IO) {
                    val count = embeddingDb.count()
                    Log.i(TAG, "semanticSearch: DB ma $count embeddingu")
                    embeddingDb.search(queryEmb, limit = 20)
                }
                Log.i(TAG, "semanticSearch: vraci ${results.size} IDs")
                _semanticResults.value = results.map { it.first }
                Log.i(TAG, "semanticSearch: ${results.size} vysledku")
            } catch (e: Exception) {
                Log.e(TAG, "semanticSearch", e)
                _error.value = "Search chyba: ${e.message}"
            } finally {
                _searchLoading.value = false
            }
        }
    }

    fun clearSemanticResults() {
        _semanticResults.value = null
    }

    override fun onCleared() {
        httpClient.close()
        embeddingDb.close()
        super.onCleared()
    }
}
