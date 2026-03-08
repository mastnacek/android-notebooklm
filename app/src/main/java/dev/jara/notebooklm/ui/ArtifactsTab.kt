package dev.jara.notebooklm.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.jara.notebooklm.rpc.*

// ══════════════════════════════════════════════════════════════════════════════
// ARTIFACTS TAB
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ArtifactsTab(
    detail: DetailState,
    onPlayAudio: (String, String) -> Unit,
    onDownloadAudio: (Artifact) -> Unit,
    onGenerateArtifact: (GenerateType, GenerateOptions) -> Unit,
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                shape = RoundedCornerShape(DS.snackbarRadius),
            )
        }
    } // Box
}

@Composable
internal fun GenerateArtifactPanel(
    onGenerate: (GenerateType, GenerateOptions) -> Unit,
    onClose: () -> Unit,
) {
    var selectedType by remember { mutableStateOf<GenerateType?>(null) }

    // Options state
    var audioFormat by remember { mutableStateOf(AudioFormat.DEEP_DIVE) }
    var audioLength by remember { mutableStateOf(AudioLength.DEFAULT) }
    var videoFormat by remember { mutableStateOf(VideoFormat.EXPLAINER) }
    var videoStyle by remember { mutableStateOf(VideoStyle.AUTO) }
    var quizDifficulty by remember { mutableStateOf(QuizDifficulty.MEDIUM) }
    var quizQuantity by remember { mutableStateOf(QuizQuantity.STANDARD) }
    var infraOrientation by remember { mutableStateOf(InfographicOrientation.PORTRAIT) }
    var infraDetail by remember { mutableStateOf(InfographicDetail.STANDARD) }
    var slideFormat by remember { mutableStateOf(SlideDeckFormat.DETAILED) }
    var slideLength by remember { mutableStateOf(SlideDeckLength.DEFAULT) }
    var instructions by remember { mutableStateOf("") }

    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Term.surface)
            .border(DS.borderWidth, Term.purple.copy(alpha = DS.borderAlpha), shape)
            .padding(14.dp),
    ) {
        if (selectedType == null) {
            // Grid vyber typu
            Text("Generovat", color = Term.purple, fontFamily = Term.font,
                fontSize = Term.fontSizeLg, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            val types = GenerateType.entries
            for (row in types.chunked(4)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    for (type in row) {
                        val icon = generateTypeIcon(type)
                        val btnShape = RoundedCornerShape(DS.buttonRadius)
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
                GenerateType.AUDIO -> {
                    OptionRow("Formát") {
                        for (f in AudioFormat.entries) {
                            OptionChip(f.label, f == audioFormat) { audioFormat = f }
                        }
                    }
                    OptionRow("Délka") {
                        for (l in AudioLength.entries) {
                            OptionChip(l.label, l == audioLength) { audioLength = l }
                        }
                    }
                }
                GenerateType.VIDEO -> {
                    OptionRow("Formát") {
                        for (f in VideoFormat.entries) {
                            OptionChip(f.label, f == videoFormat) { videoFormat = f }
                        }
                    }
                    OptionRow("Styl") {
                        for (s in VideoStyle.entries) {
                            OptionChip(s.label, s == videoStyle) { videoStyle = s }
                        }
                    }
                }
                GenerateType.QUIZ -> {
                    OptionRow("Obtížnost") {
                        for (d in QuizDifficulty.entries) {
                            OptionChip(d.label, d == quizDifficulty) { quizDifficulty = d }
                        }
                    }
                    OptionRow("Množství") {
                        for (q in QuizQuantity.entries) {
                            OptionChip(q.label, q == quizQuantity) { quizQuantity = q }
                        }
                    }
                }
                GenerateType.INFOGRAPHIC -> {
                    OptionRow("Orientace") {
                        for (o in InfographicOrientation.entries) {
                            OptionChip(o.label, o == infraOrientation) { infraOrientation = o }
                        }
                    }
                    OptionRow("Detail") {
                        for (d in InfographicDetail.entries) {
                            OptionChip(d.label, d == infraDetail) { infraDetail = d }
                        }
                    }
                }
                GenerateType.SLIDE_DECK -> {
                    OptionRow("Formát") {
                        for (f in SlideDeckFormat.entries) {
                            OptionChip(f.label, f == slideFormat) { slideFormat = f }
                        }
                    }
                    OptionRow("Délka") {
                        for (l in SlideDeckLength.entries) {
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
                val opts = GenerateOptions(
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

internal fun generateTypeIcon(type: GenerateType): String = when (type) {
    GenerateType.AUDIO -> "🎧"
    GenerateType.VIDEO -> "🎥"
    GenerateType.QUIZ -> "❓"
    GenerateType.MIND_MAP -> "🧠"
    GenerateType.INFOGRAPHIC -> "🖼️"
    GenerateType.SLIDE_DECK -> "📊"
    GenerateType.DATA_TABLE -> "📋"
}

@Composable
internal fun OptionRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(label, color = Term.textDim, fontFamily = Term.font, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
internal fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val chipShape = RoundedCornerShape(DS.chipRadius)
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
                    .background(Term.green.copy(alpha = DS.selectionAlpha))
                    .border(DS.borderWidth, Term.green.copy(alpha = DS.borderAlpha), chipShape)
                else Modifier.background(Term.bg)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDismissArtifactCard(
    art: Artifact,
    onPlayAudio: (String, String) -> Unit,
    onDownloadAudio: (Artifact) -> Unit,
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
                        val job = launch {
                            delay(5000)
                            snackbarHostState.currentSnackbarData?.dismiss()
                        }
                        val result = snackbarHostState.showSnackbar(
                            message = "Smazat: ${art.title.take(25)}",
                            actionLabel = "Zpět",
                            duration = SnackbarDuration.Indefinite,
                        )
                        job.cancel()
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
internal fun ArtifactCard(
    art: Artifact,
    onPlayAudio: (String, String) -> Unit,
    onDownloadAudio: (Artifact) -> Unit,
    onOpenInteractiveHtml: (String) -> Unit,
    downloadState: DownloadState?,
) {
    val shape = RoundedCornerShape(14.dp)
    val statusColor = when (art.status) {
        ArtifactStatus.COMPLETED -> Term.green
        ArtifactStatus.PROCESSING -> Term.orange
        ArtifactStatus.PENDING -> Term.cyan
        ArtifactStatus.FAILED -> Term.red
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
            if (art.url != null && art.status == ArtifactStatus.COMPLETED) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (art.type == ArtifactType.AUDIO || art.type == ArtifactType.VIDEO) {
                        DetailPill("▶", Term.green) { onPlayAudio(art.url!!, art.title) }
                    }
                    if (art.type == ArtifactType.QUIZ) {
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
                                        ArtifactType.AUDIO -> "audio/*"
                                        ArtifactType.VIDEO -> "video/*"
                                        ArtifactType.SLIDE_DECK -> "application/pdf"
                                        ArtifactType.INFOGRAPHIC -> "image/*"
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
