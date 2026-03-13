package dev.jara.notebooklm.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import dev.jara.notebooklm.rpc.*

@Composable
fun NotebookDetailScreen(
    notebook: Notebook,
    detail: DetailState,
    onBack: () -> Unit,
    onTabSwitch: (DetailTab) -> Unit,
    onSendChat: (String) -> Unit,
    onSaveAsNote: (String) -> Unit,
    onPlayAudio: (String, String) -> Unit,
    onStopAudio: () -> Unit,
    onDownloadAudio: (Artifact) -> Unit,
    onAddSource: (type: String, value: String, title: String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onDeleteSources: (Set<String>) -> Unit,
    onDedupSources: () -> Unit,
    onDismissDedup: () -> Unit,
    dedup: DeduplicationState,
    onGenerateArtifact: (GenerateType, GenerateOptions) -> Unit,
    onOpenQuiz: (String, String) -> Unit,
    onExportQuiz: (String, String) -> Unit,
    onDeleteArtifact: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    discovery: SourceDiscoveryState = SourceDiscoveryState(),
    onDiscoverSources: (String) -> Unit = {},
    onToggleDiscoverySource: (String) -> Unit = {},
    onImportDiscovered: () -> Unit = {},
    onDismissDiscovery: () -> Unit = {},
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
        // ── Header s marquee efektem — scroll doprava, pauza, navrat, tri tecky ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val fullTitle = if (notebook.emoji.isNotEmpty()) "${notebook.emoji} ${notebook.title}" else notebook.title
            var marqueeTriger by remember { mutableIntStateOf(0) }
            val scrollState = rememberScrollState()
            var isOverflowing by remember { mutableStateOf(false) }
            var animationDone by remember { mutableStateOf(false) }

            // Marquee animace — projede nazev, pauza, navrat na zacatek
            LaunchedEffect(fullTitle, marqueeTriger) {
                animationDone = false
                scrollState.scrollTo(0)
                // Pockej nez se layout spocita maxValue
                delay(100)
                isOverflowing = scrollState.maxValue > 0
                if (isOverflowing) {
                    delay(300) // kratka pauza pred startem
                    scrollState.animateScrollTo(
                        scrollState.maxValue,
                        animationSpec = tween(
                            durationMillis = (fullTitle.length * 50).coerceIn(800, 4000),
                            easing = LinearEasing,
                        ),
                    )
                    delay(600) // pauza na konci
                    scrollState.animateScrollTo(
                        0,
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                animationDone = true
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = fullTitle,
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Term.white.copy(alpha = 0.5f),
                            blurRadius = 10f,
                        ),
                    ),
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .clickable { marqueeTriger++ },
                )
                // Fade + "..." indikator pokud nazev preteka a animace dobehla
                if (isOverflowing && animationDone) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .matchParentSize()
                            .wrapContentHeight(Alignment.CenterVertically)
                            .wrapContentWidth(Alignment.End),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(Term.fontSizeLg.value.dp + 4.dp)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, Term.surface),
                                        ),
                                    ),
                            )
                            Box(
                                modifier = Modifier
                                    .background(Term.surface)
                            ) {
                                Text(
                                    text = "…",
                                    color = Term.textDim,
                                    fontFamily = Term.font,
                                    fontSize = Term.fontSizeLg,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
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
            val shimmerColors = listOf(
                Term.surfaceLight.copy(alpha = 0.3f),
                Term.surfaceLight.copy(alpha = 0.6f),
                Term.surfaceLight.copy(alpha = 0.3f),
            )
            val transition = rememberInfiniteTransition(label = "detail_shimmer")
            val translateAnim by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = DS.shimmerDurationMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "detail_shimmer_translate",
            )
            val brush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = shimmerColors,
                start = androidx.compose.ui.geometry.Offset(translateAnim - 200f, 0f),
                end = androidx.compose.ui.geometry.Offset(translateAnim, 0f),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (it == 0) 100.dp else 60.dp)
                            .clip(RoundedCornerShape(DS.cardRadius))
                            .background(brush),
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
                    discovery, onDiscoverSources, onToggleDiscoverySource,
                    onImportDiscovered, onDismissDiscovery,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                DetailTab.ARTIFACTS -> ArtifactsTab(
                    detail, onPlayAudio, onDownloadAudio, onGenerateArtifact,
                    onOpenQuiz, onExportQuiz, onDeleteArtifact, detail.downloads,
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
                val (icon, label, color) = when (tab) {
                    DetailTab.CHAT -> Triple(Icons.Filled.ChatBubble, "Chat", Term.green)
                    DetailTab.SOURCES -> Triple(Icons.AutoMirrored.Filled.LibraryBooks, "${detail.sources.size}", Term.cyan)
                    DetailTab.ARTIFACTS -> Triple(Icons.Filled.AutoAwesome, "${detail.artifacts.size}", Term.purple)
                    DetailTab.NOTES -> Triple(Icons.AutoMirrored.Filled.StickyNote2, "${detail.notes.size}", Term.orange)
                }
                val shape = RoundedCornerShape(DS.buttonRadius)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(shape)
                        .then(
                            if (selected) Modifier
                                .background(color.copy(alpha = DS.selectionAlpha))
                                .border(DS.borderWidth, color.copy(alpha = DS.borderAlpha), shape)
                            else Modifier
                        )
                        .clickable { onTabSwitch(tab) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.name,
                        tint = if (selected) color else Term.textDim,
                        modifier = Modifier.size(16.dp),
                    )
                    if (label.isNotEmpty() && tab != DetailTab.CHAT) {
                        Text(
                            text = " $label",
                            color = if (selected) color else Term.textDim,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
