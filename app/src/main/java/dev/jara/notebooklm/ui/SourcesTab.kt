package dev.jara.notebooklm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.jara.notebooklm.rpc.*

// ══════════════════════════════════════════════════════════════════════════════
// SOURCES TAB
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SourcesTab(
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
                    .clip(RoundedCornerShape(DS.dialogRadius))
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
            shape = RoundedCornerShape(DS.dialogRadius),
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
            shape = RoundedCornerShape(DS.snackbarRadius),
        )
    }
    } // Box
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SelectableSourceCard(
    src: Source,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) Term.green else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Term.green.copy(alpha = DS.selectionAlpha) else Term.surface)
            .border(DS.borderWidthSelected, borderColor, shape)
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
internal fun SwipeToDismissSourceCard(
    src: Source,
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
                        val job = launch {
                            delay(5000)
                            snackbarHostState.currentSnackbarData?.dismiss()
                        }
                        val result = snackbarHostState.showSnackbar(
                            message = "Smazat: ${src.title.take(25)}",
                            actionLabel = "Zpět",
                            duration = SnackbarDuration.Indefinite,
                        )
                        job.cancel()
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
internal fun SourceCard(
    src: Source,
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
internal fun AddSourceDialog(
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
                .clip(RoundedCornerShape(DS.dialogRadius))
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
            Spacer(modifier = Modifier.height(16.dp))

            // Typ
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((type, label) in listOf("url" to "🌐 Web", "youtube" to "🎥 YT", "text" to "📝 Text")) {
                    val selected = selectedType == type
                    val shape = RoundedCornerShape(DS.buttonRadius)
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
                                    .background(Term.green.copy(alpha = DS.selectionAlpha))
                                    .border(DS.borderWidthSelected, Term.green.copy(alpha = DS.borderAlpha), shape)
                                else Modifier.background(Term.bg)
                            )
                            .clickable { selectedType = type }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailPill("Zrušit", Term.textDim) { onDismiss() }
                Spacer(modifier = Modifier.width(8.dp))
                DetailPill("Přidat", Term.green) {
                    if (value.isNotBlank()) onAdd(selectedType, value.trim(), title.trim())
                }
            }
        }
    }
}
