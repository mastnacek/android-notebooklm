package dev.jara.notebooklm.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            .windowInsetsPadding(WindowInsets.systemBars)
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
        var menuExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surface)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NotebookLM",
                color = Term.green,
                fontFamily = Term.font,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // Razeni — pill button
            Text(
                text = "↕ ${sortMode.label}",
                color = Term.orange,
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Term.surfaceLight)
                    .clickable { onCycleSort() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Novy notebook — pill
            Text(
                text = "＋",
                color = Term.green,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Term.surfaceLight)
                    .clickable { showCreateDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Sync — pill button
            Text(
                text = "⟳",
                color = Term.cyan,
                fontSize = 20.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Term.surfaceLight)
                    .clickable { onRefresh() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Overflow menu
            Box {
                Text(
                    text = "⋮",
                    color = Term.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { menuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Term.surface),
                ) {
                    DropdownMenuItem(
                        text = { Text("Nastavení", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                        onClick = { menuExpanded = false; onSettings() },
                    )
                    DropdownMenuItem(
                        text = { Text("Deduplikace zdrojů", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                        onClick = { menuExpanded = false; if (!dedup.running) onStartDedup() },
                    )
                    DropdownMenuItem(
                        text = { Text("AI klasifikace", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                        onClick = { menuExpanded = false; if (!classify.running) onStartClassify() },
                    )
                    DropdownMenuItem(
                        text = { Text("Odhlásit", color = Term.red, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                        onClick = { menuExpanded = false; onLogout() },
                    )
                }
            }
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

        // Mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SearchModePill("fulltext", !semanticMode, Term.green) {
                if (semanticMode) { semanticMode = false; onClearSemantic() }
            }
            SearchModePill("semantic", semanticMode, Term.purple) {
                if (!semanticMode) semanticMode = true
            }
        }

        // Search input — zaobleny
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Term.surfaceLight)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (semanticMode) "? " else "/ ",
                color = if (semanticMode) Term.purple else Term.green,
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
                fontWeight = FontWeight.Bold,
            )
            BasicTextField(
                value = query,
                onValueChange = { query = it; if (!semanticMode) onClearSemantic() },
                textStyle = TextStyle(
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
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
                                text = if (semanticMode) "semantické hledání..." else "filtr sešitů...",
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
            if (query.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = Term.textDim,
                    fontSize = Term.fontSizeLg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { query = ""; onClearSemantic() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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

        // Embed all button
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

        // ── Obsah ──
        if (loading || searchLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchLoading) "Hledám..." else "Načítám sešity...",
                    color = Term.orange,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                )
            }
        } else if (notebooks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Žádné sešity",
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                )
            }
        } else {
            // Pocet
            val countText = when {
                semanticMode && semanticResults != null -> "${displayList.size} výsledků (semantic)"
                query.isNotBlank() && !semanticMode -> "${displayList.size}/${notebooks.size} sešitů"
                else -> "${notebooks.size} sešitů"
            }
            Text(
                text = countText,
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            // Seznam
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            }
                        }
                    }
                } else {
                    itemsIndexed(displayList) { _, nb ->
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
                    }
                }
            }
        }
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
    val bgColor = if (isSelected) Term.cyan.copy(alpha = 0.12f) else Term.surface
    val borderMod = if (isSelected) {
        Modifier.border(1.5.dp, Term.cyan.copy(alpha = 0.4f), shape)
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(borderMod)
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
