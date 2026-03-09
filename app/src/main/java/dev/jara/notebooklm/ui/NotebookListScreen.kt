package dev.jara.notebooklm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jara.notebooklm.rpc.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookListScreen(
    notebooks: List<Notebook>,
    loading: Boolean,
    semanticResults: List<String>?,
    searchLoading: Boolean,
    embeddingStatus: String?,
    hasApiKey: Boolean,
    favorites: Set<String>,
    sortMode: NotebookSort,
    onNotebookClick: (Notebook) -> Unit,
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
    facets: Map<String, NotebookFacets>,
    facetFilter: FacetFilter,
    onFacetFilterChange: (FacetFilter) -> Unit,
    sourceScan: SourceScanState,
    onScanSources: (Set<String>?) -> Unit,
    onDismissSourceScan: () -> Unit,
    indicators: Map<String, NotebookIndicators>,
    sourceGroups: Map<String, String>,
    onDedupSelected: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Search state (sdílený mezi bottomBar a content)
    var query by remember { mutableStateOf("") }
    var semanticMode by remember { mutableStateOf(false) }

    // Filter sheet state
    var showFilterSheet by remember { mutableStateOf(false) }

    // ── Scroll-hide: scrolluje = skryj, zastaví na 1s = ukaž ──
    val listState = rememberLazyListState()
    var barsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    barsVisible = false
                } else {
                    kotlinx.coroutines.delay(1000L)
                    barsVisible = true
                }
            }
    }

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
            AnimatedVisibility(
                visible = barsVisible || WindowInsets.isImeVisible,
                enter = slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it },
            ) {
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
                        onScanSourcesAll = { onScanSources(null) },
                    )
                }
            }
            } // AnimatedVisibility
        },
    ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize()) {
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

        // ── Header (scroll-hide) ──
        LaunchedEffect(selectionMode) { if (selectionMode) barsVisible = true }

        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(tween(200, easing = FastOutSlowInEasing)) { -it },
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it },
        ) {
            Column {
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

                StatusLegend()
            }
        }

        // ── Selection action bar ──
        if (selectionMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Term.surfaceLight),
            ) {
                // Horní řádek: počet + vše/žádný + zavřít
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${selectedIds.size}×",
                        color = Term.cyan,
                        fontFamily = Term.font,
                        fontSize = Term.fontSizeLg,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ActionPill(
                        text = if (selectedIds.size == notebooks.size) "Žádný" else "Vše",
                        color = Term.cyan,
                        onClick = {
                            selectedIds = if (selectedIds.size == notebooks.size) emptySet()
                            else notebooks.map { it.id }.toSet()
                        },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ActionPill(
                        text = "✕",
                        color = Term.textDim,
                        onClick = { selectedIds = emptySet() },
                    )
                }
                // Spodní řádek: scrollovatelné batch akce
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
                        ActionPill(
                            text = "Zdroje",
                            color = Color(0xFF7AA2F7),
                            onClick = {
                                onScanSources(selectedIds)
                                selectedIds = emptySet()
                            },
                        )
                    }
                    ActionPill(
                        text = "Dedup",
                        color = Term.red,
                        onClick = {
                            onDedupSelected(selectedIds)
                            selectedIds = emptySet()
                        },
                    )
                    ActionPill(
                        text = if (selectedIds.size > 1) "🗑 ${selectedIds.size}" else "🗑",
                        color = Term.red,
                        onClick = { deleteConfirmIds = selectedIds },
                    )
                }
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

        // Facetové filtrování
        val facetFiltered = if (facetFilter.isEmpty) displayList
        else displayList.filter { nb ->
            val f = facets[nb.id] ?: return@filter true
            facetFilter.matches(f)
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
        if (sourceScan.running) {
            StatusBar(
                text = "Zdroje: ${sourceScan.progress} — ${sourceScan.currentNotebook}",
                color = Color(0xFF7AA2F7),
            )
        } else if (sourceScan.done) {
            StatusBar(
                text = "Sken zdrojů dokončen",
                color = Color(0xFF7AA2F7),
                dismissText = "OK",
                onDismiss = onDismissSourceScan,
            )
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
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sortMode == NotebookSort.CATEGORY) {
                    val grouped = facetFiltered
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
                                    indicators = indicators[nb.id] ?: NotebookIndicators(),
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
                } else if (sortMode == NotebookSort.SOURCES) {
                    val grouped = facetFiltered
                        .groupBy { sourceGroups[it.id] ?: "Bez sdílených zdrojů" }
                        .toSortedMap(compareBy { if (it == "Bez sdílených zdrojů") "zzz" else it.lowercase() })
                    for ((group, nbs) in grouped) {
                        item(key = "src_$group") {
                            Text(
                                text = group,
                                color = Color(0xFF7AA2F7),
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
                                    indicators = indicators[nb.id] ?: NotebookIndicators(),
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
                    itemsIndexed(facetFiltered) { _, nb ->
                        SwipeableNotebookItem(
                            nb = nb,
                            isFavorite = nb.id in favorites,
                            category = categories[nb.id],
                            isSelected = nb.id in selectedIds,
                            selectionMode = selectionMode,
                            indicators = indicators[nb.id] ?: NotebookIndicators(),
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

        // Edge tab — malý prvek na pravém okraji (spodní třetina)
        // Viditelný JEN když panel NENÍ otevřený a jsou facety k filtrování
        if (!showFilterSheet && facets.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = 80.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(Term.surface)
                    .border(
                        DS.borderWidth,
                        Term.border.copy(alpha = DS.borderAlpha),
                        RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                    )
                    .clickable { showFilterSheet = true }
                    .padding(vertical = 16.dp, horizontal = 6.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val totalSelected = facetFilter.topics.size + facetFilter.formats.size +
                        facetFilter.purposes.size + facetFilter.domains.size + facetFilter.freshnesses.size
                    Text("☷", color = if (totalSelected > 0) Term.cyan else Term.textDim, fontSize = 16.sp)
                    if (totalSelected > 0) {
                        Text(
                            "$totalSelected",
                            color = Term.cyan,
                            fontFamily = Term.font,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Scrim + Side panel
        AnimatedVisibility(
            visible = showFilterSheet,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showFilterSheet = false },
            )
        }

        AnimatedVisibility(
            visible = showFilterSheet,
            enter = slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it },
            exit = slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            FacetFilterPanel(
                facets = facets,
                currentFilter = facetFilter,
                onFilterChange = onFacetFilterChange,
                onDismiss = { showFilterSheet = false },
            )
        }
    } // Box
    } // Scaffold
}

/** Toggle element v Set */
private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item
