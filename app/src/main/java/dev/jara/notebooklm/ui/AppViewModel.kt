package dev.jara.notebooklm.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jara.notebooklm.auth.AuthManager
import dev.jara.notebooklm.rpc.NotebookLmApi
import dev.jara.notebooklm.search.EmbeddingDb
import dev.jara.notebooklm.search.OpenRouterEmbedding
import io.ktor.client.*
import io.ktor.client.engine.cio.*
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

/** Stav detailu sesitu */
data class DetailState(
    val sources: List<NotebookLmApi.Source> = emptyList(),
    val summary: String? = null,
    val loading: Boolean = true,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "AppViewModel"
    }

    private val authManager = AuthManager(app)
    private val httpClient = HttpClient(CIO)
    private val embeddingDb = EmbeddingDb(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _semanticResults = MutableStateFlow<List<String>?>(null)
    val semanticResults: StateFlow<List<String>?> get() = _semanticResults

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> get() = _searchLoading

    private val _embeddingStatus = MutableStateFlow<String?>(null)
    val embeddingStatus: StateFlow<String?> get() = _embeddingStatus

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

    init {
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
                _detail.value = DetailState(sources, summary, loading = false)
            } catch (e: Exception) {
                Log.e(TAG, "openNotebook", e)
                _detail.value = DetailState(loading = false)
                _error.value = "Chyba: ${e.message}"
            }
        }
    }

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

    // ── Semantic search ──

    /** Embeddne vsechny notebooky (background, incrementalne) */
    fun embedNotebooks() {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            _error.value = "Nastav OpenRouter API klic v nastaveni"
            return
        }
        val nbs = _notebooks.value
        if (nbs.isEmpty()) return

        _embeddingStatus.value = "Embedduji ${nbs.size} sesitu..."
        viewModelScope.launch {
            try {
                val embedding = OpenRouterEmbedding(httpClient, apiKey)
                withContext(Dispatchers.IO) {
                    // Zjisti ktere potrebuji update
                    val toEmbed = nbs.filter { nb ->
                        val text = nb.title
                        embeddingDb.needsUpdate(nb.id, text)
                    }

                    if (toEmbed.isEmpty()) {
                        _embeddingStatus.value = null
                        return@withContext
                    }

                    _embeddingStatus.value = "Embedduji ${toEmbed.size} novych..."

                    // Batch po 20
                    for (chunk in toEmbed.chunked(20)) {
                        val texts = chunk.map { it.title }
                        val embeddings = embedding.embed(texts)
                        for ((i, nb) in chunk.withIndex()) {
                            embeddingDb.upsertEmbedding(nb.id, nb.title, "", embeddings[i])
                        }
                    }

                    // Prune smazane
                    embeddingDb.pruneDeleted(nbs.map { it.id }.toSet())
                }
                _embeddingStatus.value = null
                Log.i(TAG, "embedNotebooks: done")
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
                val results = withContext(Dispatchers.IO) {
                    embeddingDb.search(queryEmb, limit = 20)
                }
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
