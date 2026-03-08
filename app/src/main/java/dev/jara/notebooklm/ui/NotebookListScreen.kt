package dev.jara.notebooklm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jara.notebooklm.rpc.NotebookLmApi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookListScreen(
    notebooks: List<NotebookLmApi.Notebook>,
    loading: Boolean,
    semanticResults: List<String>?,
    searchLoading: Boolean,
    embeddingStatus: String?,
    hasApiKey: Boolean,
    favorites: Set<String>,
    sortMode: NotebookSort,
    onNotebookClick: (NotebookLmApi.Notebook) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onSemanticSearch: (String) -> Unit,
    onClearSemantic: () -> Unit,
    onEmbedNotebooks: (Set<String>?) -> Unit,
    onSettings: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCycleSort: () -> Unit,
    dedup: DeduplicationState,
    onStartDedup: () -> Unit,
    onDismissDedup: () -> Unit,
    classify: ClassificationState,
    categories: Map<String, String>,
    onStartClassify: () -> Unit,
    onClassifySelected: (Set<String>) -> Unit,
    onDismissClassify: () -> Unit,
    onCreateNotebook: (String, String) -> Unit,
    onDeleteNotebook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Search state (sdílený mezi bottomBar a content)
    var query by remember { mutableStateOf("") }
    var semanticMode by remember { mutableStateOf(false) }

    // Create notebook dialog
    var showCreateDialog by remember { mutableStateOf(false) }
    if (showCreateDialog) {
        CreateNotebookDialog(
            onConfirm = { title, emoji -> onCreateNotebook(title, emoji); showCreateDialog = false },
            onDismiss = { showCreateDialog = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = Term.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Term.surface,
                    contentColor = Term.text,
                    actionColor = Term.orange,
                    shape = RoundedCornerShape(DS.snackbarRadius),
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier
                .background(Term.bg)
                .navigationBarsPadding()
                .animateContentSize(animationSpec = tween(250))
            ) {
                // ── Search bar (thumb zone) ──
                val modeColor by animateColorAsState(
                    targetValue = if (semanticMode) Term.purple else Term.green,
                    animationSpec = tween(250),
                    label = "mode_color",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Term.bg)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(DS.searchRadius))
                        .background(if (semanticMode) Term.purple.copy(alpha = 0.06f) else Term.surfaceLight)
                        .border(DS.borderWidth, modeColor.copy(alpha = DS.borderAlpha), RoundedCornerShape(DS.searchRadius))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Mode toggle icon — tap to switch
                    Text(
                        text = if (semanticMode) "✨" else "🔍",
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                semanticMode = !semanticMode
                                if (!semanticMode) onClearSemantic()
                            }
                            .padding(end = 10.dp),
                    )

                    // Search input
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; if (!semanticMode) onClearSemantic() },
                        textStyle = TextStyle(
                            color = Term.white,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                        ),
                        cursorBrush = SolidColor(modeColor),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (semanticMode) ImeAction.Search else ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { if (semanticMode && query.isNotBlank()) onSemanticSearch(query) },
                            onDone = {},
                        ),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(
                                        text = if (semanticMode) "← hledej významem…" else "← hledej v názvech…",
                                        color = Term.textDim,
                                        fontFamily = Term.font,
                                        fontSize = Term.fontSize,
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )

                    // Clear
                    if (query.isNotEmpty()) {
                        Text(
                            text = "✕",
                            color = Term.textDim,
                            fontSize = Term.fontSize,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { query = ""; onClearSemantic() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (!WindowInsets.isImeVisible) {
                    BottomActionBar(
                        onCreateNotebook = { showCreateDialog = true },
                        onRefresh = onRefresh,
                        onSettings = onSettings,
                        hasApiKey = hasApiKey,
                        onStartDedup = { if (!dedup.running) onStartDedup() },
                        onCycleSort = onCycleSort,
                        sortLabel = sortMode.label,
                        onEmbedAll = { onEmbedNotebooks(null) },
                        onLogout = onLogout,
                    )
                }
            }
        },
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
    ) {
        // Selection mode
        var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        val selectionMode = selectedIds.isNotEmpty()

        // Delete notebook confirmation (single or batch)
        var deleteConfirmIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        if (deleteConfirmIds.isNotEmpty()) {
            val count = deleteConfirmIds.size
            val displayText = if (count == 1) {
                notebooks.find { it.id == deleteConfirmIds.first() }?.title ?: ""
            } else {
                "$count sešitů"
            }
            AlertDialog(
                onDismissRequest = { deleteConfirmIds = emptySet() },
                confirmButton = {
                    ActionPill("Smazat", Term.red) {
                        deleteConfirmIds.forEach { onDeleteNotebook(it) }
                        selectedIds = selectedIds - deleteConfirmIds
                        deleteConfirmIds = emptySet()
                    }
                },
                dismissButton = { ActionPill("Zrušit", Term.textDim) { deleteConfirmIds = emptySet() } },
                title = { Text(
                    if (count == 1) "Smazat sešit?" else "Smazat $count sešitů?",
                    color = Term.white, fontFamily = Term.font,
                    fontSize = Term.fontSizeLg, fontWeight = FontWeight.Bold) },
                text = { Text(displayText, color = Term.text, fontFamily = Term.font, fontSize = Term.fontSize) },
                containerColor = Term.surface,
                shape = RoundedCornerShape(DS.dialogRadius),
            )
        }

        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surface)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NotebookLM",
                color = Term.green,
                fontFamily = Term.font,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${notebooks.size}",
                color = Term.bg,
                fontFamily = Term.font,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Term.green.copy(alpha = 0.6f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            // Indikátor přihlášení
            Text(
                text = "●",
                color = Term.green,
                fontSize = 10.sp,
            )
        }

        // ── Selection action bar ──
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
                    color = Term.cyan,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (hasApiKey) {
                    ActionPill(
                        text = "Embed",
                        color = Term.green,
                        onClick = {
                            onEmbedNotebooks(selectedIds)
                            selectedIds = emptySet()
                        },
                    )
                    ActionPill(
                        text = "AI kat.",
                        color = Term.purple,
                        onClick = {
                            onClassifySelected(selectedIds)
                            selectedIds = emptySet()
                        },
                    )
                }
                ActionPill(
                    text = if (selectedIds.size > 1) "🗑 ${selectedIds.size}" else "🗑",
                    color = Term.red,
                    onClick = { deleteConfirmIds = selectedIds },
                )
                ActionPill(
                    text = "✕",
                    color = Term.textDim,
                    onClick = { selectedIds = emptySet() },
                )
            }
        }

        val fulltextFiltered = remember(notebooks, query, semanticMode) {
            if (semanticMode || query.isBlank()) notebooks
            else {
                val q = query.lowercase()
                notebooks.filter { nb ->
                    fuzzyMatch("${nb.emoji} ${nb.title}".lowercase(), q)
                }
            }
        }

        val displayList = if (semanticMode && semanticResults != null) {
            val order = semanticResults.withIndex().associate { (i, id) -> id to i }
            notebooks.filter { it.id in order }.sortedBy { order[it.id] ?: Int.MAX_VALUE }
        } else {
            fulltextFiltered
        }


        // ── Status bary ──
        if (embeddingStatus != null) {
            StatusBar(text = embeddingStatus, color = Term.orange)
        }
        if (dedup.running) {
            StatusBar(
                text = "Dedup: ${dedup.progress} — ${dedup.currentNotebook} (smazáno: ${dedup.totalDeleted})",
                color = Term.red,
            )
        } else if (dedup.done) {
            StatusBar(
                text = "Dedup hotovo — smazáno ${dedup.totalDeleted} duplikátů",
                color = Term.green,
                dismissText = "OK",
                onDismiss = onDismissDedup,
            )
        }
        if (classify.running) {
            StatusBar(text = "Klasifikace: ${classify.progress}", color = Term.purple)
        } else if (classify.done) {
            StatusBar(
                text = classify.progress,
                color = Term.green,
                dismissText = "OK",
                onDismiss = onDismissClassify,
            )
        } else if (classify.error != null) {
            StatusBar(text = "Chyba: ${classify.error}", color = Term.red)
        }

        // ── Obsah ──
        if (loading || searchLoading) {
            SkeletonList(modifier = Modifier.weight(1f))
        } else if (notebooks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "\uD83D\uDCD3", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Zatím žádné sešity",
                        color = Term.white,
                        fontFamily = Term.font,
                        fontSize = Term.fontSizeLg,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vytvoř si první sešit nebo synchronizuj z NotebookLM",
                        color = Term.textDim,
                        fontFamily = Term.font,
                        fontSize = Term.fontSize,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionPill("＋ Nový", Term.green) { showCreateDialog = true }
                        ActionPill("⟳ Sync", Term.cyan) { onRefresh() }
                    }
                }
            }
        } else {
            // Seznam
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sortMode == NotebookSort.CATEGORY) {
                    val grouped = displayList
                        .groupBy { (categories[it.id] ?: "Bez kategorie").lowercase().replaceFirstChar { c -> c.uppercase() } }
                        .toSortedMap()
                    for ((cat, nbs) in grouped) {
                        item(key = "cat_$cat") {
                            Text(
                                text = cat,
                                color = Term.purple,
                                fontFamily = Term.font,
                                fontSize = Term.fontSizeLg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                        for (nb in nbs) {
                            item(key = nb.id) {
                                SwipeableNotebookItem(
                                    nb = nb,
                                    isFavorite = nb.id in favorites,
                                    category = null,
                                    isSelected = nb.id in selectedIds,
                                    selectionMode = selectionMode,
                                    hasApiKey = hasApiKey,
                                    haptic = haptic,
                                    scope = scope,
                                    snackbarHostState = snackbarHostState,
                                    onClick = {
                                        if (selectionMode) selectedIds = selectedIds.toggle(nb.id)
                                        else onNotebookClick(nb)
                                    },
                                    onLongClick = { selectedIds = selectedIds.toggle(nb.id) },
                                    onToggleFavorite = { onToggleFavorite(nb.id) },
                                    onClassify = { onClassifySelected(setOf(nb.id)) },
                                    onEmbed = { onEmbedNotebooks(setOf(nb.id)) },
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(displayList) { _, nb ->
                        SwipeableNotebookItem(
                            nb = nb,
                            isFavorite = nb.id in favorites,
                            category = categories[nb.id],
                            isSelected = nb.id in selectedIds,
                            selectionMode = selectionMode,
                            hasApiKey = hasApiKey,
                            haptic = haptic,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            onClick = {
                                if (selectionMode) selectedIds = selectedIds.toggle(nb.id)
                                else onNotebookClick(nb)
                            },
                            onLongClick = { selectedIds = selectedIds.toggle(nb.id) },
                            onToggleFavorite = { onToggleFavorite(nb.id) },
                            onClassify = { onClassifySelected(setOf(nb.id)) },
                            onEmbed = { onEmbedNotebooks(setOf(nb.id)) },
                        )
                    }
                }
            }
        }

    } // Column
    } // Scaffold
}

// ══════════════════════════════════════════════════════════════════════════════
// KOMPONENTY
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActionPill(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DS.buttonRadius)
    Text(
        text = text,
        color = color,
        fontFamily = Term.font,
        fontSize = Term.fontSizeLg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(shape)
            .border(DS.borderWidth, color.copy(alpha = DS.borderAlpha), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}


@Composable
private fun StatusBar(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = Term.font,
            fontSize = Term.fontSize,
            modifier = Modifier.weight(1f),
        )
        if (dismissText != null && onDismiss != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = dismissText,
                color = color,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookCard(
    nb: NotebookLmApi.Notebook,
    isFavorite: Boolean,
    category: String?,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val shape = RoundedCornerShape(DS.cardRadius)
    val bgColor = if (isSelected) Term.cyan.copy(alpha = DS.selectionAlpha) else Term.surfaceLight
    val borderMod = if (isSelected) {
        Modifier.border(DS.borderWidthSelected, Term.cyan.copy(alpha = DS.borderAlpha), shape)
    } else {
        Modifier.border(DS.borderWidth, Term.border.copy(alpha = DS.borderAlpha), shape)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(borderMod)
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selection indicator
        if (selectionMode) {
            Text(
                text = if (isSelected) "◉" else "○",
                color = if (isSelected) Term.cyan else Term.textDim,
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        // Emoji
        if (nb.emoji.isNotEmpty()) {
            Text(
                text = nb.emoji,
                fontSize = 22.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        // Texty
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nb.title,
                color = Term.white,
                fontFamily = Term.font,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (category != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = category,
                    color = Term.purple,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                )
            }
        }

        // Favorite toggle
        if (!selectionMode) {
            Text(
                text = if (isFavorite) "★" else "☆",
                color = if (isFavorite) Term.orange else Term.textDim,
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleFavorite() }
                    .padding(8.dp),
            )
        }

        // Chevron
        Text(
            text = "›",
            color = Term.textDim,
            fontSize = 20.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotebookItem(
    nb: NotebookLmApi.Notebook,
    isFavorite: Boolean,
    category: String?,
    isSelected: Boolean,
    selectionMode: Boolean,
    hasApiKey: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClassify: () -> Unit,
    onEmbed: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { false },
    )

    // Spusť akci JEN když fyzický tah překročí práh
    // progress=1.0 skok = velocity animace, ne skutečný tah → ignorovat
    LaunchedEffect(Unit) {
        var triggered = false
        var maxDragProgress = 0f
        snapshotFlow { dismissState.progress to dismissState.dismissDirection }
            .collect { (progress, direction) ->
                // Sleduj maximální progress během skutečného tahu (ne velocity animace)
                if (progress > maxDragProgress && progress < 1.0f) {
                    maxDragProgress = progress
                }
                if (maxDragProgress >= 0.4f && !triggered && direction != SwipeToDismissBoxValue.Settled) {
                    triggered = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (hasApiKey) {
                        when (direction) {
                            SwipeToDismissBoxValue.EndToStart -> {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "AI klasifikace: ${nb.title.take(20)}",
                                        actionLabel = "Zpět",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result != SnackbarResult.ActionPerformed) {
                                        onClassify()
                                    }
                                }
                            }
                            SwipeToDismissBoxValue.StartToEnd -> {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Embed: ${nb.title.take(20)}",
                                        actionLabel = "Zpět",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result != SnackbarResult.ActionPerformed) {
                                        onEmbed()
                                    }
                                }
                            }
                            else -> {}
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
            val direction = dismissState.dismissDirection
            val bgColor = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Term.purple.copy(alpha = DS.selectionAlpha)
                SwipeToDismissBoxValue.StartToEnd -> Term.green.copy(alpha = DS.selectionAlpha)
                else -> Color.Transparent
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> "AI kat."
                SwipeToDismissBoxValue.StartToEnd -> "Embed"
                else -> ""
            }
            val iconColor = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Term.purple
                SwipeToDismissBoxValue.StartToEnd -> Term.green
                else -> Term.textDim
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(DS.cardRadius))
                    .background(bgColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                },
            ) {
                Text(
                    text = icon,
                    color = iconColor,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        content = {
            NotebookCard(
                nb = nb,
                isFavorite = isFavorite,
                category = category,
                isSelected = isSelected,
                selectionMode = selectionMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onToggleFavorite = onToggleFavorite,
            )
        },
    )
}

/** Toggle element v Set */
private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item

@Composable
private fun CreateNotebookDialog(
    onConfirm: (title: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ActionPill("Vytvořit", Term.green) {
                if (title.isNotBlank()) onConfirm(title.trim(), emoji.trim())
            }
        },
        dismissButton = { ActionPill("Zrušit", Term.textDim) { onDismiss() } },
        title = {
            Text("Nový sešit", color = Term.white, fontFamily = Term.font,
                fontSize = Term.fontSizeLg, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                androidx.compose.foundation.text.BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(color = Term.white, fontFamily = Term.font, fontSize = Term.fontSizeLg),
                    cursorBrush = SolidColor(Term.green),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Term.bg)
                                .padding(12.dp)
                        ) {
                            if (title.isEmpty()) Text("Název sešitu...", color = Term.textDim,
                                fontFamily = Term.font, fontSize = Term.fontSizeLg)
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(2) },
                    textStyle = TextStyle(color = Term.white, fontSize = 22.sp),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Term.bg)
                                .padding(12.dp)
                        ) {
                            if (emoji.isEmpty()) Text("📓", color = Term.textDim, fontSize = 22.sp)
                            inner()
                        }
                    },
                )
            }
        },
        containerColor = Term.surface,
        shape = RoundedCornerShape(DS.dialogRadius),
    )
}

/** Fuzzy match — kazdy znak query se musi vyskytovat v haystack v poradi */
private fun fuzzyMatch(haystack: String, query: String): Boolean {
    var qi = 0
    for (ch in haystack) {
        if (qi < query.length && ch == query[qi]) qi++
    }
    return qi == query.length
}

// ══════════════════════════════════════════════════════════════════════════════
// SKELETON SHIMMER LOADING
// ══════════════════════════════════════════════════════════════════════════════

/** Shimmer efekt pro skeleton loading */
@Composable
private fun ShimmerCard() {
    val shimmerColors = listOf(
        Term.surfaceLight.copy(alpha = 0.3f),
        Term.surfaceLight.copy(alpha = 0.6f),
        Term.surfaceLight.copy(alpha = 0.3f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = DS.shimmerDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200f, 0f),
        end = Offset(translateAnim.value, 0f),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.cardRadius))
            .background(Term.surfaceLight)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Emoji placeholder
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(DS.chipRadius))
                .background(brush),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Title placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Category placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush),
            )
        }
    }
}

@Composable
private fun SkeletonList(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(8) {
            ShimmerCard()
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// BOTTOM ACTION BAR
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BottomActionBar(
    onCreateNotebook: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    hasApiKey: Boolean,
    onStartDedup: () -> Unit,
    onCycleSort: () -> Unit,
    sortLabel: String,
    onEmbedAll: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Term.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomAction("＋", "Nový", Term.green) { onCreateNotebook() }
        BottomAction("⟳", "Sync", Term.cyan) { onRefresh() }
        BottomAction("↕", sortLabel, Term.orange) { onCycleSort() }
        BottomAction("⚙", "Nastavení", Term.textDim) { onSettings() }
        Box {
            BottomAction("⋯", "", Term.textDim) { menuExpanded = true }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(Term.surface),
            ) {
                if (hasApiKey) {
                    DropdownMenuItem(
                        text = { Text("Embed všechny", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                        onClick = { menuExpanded = false; onEmbedAll() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Deduplikace zdrojů", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                    onClick = { menuExpanded = false; onStartDedup() },
                )
                DropdownMenuItem(
                    text = { Text("Odhlásit", color = Term.red, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                    onClick = { menuExpanded = false; onLogout() },
                )
            }
        }
    }
}

@Composable
private fun BottomAction(
    icon: String,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = icon,
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = 11.sp,
            )
        }
    }
}
