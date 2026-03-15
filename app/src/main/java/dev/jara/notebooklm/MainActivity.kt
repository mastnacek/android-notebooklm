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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jara.notebooklm.auth.LoginActivity
import dev.jara.notebooklm.rpc.AccountInfo
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
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            NotebookLmTheme(themeMode = themeMode) {
            val screen by viewModel.screen.collectAsStateWithLifecycle()
            val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
            val loading by viewModel.notebooksLoading.collectAsStateWithLifecycle()
            val detail by viewModel.detail.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()
            val semanticResults by viewModel.semanticResults.collectAsStateWithLifecycle()
            val searchLoading by viewModel.searchLoading.collectAsStateWithLifecycle()
            val embeddingStatus by viewModel.embeddingStatus.collectAsStateWithLifecycle()
            val favorites by viewModel.favorites.collectAsStateWithLifecycle()
            val sortMode by viewModel.notebookSort.collectAsStateWithLifecycle()
            var showSettings by remember { mutableStateOf(false) }
            val dedupState by viewModel.dedup.collectAsStateWithLifecycle()
            val detailDedupState by viewModel.detailDedup.collectAsStateWithLifecycle()
            val classifyState by viewModel.classify.collectAsStateWithLifecycle()
            val categories by viewModel.categories.collectAsStateWithLifecycle()
            val facetsState by viewModel.facets.collectAsStateWithLifecycle()
            val facetFilter by viewModel.facetFilter.collectAsStateWithLifecycle()
            val sourceScanState by viewModel.sourceScan.collectAsStateWithLifecycle()
            val indicatorsState by viewModel.indicators.collectAsStateWithLifecycle()
            val sourceGroupsState by viewModel.sourceGroups.collectAsStateWithLifecycle()
            val accountInfoState by viewModel.accountInfo.collectAsStateWithLifecycle()
            val downloadPath by remember { derivedStateOf { viewModel.getDownloadPath() } }
            val quickArtifacts by viewModel.quickArtifacts.collectAsStateWithLifecycle()
            val quickArtifactsLoading by viewModel.quickArtifactsLoading.collectAsStateWithLifecycle()
            val quickArtifactsNotebook by viewModel.quickArtifactsNotebook.collectAsStateWithLifecycle()
            val quickPlayerUrl by viewModel.quickPlayerUrl.collectAsStateWithLifecycle()
            val quickPlayerTitle by viewModel.quickPlayerTitle.collectAsStateWithLifecycle()
            val showGemini by viewModel.showGemini.collectAsStateWithLifecycle()
            val geminiChats by viewModel.geminiChats.collectAsStateWithLifecycle()
            val geminiLoading by viewModel.geminiLoading.collectAsStateWithLifecycle()

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

            // Quick artifacts dialog
            if (quickArtifactsNotebook != null) {
                ArtifactQuickDialog(
                    notebookTitle = quickArtifactsNotebook!!.title,
                    artifacts = quickArtifacts ?: emptyList(),
                    loading = quickArtifactsLoading,
                    onPlay = { url, title -> viewModel.playAudioFromList(url, title) },
                    onStopPlayer = { viewModel.stopQuickPlayer() },
                    playerUrl = quickPlayerUrl,
                    playerTitle = quickPlayerTitle,
                    player = viewModel.quickExoPlayer,
                    onDismiss = { viewModel.dismissQuickArtifacts() },
                )
            }

            // Back gesto
            BackHandler(enabled = screen is Screen.Quiz) {
                viewModel.closeQuiz()
            }
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
                    } else if (targetState is Screen.Quiz || initialState is Screen.Quiz) {
                        // Quiz: slide up/down
                        slideInVertically { it } + fadeIn(tween(300)) togetherWith
                            slideOutVertically { -it / 3 } + fadeOut(tween(200))
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
                        onStartDedup = { viewModel.startDeduplication(null) },
                        onDismissDedup = { viewModel.dismissDedup() },
                        classify = classifyState,
                        categories = categories,
                        onStartClassify = { viewModel.startClassification() },
                        onClassifySelected = { ids -> viewModel.startClassification(ids) },
                        onDismissClassify = { viewModel.dismissClassify() },
                        onCreateNotebook = { title, emoji -> viewModel.createNotebook(title, emoji) },
                        onDeleteNotebook = { viewModel.deleteNotebook(it) },
                        onRenameNotebook = { id, title -> viewModel.renameNotebook(id, title) },
                        facets = facetsState,
                        facetFilter = facetFilter,
                        onFacetFilterChange = { viewModel.setFacetFilter(it) },
                        sourceScan = sourceScanState,
                        onScanSources = { ids -> viewModel.scanSources(ids) },
                        onDismissSourceScan = { viewModel.dismissSourceScan() },
                        indicators = indicatorsState,
                        sourceGroups = sourceGroupsState,
                        onDedupSelected = { ids -> viewModel.startDeduplication(ids) },
                        accountInfo = accountInfoState,
                        onArtifactsClick = { viewModel.loadQuickArtifacts(it) },
                        miniPlayerTitle = quickPlayerUrl?.let { quickPlayerTitle },
                        onMiniPlayerClick = { viewModel.reopenQuickArtifacts() },
                        onMiniPlayerStop = { viewModel.stopAndDismissQuickPlayer() },
                        showGemini = showGemini,
                        geminiChats = geminiChats,
                        geminiLoading = geminiLoading,
                        onToggleGemini = { viewModel.toggleGemini() },
                        onDeleteGeminiChat = { viewModel.deleteGeminiChat(it) },
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
                        onOpenQuiz = { id, title -> viewModel.openQuiz(id, title) },
                        onExportQuiz = { id, title -> viewModel.exportQuizForBrainGate(this@MainActivity, id, title) },
                        onDeleteArtifact = { viewModel.deleteArtifact(it) },
                        onDeleteNote = { viewModel.deleteNote(it) },
                        discovery = viewModel.discovery.collectAsState().value,
                        onDiscoverSources = { viewModel.discoverSources(it) },
                        onToggleDiscoverySource = { viewModel.toggleDiscoverySource(it) },
                        onImportDiscovered = { viewModel.importDiscoveredSources() },
                        onDismissDiscovery = { viewModel.dismissDiscovery() },
                    )

                    is Screen.Quiz -> QuizScreen(
                        questions = s.questions,
                        title = s.title,
                        onBack = { viewModel.closeQuiz() },
                    )
                }
            }
            } // NotebookLmTheme
        }
    }
}
