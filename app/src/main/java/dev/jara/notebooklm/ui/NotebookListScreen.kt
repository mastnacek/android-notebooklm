package dev.jara.notebooklm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
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
    onRenameNotebook: (String, String) -> Unit,
    facets: Map<String, NotebookFacets>,
    facetFilter: FacetFilter,
    onFacetFilterChange: (FacetFilter) -> Unit,
    sourceScan: SourceScanState,
    onScanSources: (Set<String>?) -> Unit,
    onDismissSourceScan: () -> Unit,
    indicators: Map<String, NotebookIndicators>,
    sourceGroups: Map<String, String>,
    onDedupSelected: (Set<String>) -> Unit,
    accountInfo: AccountInfo?,
    onArtifactsClick: (Notebook) -> Unit = {},
    miniPlayerTitle: String? = null,
    onMiniPlayerClick: () -> Unit = {},
    onMiniPlayerStop: () -> Unit = {},
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
    ) { _ ->
    // Selection mode — deklarace před Box aby byly dostupné i v overlays
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var renameTarget by remember { mutableStateOf<Notebook?>(null) }
    var deleteConfirmIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Rename notebook dialog
        if (renameTarget != null) {
            RenameNotebookDialog(
                currentTitle = renameTarget!!.title,
                onConfirm = { newTitle ->
                    onRenameNotebook(renameTarget!!.id, newTitle)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null },
            )
        }

        // Delete notebook confirmation (single or batch)
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

        // ── Header je overlay v Box dole ──
        LaunchedEffect(selectionMode) { if (selectionMode) barsVisible = true }

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
                        ActionPill("Nový", Term.yellow) { showCreateDialog = true }
                        ActionPill("⟳ Sync", Term.cyan) { onRefresh() }
                    }
                }
            }
        } else {
            // Seznam
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // Top 100dp = prostor pod overlay headerem, bottom 120dp = nad overlay bottom barem
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 100.dp, bottom = 120.dp),
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
                                    onArtifactsClick = { onArtifactsClick(nb) },
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
                                    onArtifactsClick = { onArtifactsClick(nb) },
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
                            onArtifactsClick = { onArtifactsClick(nb) },
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
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = "Filtr",
                        tint = if (totalSelected > 0) Term.cyan else Term.textDim,
                        modifier = Modifier.size(18.dp),
                    )
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
        // ── Header overlay — nepohybuje seznamem ──
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(tween(200, easing = FastOutSlowInEasing)) { -it },
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (selectionMode) {
                // ── Selection action bar (overlay místo headeru) ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Term.surfaceLight)
                        .statusBarsPadding(),
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
                            text = "Zrušit",
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
                        if (selectedIds.size == 1) {
                            ActionPill(
                                text = "Přejmenovat",
                                color = Term.orange,
                                onClick = {
                                    renameTarget = notebooks.find { it.id == selectedIds.first() }
                                    selectedIds = emptySet()
                                },
                            )
                        }
                        ActionPill(
                            text = if (selectedIds.size > 1) "Smazat ${selectedIds.size}" else "Smazat",
                            color = Term.red,
                            onClick = { deleteConfirmIds = selectedIds },
                        )
                    }
                }
            } else {
            Column(modifier = Modifier.background(Term.bg)) {
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

                    // Mini player nebo online indikátor
                    if (miniPlayerTitle != null) {
                        val miniShape = RoundedCornerShape(DS.chipRadius)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(miniShape)
                                .background(Term.green.copy(alpha = 0.12f))
                                .border(DS.borderWidth, Term.green.copy(alpha = DS.borderAlpha), miniShape)
                                .clickable { onMiniPlayerClick() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Headphones,
                                contentDescription = "Přehrávání",
                                tint = Term.green,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = miniPlayerTitle.take(15),
                                color = Term.green,
                                fontFamily = Term.font,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Zastavit",
                                tint = Term.textDim,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onMiniPlayerStop() },
                            )
                        }
                    } else {
                        Text(
                            text = "●",
                            color = Term.green,
                            fontSize = 10.sp,
                        )
                    }
                }
                if (accountInfo != null) {
                    val tierColor = when (accountInfo.tier) {
                        AccountTier.ULTRA -> Term.orange
                        AccountTier.PRO -> Term.cyan
                        AccountTier.PLUS -> Term.purple
                        AccountTier.FREE -> Term.textDim
                    }
                    val modelShort = accountInfo.modelName
                        .replace("GeminiPro", "Gemini ")
                        .replace("HighThinking", "· Thinking")
                        .replace("384K", "384K ")
                    Text(
                        text = "${accountInfo.tier.label} · $modelShort · ${accountInfo.contextLimit / 1000}K ctx",
                        color = tierColor,
                        fontFamily = Term.font,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                StatusLegend()
            }
            } // else (normal header)
        }

        // ── Bottom bar overlay — nepohybuje seznamem ──
        AnimatedVisibility(
            visible = barsVisible || WindowInsets.isImeVisible,
            enter = slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
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
                        .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(DS.searchRadius))
                        .background(if (semanticMode) Term.purple.copy(alpha = 0.06f) else Term.surfaceLight)
                        .border(DS.borderWidth, modeColor.copy(alpha = DS.borderAlpha), RoundedCornerShape(DS.searchRadius))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Mode toggle icon — tap to switch
                    Icon(
                        imageVector = if (semanticMode) Icons.Filled.AutoAwesome else Icons.Filled.Search,
                        contentDescription = if (semanticMode) "Sémantické hledání" else "Textové hledání",
                        tint = modeColor,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                semanticMode = !semanticMode
                                if (!semanticMode) onClearSemantic()
                            }
                            .padding(end = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))

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
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Vymazat hledání",
                            tint = Term.textDim,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { query = ""; onClearSemantic() }
                                .padding(2.dp),
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
        }
    } // Box
    } // Scaffold
}

/** Toggle element v Set */
private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item
