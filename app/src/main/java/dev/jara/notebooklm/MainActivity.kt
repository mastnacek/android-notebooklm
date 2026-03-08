package dev.jara.notebooklm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
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
            val themeMode by viewModel.themeMode.collectAsState()
            NotebookLmTheme(themeMode = themeMode) {
            val screen by viewModel.screen.collectAsState()
            val notebooks by viewModel.notebooks.collectAsState()
            val loading by viewModel.notebooksLoading.collectAsState()
            val detail by viewModel.detail.collectAsState()
            val error by viewModel.error.collectAsState()
            val semanticResults by viewModel.semanticResults.collectAsState()
            val searchLoading by viewModel.searchLoading.collectAsState()
            val embeddingStatus by viewModel.embeddingStatus.collectAsState()
            val favorites by viewModel.favorites.collectAsState()
            val sortMode by viewModel.notebookSort.collectAsState()
            var showSettings by remember { mutableStateOf(false) }
            val dedupState by viewModel.dedup.collectAsState()
            val detailDedupState by viewModel.detailDedup.collectAsState()
            val classifyState by viewModel.classify.collectAsState()
            val categories by viewModel.categories.collectAsState()
            val downloadPath by remember { derivedStateOf { viewModel.getDownloadPath() } }

            val folderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri: Uri? ->
                if (uri != null) {
                    // Persist permission pro pristup ke slozce i po restartu
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    viewModel.setDownloadPath(uri.toString())
                }
            }

            if (showSettings) {
                SettingsDialog(
                    currentApiKey = viewModel.getApiKey(),
                    currentDownloadPath = viewModel.getDownloadPath(),
                    currentClassifyModel = viewModel.getClassifyModel(),
                    currentThemeMode = themeMode,
                    onSave = { apiKey, classifyModel ->
                        viewModel.setApiKey(apiKey)
                        viewModel.setClassifyModel(classifyModel)
                    },
                    onPickFolder = { folderPicker.launch(null) },
                    onThemeChange = { viewModel.setThemeMode(it) },
                    onDismiss = { showSettings = false },
                )
            }

            // Back gesto: z detailu zpět na seznam (místo zavření appky)
            BackHandler(enabled = screen is Screen.NotebookDetail) {
                viewModel.goBack()
            }

            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState is Screen.NotebookDetail) {
                        // List -> Detail: slide in from right
                        slideInHorizontally { it / 3 } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally { -it / 3 } + fadeOut(tween(200))
                    } else if (initialState is Screen.NotebookDetail) {
                        // Detail -> List: slide in from left
                        slideInHorizontally { -it / 3 } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally { it / 3 } + fadeOut(tween(200))
                    } else {
                        // Default: crossfade
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    }
                },
                label = "screen_transition",
            ) { targetScreen ->
                when (val s = targetScreen) {
                    is Screen.Login -> LoginScreen(
                        onLogin = {
                            loginLauncher.launch(Intent(this@MainActivity, LoginActivity::class.java))
                        },
                        error = error,
                    )

                    is Screen.NotebookList -> NotebookListScreen(
                        notebooks = viewModel.sortedNotebooks(notebooks),
                        loading = loading,
                        semanticResults = semanticResults,
                        searchLoading = searchLoading,
                        embeddingStatus = embeddingStatus,
                        hasApiKey = viewModel.hasApiKey(),
                        favorites = favorites,
                        sortMode = sortMode,
                        onNotebookClick = { viewModel.openNotebook(it) },
                        onRefresh = { viewModel.loadNotebooks() },
                        onLogout = { viewModel.logout() },
                        onSemanticSearch = { viewModel.semanticSearch(it) },
                        onClearSemantic = { viewModel.clearSemanticResults() },
                        onEmbedNotebooks = { ids -> viewModel.embedNotebooks(ids) },
                        onSettings = { showSettings = true },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onCycleSort = { viewModel.cycleSort() },
                        dedup = dedupState,
                        onStartDedup = { viewModel.startDeduplication() },
                        onDismissDedup = { viewModel.dismissDedup() },
                        classify = classifyState,
                        categories = categories,
                        onStartClassify = { viewModel.startClassification() },
                        onClassifySelected = { ids -> viewModel.startClassification(ids) },
                        onDismissClassify = { viewModel.dismissClassify() },
                        onCreateNotebook = { title, emoji -> viewModel.createNotebook(title, emoji) },
                        onDeleteNotebook = { viewModel.deleteNotebook(it) },
                    )

                    is Screen.NotebookDetail -> NotebookDetailScreen(
                        notebook = s.notebook,
                        detail = detail,
                        onBack = { viewModel.goBack() },
                        onTabSwitch = { viewModel.switchTab(it) },
                        onSendChat = { viewModel.sendChat(it) },
                        onSaveAsNote = { viewModel.saveAsNote(it) },
                        onPlayAudio = { url, title -> viewModel.playAudio(url, title) },
                        onStopAudio = { viewModel.stopAudio() },
                        onDownloadAudio = { art -> viewModel.downloadArtifact(art) },
                        onAddSource = { type, value, title -> viewModel.addSource(type, value, title) },
                        onDeleteSource = { viewModel.deleteSource(it) },
                        onDeleteSources = { viewModel.deleteSources(it) },
                        onDedupSources = { viewModel.dedupCurrentNotebook() },
                        onDismissDedup = { viewModel.dismissDetailDedup() },
                        dedup = detailDedupState,
                        onGenerateArtifact = { type, opts -> viewModel.generateArtifact(type, opts) },
                        onOpenInteractiveHtml = { viewModel.openInteractiveHtml(it) },
                        onDeleteArtifact = { viewModel.deleteArtifact(it) },
                        onDeleteNote = { viewModel.deleteNote(it) },
                    )
                }
            }
            } // NotebookLmTheme
        }
    }
}
