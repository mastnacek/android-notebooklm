package dev.jara.notebooklm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import dev.jara.notebooklm.rpc.NotebookLmApi

@Composable
fun NotebookDetailScreen(
    notebook: NotebookLmApi.Notebook,
    detail: DetailState,
    onBack: () -> Unit,
    onTabSwitch: (DetailTab) -> Unit,
    onSendChat: (String) -> Unit,
    onSaveAsNote: (String) -> Unit,
    onPlayAudio: (String, String) -> Unit,
    onStopAudio: () -> Unit,
    onDownloadAudio: (NotebookLmApi.Artifact) -> Unit,
    onAddSource: (type: String, value: String, title: String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onDeleteSources: (Set<String>) -> Unit,
    onDedupSources: () -> Unit,
    onDismissDedup: () -> Unit,
    dedup: DeduplicationState,
    onGenerateArtifact: (NotebookLmApi.GenerateType, NotebookLmApi.GenerateOptions) -> Unit,
    onOpenInteractiveHtml: (String) -> Unit,
    onDeleteArtifact: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Term.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        // ── Header ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            val prefix = if (notebook.emoji.isNotEmpty()) "${notebook.emoji} " else ""
            Text(
                text = "$prefix${notebook.title}",
                color = Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }

        // Audio player bar
        if (detail.audioPlayer != null) {
            AudioPlayerBar(
                url = detail.audioPlayer.url,
                title = detail.audioPlayer.title,
                cookies = detail.audioPlayer.cookies,
                onClose = onStopAudio,
            )
        }

        if (detail.loading) {
            val infiniteTransition = rememberInfiniteTransition(label = "detail_loading")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "detail_shimmer",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Skeleton karty napodobující obsah
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (it == 0) 100.dp else 60.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Term.surfaceLight.copy(alpha = alpha)),
                    )
                }
            }
        } else {
            when (detail.tab) {
                DetailTab.CHAT -> ChatTab(
                    detail, onSendChat, onSaveAsNote,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                DetailTab.SOURCES -> SourcesTab(
                    detail, onAddSource, onDeleteSource, onDeleteSources,
                    onDedupSources, onDismissDedup, dedup,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                DetailTab.ARTIFACTS -> ArtifactsTab(
                    detail, onPlayAudio, onDownloadAudio, onGenerateArtifact,
                    onOpenInteractiveHtml, onDeleteArtifact, detail.downloads,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                DetailTab.NOTES -> NotesTab(
                    detail, onDeleteNote,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }

        // ── Bottom tab bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Taby (bez zpět — Android 13+ predictive back gesture)
            for (tab in DetailTab.entries) {
                val selected = detail.tab == tab
                val (label, color) = when (tab) {
                    DetailTab.CHAT -> "💬" to Term.green
                    DetailTab.SOURCES -> "📚 ${detail.sources.size}" to Term.cyan
                    DetailTab.ARTIFACTS -> "🎨 ${detail.artifacts.size}" to Term.purple
                    DetailTab.NOTES -> "📝 ${detail.notes.size}" to Term.orange
                }
                val shape = RoundedCornerShape(10.dp)
                Text(
                    text = label,
                    color = if (selected) color else Term.textDim,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(shape)
                        .then(
                            if (selected) Modifier
                                .background(color.copy(alpha = 0.12f))
                                .border(1.dp, color.copy(alpha = 0.3f), shape)
                            else Modifier
                        )
                        .clickable { onTabSwitch(tab) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CHAT TAB
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatTab(
    detail: DetailState,
    onSendChat: (String) -> Unit,
    onSaveAsNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(detail.chatMessages.size) {
        if (detail.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(detail.chatMessages.size - 1)
        }
    }

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary
            if (!detail.summary.isNullOrBlank() && detail.chatMessages.isEmpty()) {
                item {
                    SummaryCard(detail.summary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Zeptej se na cokoliv...",
                        color = Term.textDim,
                        fontFamily = Term.font,
                        fontSize = Term.fontSize,
                    )
                }
            }

            items(detail.chatMessages) { msg ->
                ChatBubble(msg, onSaveAsNote = onSaveAsNote)
            }

            if (detail.chatAnswering) {
                item {
                    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "thinking_alpha",
                    )
                    Text(
                        text = "Přemýšlím...",
                        color = Term.orange.copy(alpha = alpha),
                        fontFamily = Term.font,
                        fontSize = Term.fontSize,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }

        // Input card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Term.surfaceLight)
                .border(1.dp, Term.green.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                ),
                cursorBrush = SolidColor(Term.green),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (input.isNotBlank() && !detail.chatAnswering) {
                            onSendChat(input.trim())
                            input = ""
                        }
                    },
                ),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                text = "Zeptej se...",
                                color = Term.textDim,
                                fontFamily = Term.font,
                                fontSize = Term.fontSizeLg,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            if (input.isNotBlank() && !detail.chatAnswering) {
                Spacer(modifier = Modifier.width(8.dp))
                DetailPill("Odeslat", Term.green) {
                    onSendChat(input.trim())
                    input = ""
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: String) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Term.surface.copy(alpha = 0.7f))
            .border(1.dp, Term.cyan.copy(alpha = 0.3f), shape)
            .padding(16.dp),
    ) {
        Text(
            text = "📋 Souhrn",
            color = Term.cyan,
            fontFamily = Term.font,
            fontSize = Term.fontSizeLg,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MarkdownText(text = summary, color = Term.text)
    }
}

@Composable
private fun ChatBubble(
    msg: NotebookLmApi.ChatMessage,
    onSaveAsNote: (String) -> Unit,
) {
    val isUser = msg.role == NotebookLmApi.ChatRole.USER
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Akce — ikonky
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (isUser) "Ty" else "NLM",
                color = if (isUser) Term.cyan else Term.green,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(6.dp))

            if (!isUser) {
                var saved by remember { mutableStateOf(false) }
                MicroAction(if (saved) "✓" else "📝", if (saved) Term.green else Term.textDim) {
                    if (!saved) { onSaveAsNote(msg.text); saved = true }
                }
            }

            var copied by remember { mutableStateOf(false) }
            MicroAction(if (copied) "✓" else "📋", if (copied) Term.green else Term.textDim) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("chat", msg.text))
                copied = true
            }
            IconMicroAction(Icons.Default.Share, Term.textDim) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, msg.text)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Bublina
        val bubbleBg = if (isUser) Term.surfaceLight else Term.surface.copy(alpha = 0.7f)
        val bubbleBorder = if (isUser) Term.cyan.copy(alpha = 0.15f) else Term.green.copy(alpha = 0.2f)
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 0.92f)
                .clip(shape)
                .background(bubbleBg)
                .border(1.dp, bubbleBorder, shape)
                .padding(14.dp),
        ) {
            if (isUser) {
                Text(
                    text = msg.text,
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                    lineHeight = Term.fontSize * 1.4f,
                )
            } else {
                MarkdownText(text = msg.text, color = Term.text)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SOURCES TAB
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SourcesTab(
    detail: DetailState,
    onAddSource: (type: String, value: String, title: String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onDeleteSources: (Set<String>) -> Unit,
    onDedupSources: () -> Unit,
    onDismissDedup: () -> Unit,
    dedup: DeduplicationState,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var deleteConfirmIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    if (showAddDialog) {
        AddSourceDialog(
            onAdd = { type, value, title -> onAddSource(type, value, title); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }

    // Dedup progress dialog
    if (dedup.running || dedup.done || dedup.error != null) {
        Dialog(
            onDismissRequest = { if (!dedup.running) onDismissDedup() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Term.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Deduplikace zdrojů",
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (dedup.running) {
                    CircularProgressIndicator(
                        color = Term.orange,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Hledám duplikáty…",
                        color = Term.textDim,
                        fontFamily = Term.font,
                        fontSize = Term.fontSize,
                    )
                    if (dedup.totalDeleted > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Smazáno: ${dedup.totalDeleted}",
                            color = Term.orange,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                        )
                    }
                } else if (dedup.error != null) {
                    Text("⚠", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dedup.error,
                        color = Term.red,
                        fontFamily = Term.font,
                        fontSize = Term.fontSize,
                    )
                } else if (dedup.done) {
                    Text(
                        text = if (dedup.totalDeleted > 0) "✓" else "👍",
                        fontSize = 32.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (dedup.totalDeleted > 0)
                            "Smazáno ${dedup.totalDeleted} duplikátů"
                        else
                            "Žádné duplikáty nenalezeny",
                        color = if (dedup.totalDeleted > 0) Term.green else Term.text,
                        fontFamily = Term.font,
                        fontSize = Term.fontSize,
                    )
                }

                if (!dedup.running) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailPill("Zavřít", Term.textDim) { onDismissDedup() }
                }
            }
        }
    }

    if (deleteConfirmIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteConfirmIds = emptySet() },
            confirmButton = {
                DetailPill("Smazat ${deleteConfirmIds.size}", Term.red) {
                    onDeleteSources(deleteConfirmIds)
                    selectedIds = selectedIds - deleteConfirmIds
                    deleteConfirmIds = emptySet()
                }
            },
            dismissButton = {
                DetailPill("Zrušit", Term.textDim) { deleteConfirmIds = emptySet() }
            },
            title = {
                Text("Smazat ${deleteConfirmIds.size} zdrojů?", color = Term.white, fontFamily = Term.font,
                    fontSize = Term.fontSizeLg, fontWeight = FontWeight.Bold)
            },
            containerColor = Term.surface,
            shape = RoundedCornerShape(16.dp),
        )
    }

    Box(modifier = modifier) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (detail.sources.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = "\uD83D\uDCDA", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Žádné zdroje",
                            color = Term.white,
                            fontFamily = Term.font,
                            fontSize = Term.fontSizeLg,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Přidej webové stránky, texty nebo YouTube videa",
                            color = Term.textDim,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                        )
                    }
                }
            } else {
                items(detail.sources, key = { it.id }) { src ->
                    val selected = src.id in selectedIds
                    if (selectionMode) {
                        SelectableSourceCard(
                            src = src,
                            selected = selected,
                            onClick = { selectedIds = selectedIds.toggle(src.id) },
                        )
                    } else {
                        SwipeToDismissSourceCard(
                            src = src,
                            onDeleteSource = onDeleteSource,
                            onLongClick = { selectedIds = selectedIds + src.id },
                            haptic = haptic,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                        )
                    }
                }
            }
        }

        // Selection bar nebo action bar
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Term.surfaceLight)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${selectedIds.size}×",
                    color = Term.green,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                DetailPill("🗑 ${selectedIds.size}", Term.red) {
                    deleteConfirmIds = selectedIds
                }
                DetailPill("✕", Term.textDim) { selectedIds = emptySet() }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Term.surface)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailPill("＋ Zdroj", Term.green) { showAddDialog = true }
                Spacer(modifier = Modifier.weight(1f))
                if (detail.sources.size >= 2) {
                    DetailPill("Deduplikace", Term.orange) { onDedupSources() }
                }
            }
        }
    } // Column

    // Snackbar overlay
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = Term.surface,
            contentColor = Term.text,
            actionColor = Term.orange,
            shape = RoundedCornerShape(12.dp),
        )
    }
    } // Box
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableSourceCard(
    src: NotebookLmApi.Source,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) Term.green else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Term.green.copy(alpha = 0.1f) else Term.surface)
            .border(1.5.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "✓" else src.type.icon,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = src.title,
            color = if (selected) Term.green else Term.text,
            fontFamily = Term.font,
            fontSize = Term.fontSize,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeToDismissSourceCard(
    src: NotebookLmApi.Source,
    onDeleteSource: (String) -> Unit,
    onLongClick: () -> Unit = {},
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback = LocalHapticFeedback.current,
    scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { false },
    )

    LaunchedEffect(Unit) {
        var triggered = false
        var maxDragProgress = 0f
        snapshotFlow { dismissState.progress to dismissState.dismissDirection }
            .collect { (progress, direction) ->
                if (progress > maxDragProgress && progress < 1.0f) {
                    maxDragProgress = progress
                }
                if (maxDragProgress >= 0.4f && !triggered && direction == SwipeToDismissBoxValue.EndToStart) {
                    triggered = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Smazat: ${src.title.take(25)}",
                            actionLabel = "Zpět",
                            duration = SnackbarDuration.Long,
                        )
                        if (result != SnackbarResult.ActionPerformed) {
                            onDeleteSource(src.id)
                        }
                    }
                }
                if (progress < 0.05f) {
                    triggered = false
                    maxDragProgress = 0f
                }
            }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Term.red
                else Term.red.copy(alpha = 0.3f),
                label = "swipeBg",
            )
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1.1f else 0.8f,
                label = "swipeScale",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("🗑", fontSize = 20.sp, modifier = Modifier.scale(scale))
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        SourceCard(src, onLongClick = onLongClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceCard(
    src: NotebookLmApi.Source,
    onLongClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Term.surface)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = src.type.icon,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = src.title,
            color = Term.text,
            fontFamily = Term.font,
            fontSize = Term.fontSize,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "‹ swipe",
            color = Term.textDim,
            fontFamily = Term.font,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun AddSourceDialog(
    onAdd: (type: String, value: String, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedType by remember { mutableStateOf("url") }
    var value by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Term.surface)
                .padding(24.dp),
        ) {
            Text(
                text = "Přidat zdroj",
                color = Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSizeXl,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(18.dp))

            // Typ
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((type, label) in listOf("url" to "🌐 Web", "youtube" to "🎥 YT", "text" to "📝 Text")) {
                    val selected = selectedType == type
                    val shape = RoundedCornerShape(12.dp)
                    Text(
                        text = label,
                        color = if (selected) Term.green else Term.textDim,
                        fontFamily = Term.font,
                        fontSize = Term.fontSize,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(shape)
                            .then(
                                if (selected) Modifier
                                    .background(Term.green.copy(alpha = 0.15f))
                                    .border(1.5.dp, Term.green.copy(alpha = 0.5f), shape)
                                else Modifier.background(Term.bg)
                            )
                            .clickable { selectedType = type }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            if (selectedType == "text") {
                DetailInput(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Název...",
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            DetailInput(
                value = value,
                onValueChange = { value = it },
                placeholder = when (selectedType) {
                    "url" -> "https://..."
                    "youtube" -> "https://youtube.com/..."
                    else -> "Obsah textu..."
                },
                singleLine = selectedType != "text",
                maxLines = if (selectedType == "text") 6 else 1,
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailPill("Zrušit", Term.textDim) { onDismiss() }
                Spacer(modifier = Modifier.width(10.dp))
                DetailPill("Přidat", Term.green) {
                    if (value.isNotBlank()) onAdd(selectedType, value.trim(), title.trim())
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ARTIFACTS TAB
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ArtifactsTab(
    detail: DetailState,
    onPlayAudio: (String, String) -> Unit,
    onDownloadAudio: (NotebookLmApi.Artifact) -> Unit,
    onGenerateArtifact: (NotebookLmApi.GenerateType, NotebookLmApi.GenerateOptions) -> Unit,
    onOpenInteractiveHtml: (String) -> Unit,
    onDeleteArtifact: (String) -> Unit,
    downloads: Map<String, DownloadState>,
    modifier: Modifier = Modifier,
) {
    var showGenerate by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (detail.artifacts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(text = "\uD83C\uDFA8", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Zatím žádné artefakty",
                                color = Term.white,
                                fontFamily = Term.font,
                                fontSize = Term.fontSizeLg,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Vygeneruj audio, report nebo prezentaci",
                                color = Term.textDim,
                                fontFamily = Term.font,
                                fontSize = Term.fontSize,
                            )
                        }
                    }
                }

                items(detail.artifacts, key = { it.id }) { art ->
                    SwipeToDismissArtifactCard(
                        art, onPlayAudio, onDownloadAudio, onOpenInteractiveHtml,
                        onDeleteArtifact, downloads[art.id],
                        haptic, scope, snackbarHostState,
                    )
                }
            }

            // Generovací panel — dole nad bottom barem (thumb area)
            if (showGenerate) {
                GenerateArtifactPanel(onGenerateArtifact) { showGenerate = false }
            }

            // Bottom bar s tlačítkem Generovat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Term.surface)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailPill(
                if (showGenerate) "✕ Zavřít" else "＋ Generovat",
                if (showGenerate) Term.textDim else Term.purple,
            ) { showGenerate = !showGenerate }
        }
        } // Column

        // Snackbar overlay
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Term.surface,
                contentColor = Term.text,
                actionColor = Term.orange,
                shape = RoundedCornerShape(12.dp),
            )
        }
    } // Box
}

@Composable
private fun GenerateArtifactPanel(
    onGenerate: (NotebookLmApi.GenerateType, NotebookLmApi.GenerateOptions) -> Unit,
    onClose: () -> Unit,
) {
    var selectedType by remember { mutableStateOf<NotebookLmApi.GenerateType?>(null) }

    // Options state
    var audioFormat by remember { mutableStateOf(NotebookLmApi.AudioFormat.DEEP_DIVE) }
    var audioLength by remember { mutableStateOf(NotebookLmApi.AudioLength.DEFAULT) }
    var videoFormat by remember { mutableStateOf(NotebookLmApi.VideoFormat.EXPLAINER) }
    var videoStyle by remember { mutableStateOf(NotebookLmApi.VideoStyle.AUTO) }
    var quizDifficulty by remember { mutableStateOf(NotebookLmApi.QuizDifficulty.MEDIUM) }
    var quizQuantity by remember { mutableStateOf(NotebookLmApi.QuizQuantity.STANDARD) }
    var infraOrientation by remember { mutableStateOf(NotebookLmApi.InfographicOrientation.PORTRAIT) }
    var infraDetail by remember { mutableStateOf(NotebookLmApi.InfographicDetail.STANDARD) }
    var slideFormat by remember { mutableStateOf(NotebookLmApi.SlideDeckFormat.DETAILED) }
    var slideLength by remember { mutableStateOf(NotebookLmApi.SlideDeckLength.DEFAULT) }
    var instructions by remember { mutableStateOf("") }

    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Term.surface)
            .border(1.dp, Term.purple.copy(alpha = 0.2f), shape)
            .padding(14.dp),
    ) {
        if (selectedType == null) {
            // Grid vyber typu
            Text("Generovat", color = Term.purple, fontFamily = Term.font,
                fontSize = Term.fontSizeLg, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            val types = NotebookLmApi.GenerateType.entries
            for (row in types.chunked(4)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    for (type in row) {
                        val icon = generateTypeIcon(type)
                        val btnShape = RoundedCornerShape(10.dp)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(btnShape)
                                .background(Term.bg)
                                .clickable { selectedType = type }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(text = icon, fontSize = 22.sp)
                            Text(type.label, color = Term.textDim, fontFamily = Term.font,
                                fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        } else {
            // Parametry pro vybrany typ
            val type = selectedType!!
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroAction("‹", Term.cyan) { selectedType = null }
                Spacer(modifier = Modifier.width(6.dp))
                Text("${generateTypeIcon(type)} ${type.label}", color = Term.purple,
                    fontFamily = Term.font, fontSize = Term.fontSizeLg, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))

            when (type) {
                NotebookLmApi.GenerateType.AUDIO -> {
                    OptionRow("Formát") {
                        for (f in NotebookLmApi.AudioFormat.entries) {
                            OptionChip(f.label, f == audioFormat) { audioFormat = f }
                        }
                    }
                    OptionRow("Délka") {
                        for (l in NotebookLmApi.AudioLength.entries) {
                            OptionChip(l.label, l == audioLength) { audioLength = l }
                        }
                    }
                }
                NotebookLmApi.GenerateType.VIDEO -> {
                    OptionRow("Formát") {
                        for (f in NotebookLmApi.VideoFormat.entries) {
                            OptionChip(f.label, f == videoFormat) { videoFormat = f }
                        }
                    }
                    OptionRow("Styl") {
                        for (s in NotebookLmApi.VideoStyle.entries) {
                            OptionChip(s.label, s == videoStyle) { videoStyle = s }
                        }
                    }
                }
                NotebookLmApi.GenerateType.QUIZ -> {
                    OptionRow("Obtížnost") {
                        for (d in NotebookLmApi.QuizDifficulty.entries) {
                            OptionChip(d.label, d == quizDifficulty) { quizDifficulty = d }
                        }
                    }
                    OptionRow("Množství") {
                        for (q in NotebookLmApi.QuizQuantity.entries) {
                            OptionChip(q.label, q == quizQuantity) { quizQuantity = q }
                        }
                    }
                }
                NotebookLmApi.GenerateType.INFOGRAPHIC -> {
                    OptionRow("Orientace") {
                        for (o in NotebookLmApi.InfographicOrientation.entries) {
                            OptionChip(o.label, o == infraOrientation) { infraOrientation = o }
                        }
                    }
                    OptionRow("Detail") {
                        for (d in NotebookLmApi.InfographicDetail.entries) {
                            OptionChip(d.label, d == infraDetail) { infraDetail = d }
                        }
                    }
                }
                NotebookLmApi.GenerateType.SLIDE_DECK -> {
                    OptionRow("Formát") {
                        for (f in NotebookLmApi.SlideDeckFormat.entries) {
                            OptionChip(f.label, f == slideFormat) { slideFormat = f }
                        }
                    }
                    OptionRow("Délka") {
                        for (l in NotebookLmApi.SlideDeckLength.entries) {
                            OptionChip(l.label, l == slideLength) { slideLength = l }
                        }
                    }
                }
                else -> {} // Mind map, Data table — bez extra parametru
            }

            // Instrukce — pro vsechny typy
            Spacer(modifier = Modifier.height(8.dp))
            DetailInput(
                value = instructions,
                onValueChange = { instructions = it },
                placeholder = "Vlastní instrukce (volitelné)...",
                singleLine = false,
                maxLines = 3,
            )

            Spacer(modifier = Modifier.height(10.dp))
            DetailPill("▶ Spustit", Term.green) {
                val opts = NotebookLmApi.GenerateOptions(
                    instructions = instructions.takeIf { it.isNotBlank() },
                    audioFormat = audioFormat,
                    audioLength = audioLength,
                    videoFormat = videoFormat,
                    videoStyle = videoStyle,
                    quizDifficulty = quizDifficulty,
                    quizQuantity = quizQuantity,
                    infographicOrientation = infraOrientation,
                    infographicDetail = infraDetail,
                    slideDeckFormat = slideFormat,
                    slideDeckLength = slideLength,
                )
                onGenerate(type, opts)
                onClose()
            }
        }
    }
}

private fun generateTypeIcon(type: NotebookLmApi.GenerateType): String = when (type) {
    NotebookLmApi.GenerateType.AUDIO -> "🎧"
    NotebookLmApi.GenerateType.VIDEO -> "🎥"
    NotebookLmApi.GenerateType.QUIZ -> "❓"
    NotebookLmApi.GenerateType.MIND_MAP -> "🧠"
    NotebookLmApi.GenerateType.INFOGRAPHIC -> "🖼️"
    NotebookLmApi.GenerateType.SLIDE_DECK -> "📊"
    NotebookLmApi.GenerateType.DATA_TABLE -> "📋"
}

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(label, color = Term.textDim, fontFamily = Term.font, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val chipShape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        color = if (selected) Term.green else Term.textDim,
        fontFamily = Term.font,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(chipShape)
            .then(
                if (selected) Modifier
                    .background(Term.green.copy(alpha = 0.12f))
                    .border(1.dp, Term.green.copy(alpha = 0.3f), chipShape)
                else Modifier.background(Term.bg)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissArtifactCard(
    art: NotebookLmApi.Artifact,
    onPlayAudio: (String, String) -> Unit,
    onDownloadAudio: (NotebookLmApi.Artifact) -> Unit,
    onOpenInteractiveHtml: (String) -> Unit,
    onDeleteArtifact: (String) -> Unit,
    downloadState: DownloadState?,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { false },
    )

    LaunchedEffect(Unit) {
        var triggered = false
        var maxDragProgress = 0f
        snapshotFlow { dismissState.progress to dismissState.dismissDirection }
            .collect { (progress, direction) ->
                if (progress > maxDragProgress && progress < 1.0f) {
                    maxDragProgress = progress
                }
                if (maxDragProgress >= 0.4f && !triggered && direction == SwipeToDismissBoxValue.EndToStart) {
                    triggered = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Smazat: ${art.title.take(25)}",
                            actionLabel = "Zpět",
                            duration = SnackbarDuration.Long,
                        )
                        if (result != SnackbarResult.ActionPerformed) {
                            onDeleteArtifact(art.id)
                        }
                    }
                }
                if (progress < 0.05f) {
                    triggered = false
                    maxDragProgress = 0f
                }
            }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Term.red
                else Term.red.copy(alpha = 0.3f),
                label = "swipeBg",
            )
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1.1f else 0.8f,
                label = "swipeScale",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("🗑", fontSize = 20.sp, modifier = Modifier.scale(scale))
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        ArtifactCard(art, onPlayAudio, onDownloadAudio, onOpenInteractiveHtml, downloadState)
    }
}

@Composable
private fun ArtifactCard(
    art: NotebookLmApi.Artifact,
    onPlayAudio: (String, String) -> Unit,
    onDownloadAudio: (NotebookLmApi.Artifact) -> Unit,
    onOpenInteractiveHtml: (String) -> Unit,
    downloadState: DownloadState?,
) {
    val shape = RoundedCornerShape(14.dp)
    val statusColor = when (art.status) {
        NotebookLmApi.ArtifactStatus.COMPLETED -> Term.green
        NotebookLmApi.ArtifactStatus.PROCESSING -> Term.orange
        NotebookLmApi.ArtifactStatus.PENDING -> Term.cyan
        NotebookLmApi.ArtifactStatus.FAILED -> Term.red
    }
    val isDownloading = downloadState != null && !downloadState.done && downloadState.error == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Term.surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = art.type.icon, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = art.title,
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    text = "${art.type.label} • ${art.status.label}",
                    color = statusColor,
                    fontFamily = Term.font,
                    fontSize = 11.sp,
                )
            }

            // Akce — kompaktní, na stejném řádku vpravo
            if (art.url != null && art.status == NotebookLmApi.ArtifactStatus.COMPLETED) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (art.type == NotebookLmApi.ArtifactType.AUDIO || art.type == NotebookLmApi.ArtifactType.VIDEO) {
                        DetailPill("▶", Term.green) { onPlayAudio(art.url!!, art.title) }
                    }
                    if (art.type == NotebookLmApi.ArtifactType.QUIZ) {
                        DetailPill("🎮", Term.orange) { onOpenInteractiveHtml(art.id) }
                    }

                    if (!isDownloading) {
                        if (downloadState?.done == true) {
                            DetailPill("✓", Term.green) {}
                            val context = LocalContext.current
                            DetailPill("↗", Term.orange) {
                                if (downloadState.filePath != null) {
                                    val uri = android.net.Uri.parse(downloadState.filePath)
                                    val mime = when (art.type) {
                                        NotebookLmApi.ArtifactType.AUDIO -> "audio/*"
                                        NotebookLmApi.ArtifactType.VIDEO -> "video/*"
                                        NotebookLmApi.ArtifactType.SLIDE_DECK -> "application/pdf"
                                        NotebookLmApi.ArtifactType.INFOGRAPHIC -> "image/*"
                                        else -> "*/*"
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mime)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        } else {
                            DetailPill("⬇", Term.cyan) { onDownloadAudio(art) }
                        }
                    }
                }
            }
        }

        // Download progress
        if (isDownloading) {
            Spacer(modifier = Modifier.height(8.dp))
            val progressText = if (downloadState!!.progress < 0) "Stahuji..."
                else "${(downloadState.progress * 100).toInt()}%"
            Text(text = progressText, color = Term.orange, fontFamily = Term.font, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            if (downloadState.progress >= 0) {
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = Term.cyan,
                    trackColor = Term.border,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = Term.cyan,
                    trackColor = Term.border,
                )
            }
        }

        if (downloadState?.error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Chyba: ${downloadState.error}",
                color = Term.red,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// NOTES TAB
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesTab(
    detail: DetailState,
    onDeleteNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (detail.notes.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "\uD83D\uDCDD", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Zatím žádné poznámky",
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ulož si odpovědi z chatu jako poznámky",
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(detail.notes, key = { it.id }) { note ->
                SwipeToDismissNoteCard(note, onDeleteNote)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissNoteCard(
    note: NotebookLmApi.Note,
    onDeleteNote: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                confirmDelete = true
                false
            } else false
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                DetailPill("Smazat", Term.red) {
                    onDeleteNote(note.id)
                    confirmDelete = false
                }
            },
            dismissButton = {
                DetailPill("Zrušit", Term.textDim) { confirmDelete = false }
            },
            title = {
                Text("Smazat poznámku?", color = Term.white, fontFamily = Term.font,
                    fontSize = Term.fontSizeLg, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(note.title.ifBlank { note.content.take(60) }, color = Term.text,
                    fontFamily = Term.font, fontSize = Term.fontSize)
            },
            containerColor = Term.surface,
            shape = RoundedCornerShape(16.dp),
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Term.red
                else Term.red.copy(alpha = 0.3f),
                label = "swipeBg",
            )
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1.1f else 0.8f,
                label = "swipeScale",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("🗑", fontSize = 20.sp, modifier = Modifier.scale(scale))
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        NoteCard(note)
    }
}

@Composable
private fun NoteCard(note: NotebookLmApi.Note) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val context = LocalContext.current

    val displayTitle = note.title.ifBlank {
        note.content.lineSequence()
            .map { it.trimStart('#', ' ', '*', '-') }
            .firstOrNull { it.isNotBlank() }
            ?.take(60) ?: "Poznámka"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Term.surface)
            .clickable { expanded = !expanded }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "▾" else "▸",
                color = Term.textDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = displayTitle,
                color = Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            MicroAction("📋", Term.textDim) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("note", note.content))
            }
            IconMicroAction(Icons.Default.Share, Term.textDim) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, note.content)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }
        }
        if (expanded && note.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownText(text = note.content, color = Term.text)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SHARED KOMPONENTY
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DetailPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val isSingleChar = text.codePointCount(0, text.length) == 1
    Text(
        text = text,
        color = color,
        fontFamily = Term.font,
        fontSize = Term.fontSize,
        fontWeight = FontWeight.SemiBold,
        textAlign = if (isSingleChar) androidx.compose.ui.text.style.TextAlign.Center else null,
        modifier = modifier
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .then(
                if (isSingleChar) Modifier.defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                    .padding(horizontal = 7.dp, vertical = 7.dp)
                else Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ),
    )
}

@Composable
private fun IconMicroAction(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    )
}

@Composable
private fun IconPill(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(30.dp)
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(5.dp),
    )
}

@Composable
private fun MicroAction(icon: String, color: Color, onClick: () -> Unit) {
    Text(
        text = icon,
        color = color,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp),
    )
}

@Composable
private fun DetailInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    maxLines: Int = 1,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = Term.white,
            fontFamily = Term.font,
            fontSize = Term.fontSize,
        ),
        cursorBrush = SolidColor(Term.green),
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Term.bg)
                    .padding(12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = Term.textDim, fontFamily = Term.font, fontSize = Term.fontSize)
                }
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
