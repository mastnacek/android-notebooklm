package dev.jara.notebooklm.ui

// Extrahované komponenty z NotebookListScreen — čistý přesun bez změny logiky

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jara.notebooklm.rpc.Notebook
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
// KOMPONENTY
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ActionPill(text: String, color: Color, onClick: () -> Unit) {
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
internal fun StatusBar(
    text: String,
    color: Color,
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

/** 4 indikátorové tečky stavu notebooku */
@Composable
private fun StatusDots(indicators: NotebookIndicators, modifier: Modifier = Modifier) {
    val dots = listOf(
        indicators.scanned to Color(0xFF7AA2F7),  // modrá (pastelová)
        indicators.embedded to Color(0xFF9ECE6A),  // zelená
        indicators.classified to Color(0xFFE0AF68), // žlutá
        indicators.deduped to Term.red,              // červená
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((active, color) in dots) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .then(
                        if (active) Modifier
                            .shadow(4.dp, CircleShape, ambientColor = color, spotColor = color)
                            .background(color, CircleShape)
                        else Modifier
                            .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotebookCard(
    nb: Notebook,
    isFavorite: Boolean,
    category: String?,
    indicators: NotebookIndicators,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(borderMod)
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
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
                if (nb.sourceTypes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sourceTypesSummary(nb.sourceTypes, nb.sourceCount),
                        color = Term.textDim,
                        fontSize = 11.sp,
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

        // StatusDots — pravý horní roh karty
        StatusDots(indicators, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))

        // Spodní řádek — relativní čas vlevo, sdílení vpravo
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (nb.modifiedAt > 0) {
                Text(
                    text = relativeTime(nb.modifiedAt),
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = 10.sp,
                )
            }
            if (nb.isShared) {
                Text(
                    text = "👥",
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableNotebookItem(
    nb: Notebook,
    isFavorite: Boolean,
    category: String?,
    indicators: NotebookIndicators,
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
                indicators = indicators,
                isSelected = isSelected,
                selectionMode = selectionMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onToggleFavorite = onToggleFavorite,
            )
        },
    )
}


@Composable
internal fun CreateNotebookDialog(
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
                BasicTextField(
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
                BasicTextField(
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

/** Souhrn typu zdroju — "📄3 🌐5 🎥1" */
private fun sourceTypesSummary(types: Map<dev.jara.notebooklm.rpc.SourceType, Int>, total: Int): String {
    if (types.isEmpty()) return "$total"
    return types.entries
        .sortedByDescending { it.value }
        .joinToString(" ") { "${it.key.icon}${it.value}" }
}

/** Relativni cas — "pred 2h", "pred 3d", "5. 3." */
private fun relativeTime(epochSeconds: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - epochSeconds
    return when {
        diff < 60 -> "právě teď"
        diff < 3600 -> "před ${diff / 60}m"
        diff < 86400 -> "před ${diff / 3600}h"
        diff < 604800 -> "před ${diff / 86400}d"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochSeconds * 1000 }
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)}. ${cal.get(java.util.Calendar.MONTH) + 1}."
        }
    }
}

/** Fuzzy match — kazdy znak query se musi vyskytovat v haystack v poradi */
internal fun fuzzyMatch(haystack: String, query: String): Boolean {
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
internal fun ShimmerCard() {
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
internal fun SkeletonList(modifier: Modifier = Modifier) {
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
internal fun BottomActionBar(
    onCreateNotebook: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    hasApiKey: Boolean,
    onStartDedup: () -> Unit,
    onCycleSort: () -> Unit,
    sortLabel: String,
    onEmbedAll: () -> Unit,
    onLogout: () -> Unit,
    onScanSourcesAll: () -> Unit,
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
                    text = { Text("Skenuj zdroje všech", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
                    onClick = { menuExpanded = false; onScanSourcesAll() },
                )
                DropdownMenuItem(
                    text = { Text("Dedup", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
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
internal fun BottomAction(
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

// ══════════════════════════════════════════════════════════════════════════════
// STATUS LEGEND
// ══════════════════════════════════════════════════════════════════════════════

/** Legenda indikátorů pod nadpisem */
@Composable
internal fun StatusLegend() {
    val items = listOf(
        "Zdroje" to Color(0xFF7AA2F7),
        "Embed" to Color(0xFF9ECE6A),
        "AI kat." to Color(0xFFE0AF68),
        "Dedup" to Term.red,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        for ((label, color) in items) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .shadow(3.dp, CircleShape, ambientColor = color, spotColor = color)
                        .background(color, CircleShape)
                )
                Text(
                    text = label,
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
