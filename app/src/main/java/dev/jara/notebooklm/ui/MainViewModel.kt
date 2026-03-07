package dev.jara.notebooklm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import dev.jara.notebooklm.auth.AuthManager
import dev.jara.notebooklm.rpc.NotebookLmApi
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val authManager = AuthManager(app)
    private val httpClient = HttpClient(CIO)

    private val _lines = MutableStateFlow(mutableListOf<TermLine>())
    val lines: StateFlow<List<TermLine>> get() = _lines

    private val _isLoggedIn = MutableStateFlow(authManager.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> get() = _isLoggedIn

    init {
        appendLine(TermLine.Text("NotebookLM Terminal v0.1.0-alpha"))
        appendLine(TermLine.Text("batchexecute RPC client for Android"))
        appendLine(TermLine.Blank)

        if (authManager.isLoggedIn()) {
            appendLine(TermLine.Ok("Ulozena session nalezena."))
            appendLine(TermLine.Info("Prikaz: [refresh] [list] [logout]"))
        } else {
            appendLine(TermLine.Warn("Nejsi prihlasen."))
            appendLine(TermLine.Info("Prikaz: [login]"))
        }
    }

    fun handleCommand(cmd: String) {
        val trimmed = cmd.trim().lowercase()
        appendLine(TermLine.Input("> $cmd"))

        when (trimmed) {
            "login" -> appendLine(TermLine.Info("Otviram WebView pro prihlaseni..."))
            "logout" -> doLogout()
            "refresh" -> doRefreshTokens()
            "list" -> doListNotebooks()
            "clear" -> doClear()
            "help" -> doHelp()
            else -> appendLine(TermLine.Error("Neznamy prikaz: $trimmed"))
        }
    }

    fun onLoginSuccess(cookies: String) {
        authManager.saveCookies(cookies)
        appendLine(TermLine.Ok("Cookies ziskany z WebView."))
        appendLine(TermLine.Info("Stahuji tokeny..."))
        viewModelScope.launch {
            try {
                val tokens = authManager.fetchTokens(httpClient)
                if (tokens != null) {
                    appendLine(TermLine.Ok("CSRF token: ${tokens.csrfToken.take(20)}..."))
                    appendLine(TermLine.Ok("Session ID: ${tokens.sessionId.take(20)}..."))
                    appendLine(TermLine.Ok("Autentizace uspesna!"))
                    appendLine(TermLine.Info("Prikaz: [list] [refresh] [logout]"))
                    _isLoggedIn.value = true
                } else {
                    appendLine(TermLine.Error("Nelze extrahovat tokeny z HTML."))
                    appendLine(TermLine.Warn("Zkus 'login' znovu."))
                }
            } catch (e: Exception) {
                appendLine(TermLine.Error("Chyba: ${e.message}"))
            }
        }
    }

    fun onLoginFailed(reason: String) {
        appendLine(TermLine.Error(reason))
    }

    private fun doLogout() {
        authManager.clear()
        _isLoggedIn.value = false
        appendLine(TermLine.Ok("Odhlaseno. Vsechna data smazana."))
    }

    private fun doRefreshTokens() {
        if (!authManager.isLoggedIn()) {
            appendLine(TermLine.Error("Nejsi prihlasen. Pouzij 'login'."))
            return
        }
        appendLine(TermLine.Info("Obnovuji tokeny..."))
        viewModelScope.launch {
            try {
                val tokens = authManager.fetchTokens(httpClient)
                if (tokens != null) {
                    appendLine(TermLine.Ok("Tokeny obnoveny."))
                } else {
                    appendLine(TermLine.Error("Session vyprsela. Pouzij 'login'."))
                    _isLoggedIn.value = false
                }
            } catch (e: Exception) {
                appendLine(TermLine.Error("Chyba: ${e.message}"))
            }
        }
    }

    private fun doListNotebooks() {
        val tokens = authManager.loadTokens()
        Log.i(TAG, "doListNotebooks: tokens=${tokens != null}")
        if (tokens == null) {
            appendLine(TermLine.Error("Nejsi prihlasen. Pouzij 'login'."))
            return
        }
        Log.i(TAG, "doListNotebooks: csrf=${tokens.csrfToken.take(20)}, sid=${tokens.sessionId.take(20)}, cookies=${tokens.cookies.take(40)}")

        appendLine(TermLine.Info("Nacitam sesity..."))
        viewModelScope.launch {
            try {
                Log.i(TAG, "doListNotebooks: creating API and calling listNotebooks")
                val api = NotebookLmApi(httpClient, tokens)
                val notebooks = api.listNotebooks()
                Log.i(TAG, "doListNotebooks: got ${notebooks.size} notebooks")

                if (notebooks.isEmpty()) {
                    appendLine(TermLine.Warn("Zadne sesity nenalezeny."))
                } else {
                    appendLine(TermLine.Ok("${notebooks.size} sesitu:"))
                    appendLine(TermLine.Blank)
                    for ((i, nb) in notebooks.withIndex()) {
                        val prefix = if (nb.emoji.isNotEmpty()) "${nb.emoji} " else ""
                        val num = "${i + 1}".padStart(2)
                        appendLine(TermLine.Text("  $num. $prefix${nb.title}"))
                    }
                    appendLine(TermLine.Blank)
                    appendLine(TermLine.Info("Prikaz: [open <cislo>] pro detail"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "doListNotebooks: exception", e)
                appendLine(TermLine.Error("RPC chyba: ${e.message}"))
            }
        }
    }

    private fun doClear() {
        _lines.value = mutableListOf()
    }

    private fun doHelp() {
        appendLine(TermLine.Text("  login    - Prihlaseni pres Google"))
        appendLine(TermLine.Text("  list     - Seznam sesitu"))
        appendLine(TermLine.Text("  refresh  - Obnovit tokeny"))
        appendLine(TermLine.Text("  logout   - Odhlaseni"))
        appendLine(TermLine.Text("  clear    - Vycistit terminal"))
        appendLine(TermLine.Text("  help     - Tato napoveda"))
    }

    private fun appendLine(line: TermLine) {
        _lines.value = (_lines.value + line).toMutableList()
    }

    override fun onCleared() {
        httpClient.close()
        super.onCleared()
    }
}
