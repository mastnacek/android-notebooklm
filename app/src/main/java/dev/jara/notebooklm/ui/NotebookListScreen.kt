package dev.jara.notebooklm.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jara.notebooklm.rpc.NotebookLmApi

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Term.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Selection mode
        var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        val selectionMode = selectedIds.isNotEmpty()

        // Create notebook dialog
        var showCreateDialog by remember { mutableStateOf(false) }
        if (showCreateDialog) {
            CreateNotebookDialog(
                onConfirm = { title, emoji -> onCreateNotebook(title, emoji); showCreateDialog = false },
                onDismiss = { showCreateDialog = false },
            )
        }
        // Delete notebook confirmation
        var deleteConfirmId by remember { mutableStateOf<String?>(null) }
        if (deleteConfirmId != null) {
            val nbTitle = notebooks.find { it.id == deleteConfirmId }?.title ?: ""
            AlertDialog(
                onDismissRequest = { deleteConfirmId = null },
                confirmButton = {
                    ActionPill("Smazat", Term.red) {
                        onDeleteNotebook(deleteConfirmId!!)
                        selectedIds = selectedIds - deleteConfirmId!!
                        deleteConfirmId = null
                    }
                },
                dismissButton = { ActionPill("Zrušit", Term.textDim) { deleteConfirmId = null } },
                title = { Text("Smazat sešit?", color = Term.white, fontFamily = Term.font,
                    fontSize = Term.fontSizeLg, fontWeight = FontWeight.Bold) },
                text = { Text(nbTitle, color = Term.text, fontFamily = Term.font, fontSize = Term.fontSize) },
                containerColor = Term.surface,
                shape = RoundedCornerShape(16.dp),
            )
        }

        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surface)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NotebookLM",
                color = Term.green,
                fontFamily = Term.font,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
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
                if (selectedIds.size == 1) {
                    ActionPill(
                        text = "🗑",
                        color = Term.red,
                        onClick = { deleteConfirmId = selectedIds.first() },
                    )
                }
                ActionPill(
                    text = "✕",
                    color = Term.textDim,
                    onClick = { selectedIds = emptySet() },
                )
            }
        }

        // ── Search bar ──
        var query by remember { mutableStateOf("") }
        var semanticMode by remember { mutableStateOf(false) }

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

        // ── Count + mode info ──
        val countText = when {
            semanticMode && semanticResults != null -> "${displayList.size} výsledků (semantic)"
            query.isNotBlank() && !semanticMode -> "${displayList.size}/${notebooks.size} sešitů"
            else -> "${notebooks.size} sešitů"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = countText,
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (semanticMode) "semantic" else "fulltext",
                color = if (semanticMode) Term.purple else Term.green,
                fontFamily = Term.font,
                fontSize = 11.sp,
            )
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
                    Spacer(modifier = Modifier.height(20.dp))
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
                                @OptIn(ExperimentalMaterial3Api::class)
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        when (value) {
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                if (hasApiKey) onClassifySelected(setOf(nb.id))
                                                false
                                            }
                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                if (hasApiKey) onEmbedNotebooks(setOf(nb.id))
                                                false
                                            }
                                            SwipeToDismissBoxValue.Settled -> false
                                        }
                                    },
                                )

                                @OptIn(ExperimentalMaterial3Api::class)
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        val direction = dismissState.dismissDirection
                                        val bgColor = when (direction) {
                                            SwipeToDismissBoxValue.EndToStart -> Term.purple.copy(alpha = 0.15f)
                                            SwipeToDismissBoxValue.StartToEnd -> Term.green.copy(alpha = 0.15f)
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
                                                .clip(RoundedCornerShape(16.dp))
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
                                            isFavorite = nb.id in favorites,
                                            category = null,
                                            isSelected = nb.id in selectedIds,
                                            selectionMode = selectionMode,
                                            onClick = {
                                                if (selectionMode) selectedIds = selectedIds.toggle(nb.id)
                                                else onNotebookClick(nb)
                                            },
                                            onLongClick = { selectedIds = selectedIds.toggle(nb.id) },
                                            onToggleFavorite = { onToggleFavorite(nb.id) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(displayList) { _, nb ->
                        @OptIn(ExperimentalMaterial3Api::class)
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        if (hasApiKey) onClassifySelected(setOf(nb.id))
                                        false
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        if (hasApiKey) onEmbedNotebooks(setOf(nb.id))
                                        false
                                    }
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            },
                        )

                        @OptIn(ExperimentalMaterial3Api::class)
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val direction = dismissState.dismissDirection
                                val bgColor = when (direction) {
                                    SwipeToDismissBoxValue.EndToStart -> Term.purple.copy(alpha = 0.15f)
                                    SwipeToDismissBoxValue.StartToEnd -> Term.green.copy(alpha = 0.15f)
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
                                        .clip(RoundedCornerShape(16.dp))
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
                                    isFavorite = nb.id in favorites,
                                    category = categories[nb.id],
                                    isSelected = nb.id in selectedIds,
                                    selectionMode = selectionMode,
                                    onClick = {
                                        if (selectionMode) selectedIds = selectedIds.toggle(nb.id)
                                        else onNotebookClick(nb)
                                    },
                                    onLongClick = { selectedIds = selectedIds.toggle(nb.id) },
                                    onToggleFavorite = { onToggleFavorite(nb.id) },
                                )
                            },
                        )
                    }
                }
            }
        }

        // ── Embed all (thumb zone) ──
        if (semanticMode && hasApiKey && embeddingStatus == null && notebooks.isNotEmpty()) {
            Text(
                text = "Embed všechny sešity",
                color = Term.cyan,
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Term.surfaceLight)
                    .clickable { onEmbedNotebooks(null) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        // ── Search bar (thumb zone) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.bg)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Term.surfaceLight)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mode indicator
            Text(
                text = if (semanticMode) "🔮" else "🔍",
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        semanticMode = !semanticMode
                        if (!semanticMode) onClearSemantic()
                    }
                    .padding(4.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it; if (!semanticMode) onClearSemantic() },
                textStyle = TextStyle(
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                ),
                cursorBrush = SolidColor(if (semanticMode) Term.purple else Term.green),
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
                                text = if (semanticMode) "sémantické hledání..." else "hledat sešity...",
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
                Spacer(modifier = Modifier.width(4.dp))
            }
            // Sort
            Text(
                text = "↕",
                color = Term.orange,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCycleSort() }
                    .padding(4.dp),
            )
        }

        // ── Bottom action bar ──
        BottomActionBar(
            onCreateNotebook = { showCreateDialog = true },
            onRefresh = onRefresh,
            onSettings = onSettings,
            hasApiKey = hasApiKey,
            onStartDedup = { if (!dedup.running) onStartDedup() },
            onStartClassify = { if (!classify.running) onStartClassify() },
            onLogout = onLogout,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// KOMPONENTY
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActionPill(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontFamily = Term.font,
        fontSize = Term.fontSizeLg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun SearchModePill(
    text: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (active) activeColor else Term.textDim,
        fontFamily = Term.font,
        fontSize = Term.fontSize,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (active) Modifier
                    .background(activeColor.copy(alpha = 0.12f))
                    .border(1.dp, activeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
    val shape = RoundedCornerShape(16.dp)
    val bgColor = if (isSelected) Term.cyan.copy(alpha = 0.12f) else Term.surfaceLight
    val borderMod = if (isSelected) {
        Modifier.border(1.5.dp, Term.cyan.copy(alpha = 0.4f), shape)
    } else {
        Modifier.border(1.dp, Term.border.copy(alpha = 0.3f), shape)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(borderMod)
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
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
                Spacer(modifier = Modifier.height(10.dp))
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
        shape = RoundedCornerShape(20.dp),
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
            animation = tween(durationMillis = 1200, easing = LinearEasing),
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
            .clip(RoundedCornerShape(16.dp))
            .background(Term.surfaceLight)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Emoji placeholder
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
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
    onStartClassify: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Term.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomAction("＋", "Nový", Term.green) { onCreateNotebook() }
        BottomAction("⟳", "Sync", Term.cyan) { onRefresh() }
        BottomAction("⚙", "Nastavení", Term.orange) { onSettings() }
        Box {
            BottomAction("⋮", "Více", Term.textDim) { menuExpanded = true }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(Term.surface),
            ) {
                DropdownMenuItem(
                    text = { Text("Deduplikace zdrojů", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                    onClick = { menuExpanded = false; onStartDedup() },
                )
                DropdownMenuItem(
                    text = { Text("AI klasifikace", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                    onClick = { menuExpanded = false; onStartClassify() },
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Term.textDim,
            fontFamily = Term.font,
            fontSize = 11.sp,
        )
    }
}
