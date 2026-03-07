package dev.jara.notebooklm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import dev.jara.notebooklm.auth.LoginActivity
import dev.jara.notebooklm.ui.*

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val cookies = result.data?.getStringExtra(LoginActivity.RESULT_COOKIES)
            if (cookies != null) {
                viewModel.onLoginSuccess(cookies)
            } else {
                viewModel.onLoginFailed("Cookies nebyly ziskany.")
            }
        } else {
            viewModel.onLoginFailed("Login zrusen.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[AppViewModel::class.java]

        setContent {
            val screen by viewModel.screen.collectAsState()
            val notebooks by viewModel.notebooks.collectAsState()
            val loading by viewModel.notebooksLoading.collectAsState()
            val detail by viewModel.detail.collectAsState()
            val error by viewModel.error.collectAsState()

            when (val s = screen) {
                is Screen.Login -> LoginScreen(
                    onLogin = {
                        loginLauncher.launch(Intent(this, LoginActivity::class.java))
                    },
                    error = error,
                )

                is Screen.NotebookList -> NotebookListScreen(
                    notebooks = notebooks,
                    loading = loading,
                    onNotebookClick = { viewModel.openNotebook(it) },
                    onRefresh = { viewModel.loadNotebooks() },
                    onLogout = { viewModel.logout() },
                )

                is Screen.NotebookDetail -> NotebookDetailScreen(
                    notebook = s.notebook,
                    detail = detail,
                    onBack = { viewModel.goBack() },
                )
            }
        }
    }
}
