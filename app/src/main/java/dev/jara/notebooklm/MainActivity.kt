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
import dev.jara.notebooklm.ui.MainViewModel
import dev.jara.notebooklm.ui.TerminalScreen

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

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

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            val lines by viewModel.lines.collectAsState()

            TerminalScreen(
                lines = lines,
                onCommand = { cmd ->
                    if (cmd.trim().lowercase() == "login") {
                        viewModel.handleCommand(cmd)
                        loginLauncher.launch(Intent(this, LoginActivity::class.java))
                    } else {
                        viewModel.handleCommand(cmd)
                    }
                },
            )
        }
    }
}
