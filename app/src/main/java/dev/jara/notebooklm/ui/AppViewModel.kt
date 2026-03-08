package dev.jara.notebooklm.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jara.notebooklm.auth.AuthManager
import dev.jara.notebooklm.rpc.*
import dev.jara.notebooklm.search.EmbeddingDb
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "AppViewModel"
    }

    internal val authManager = AuthManager(app)
    internal val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 120_000  // 2 min — chat muze trvat dlouho
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }
    internal val embeddingDb = EmbeddingDb(app)
    internal val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

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

    internal val _semanticResults = MutableStateFlow<List<String>?>(null)
    val semanticResults: StateFlow<List<String>?> get() = _semanticResults

    internal val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> get() = _searchLoading

    internal val _embeddingStatus = MutableStateFlow<String?>(null)
    val embeddingStatus: StateFlow<String?> get() = _embeddingStatus

    internal val _dedup = MutableStateFlow(DeduplicationState())
    val dedup: StateFlow<DeduplicationState> get() = _dedup

    internal val _detailDedup = MutableStateFlow(DeduplicationState())
    val detailDedup: StateFlow<DeduplicationState> get() = _detailDedup

    internal val _classify = MutableStateFlow(ClassificationState())
    val classify: StateFlow<ClassificationState> get() = _classify

    // Kategorie notebooku (persisted v SharedPreferences)
    internal val catPrefs = app.getSharedPreferences("categories", Context.MODE_PRIVATE)
    internal val _categories = MutableStateFlow<Map<String, String>>(emptyMap())

    internal val _facets = MutableStateFlow<Map<String, NotebookFacets>>(emptyMap())
    val facets: StateFlow<Map<String, NotebookFacets>> get() = _facets

    internal val _screen = MutableStateFlow<Screen>(
        if (authManager.isLoggedIn()) Screen.NotebookList else Screen.Login
    )
    val screen: StateFlow<Screen> get() = _screen

    internal val _notebooks = MutableStateFlow<List<Notebook>>(emptyList())
    val notebooks: StateFlow<List<Notebook>> get() = _notebooks

    private val _notebooksLoading = MutableStateFlow(false)
    val notebooksLoading: StateFlow<Boolean> get() = _notebooksLoading

    internal val _detail = MutableStateFlow(DetailState())
    val detail: StateFlow<DetailState> get() = _detail

    internal val _error = MutableStateFlow<String?>(null)
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
        // Migrace kategorií do notebook_facets (jednorázově)
        if (!prefs.getBoolean("facets_migrated", false)) {
            for ((id, cat) in rawCats) {
                embeddingDb.upsertFacets(id, NotebookFacets(topic = cat))
            }
            prefs.edit().putBoolean("facets_migrated", true).apply()
        }
        // Načti facety z DB
        _facets.value = embeddingDb.getAllFacets()
        // Categories z facet topic (zpětná kompatibilita)
        _categories.value = _facets.value.mapValues { it.value.topic }.filterValues { it.isNotEmpty() }
        if (authManager.isLoggedIn()) {
            loadNotebooks()
        }
    }

    // ── Auth ──

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

    fun logout() {
        authManager.clear()
        _notebooks.value = emptyList()
        _screen.value = Screen.Login
    }

    fun getCookies(): String = authManager.loadTokens()?.cookies ?: ""

    fun needsLogin(): Boolean = _screen.value is Screen.Login

    // ── Notebook CRUD ──

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

    fun openNotebook(nb: Notebook) {
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

    // ── Navigation ──

    fun switchTab(tab: DetailTab) {
        _detail.value = _detail.value.copy(tab = tab)
    }

    fun goBack() {
        _screen.value = Screen.NotebookList
    }

    // ── Chat ──

    fun sendChat(question: String) {
        val tokens = authManager.loadTokens() ?: return
        val current = _detail.value
        if (question.isBlank() || current.chatAnswering) return

        val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
        val updatedMessages = current.chatMessages + ChatMessage(
            ChatRole.USER, question
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
                val withAnswer = updatedMessages + ChatMessage(
                    ChatRole.ASSISTANT, answer
                )
                _detail.value = _detail.value.copy(chatMessages = withAnswer, chatAnswering = false)
            } catch (e: Exception) {
                Log.e(TAG, "sendChat", e)
                _detail.value = _detail.value.copy(chatAnswering = false)
                _error.value = "Chat chyba: ${e.message}"
            }
        }
    }

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

    // ── Audio ──

    fun playAudio(url: String, title: String) {
        val cookies = authManager.loadTokens()?.cookies ?: ""
        _detail.value = _detail.value.copy(audioPlayer = AudioPlayerState(url, title, cookies))
    }

    fun stopAudio() {
        _detail.value = _detail.value.copy(audioPlayer = null)
    }

    // ── Artifacts (generovani, mazani, interactive HTML) ──

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
        type: GenerateType,
        options: GenerateOptions = GenerateOptions(),
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
    fun sortedNotebooks(notebooks: List<Notebook>): List<Notebook> {
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

    // ── Dismiss helpers ──
    // Extension logika: AppViewModelGlobalOps.kt, AppViewModelSources.kt, AppViewModelDownload.kt

    fun dismissDedup() { _dedup.value = DeduplicationState() }
    fun dismissDetailDedup() { _detailDedup.value = DeduplicationState() }
    fun dismissClassify() { _classify.value = ClassificationState() }
    fun clearSemanticResults() { _semanticResults.value = null }
    fun dismissError() { _error.value = null }

    override fun onCleared() {
        httpClient.close()
        embeddingDb.close()
        super.onCleared()
    }
}
