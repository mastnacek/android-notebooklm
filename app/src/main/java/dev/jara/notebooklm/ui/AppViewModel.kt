package dev.jara.notebooklm.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jara.notebooklm.auth.AuthManager
import dev.jara.notebooklm.rpc.NotebookLmApi
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    override fun onCleared() {
        httpClient.close()
        super.onCleared()
    }
}
