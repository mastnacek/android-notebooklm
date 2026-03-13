package dev.jara.notebooklm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jara.notebooklm.rpc.AnswerOption
import dev.jara.notebooklm.rpc.QuizQuestion
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlin.random.Random

// ══════════════════════════════════════════════════════════════════════════════
// QUIZ SCREEN — interaktivni kviz s michanim odpovedi
// ══════════════════════════════════════════════════════════════════════════════

private data class ShuffledQuestion(
    val original: QuizQuestion,
    val options: List<AnswerOption>,
    val correctIndex: Int,
)

@Composable
fun QuizScreen(
    questions: List<QuizQuestion>,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Zamichane otazky — deterministicky seed pro preziti rotace
    val prepared = remember(questions) {
        questions.mapIndexed { i, q ->
            val shuffled = q.options.shuffled(Random(i.toLong() + title.hashCode()))
            ShuffledQuestion(q, shuffled, shuffled.indexOfFirst { it.isCorrect })
        }
    }

    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(-1) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var hintRevealed by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    if (finished || currentIndex >= prepared.size) {
        QuizResultScreen(
            score = score,
            total = prepared.size,
            title = title,
            onRestart = {
                currentIndex = 0; score = 0; selectedIndex = -1; finished = false
            },
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val q = prepared[currentIndex]
    val answered = selectedIndex >= 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Term.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp),
    ) {
        // ── Header: progress + zpet ──
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zpět",
                tint = Term.textDim,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(DS.microRadius))
                    .clickable { onBack() }
                    .padding(2.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${currentIndex + 1}/${prepared.size}",
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / prepared.size },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Term.cyan,
            trackColor = Term.border,
        )

        // ── Otazka — horni cast ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = highlightMarkers(q.original.question, Term.cyan),
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeXl,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                // Hint — tlacitko pred odpovedi, automaticky po odpovedi
                if (q.original.hint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (hintRevealed || answered) {
                        val hintShape = RoundedCornerShape(DS.cardRadius)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(hintShape)
                                .background(Term.yellow.copy(alpha = 0.08f))
                                .border(DS.borderWidth, Term.yellow.copy(alpha = DS.borderAlpha), hintShape)
                                .padding(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lightbulb,
                                contentDescription = "Nápověda",
                                tint = Term.yellow,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = highlightMarkers(q.original.hint, Term.yellow),
                                color = Term.yellow,
                                fontFamily = Term.font,
                                fontSize = Term.fontSize,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        val hintBtnShape = RoundedCornerShape(DS.buttonRadius)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(hintBtnShape)
                                .border(DS.borderWidth, Term.yellow.copy(alpha = DS.borderAlpha), hintBtnShape)
                                .clickable { hintRevealed = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lightbulb,
                                contentDescription = null,
                                tint = Term.yellow,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nápověda",
                                color = Term.yellow,
                                fontFamily = Term.font,
                                fontSize = Term.fontSize,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // ── Odpovedi — dole (thumb area), scrollovatelne po odpovedi kvuli rationale ──
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(bottom = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            q.options.take(4).forEachIndexed { i, option ->
                QuizAnswerButton(
                    text = option.text,
                    state = when {
                        !answered -> AnswerState.DEFAULT
                        i == q.correctIndex -> AnswerState.CORRECT
                        i == selectedIndex -> AnswerState.WRONG
                        else -> AnswerState.NEUTRAL
                    },
                    rationale = option.rationale,
                    onClick = {
                        if (!answered) {
                            selectedIndex = i
                            if (i == q.correctIndex) {
                                score++
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                )
            }

            // Tlacitko Dalsi — po odpovedi
            if (answered) {
                Spacer(modifier = Modifier.height(4.dp))
                val isLast = currentIndex >= prepared.size - 1
                val nextShape = RoundedCornerShape(DS.buttonRadius)
                Text(
                    text = if (isLast) "Zobrazit výsledky" else "Další otázka →",
                    color = Term.cyan,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(nextShape)
                        .background(Term.cyan.copy(alpha = DS.selectionAlpha))
                        .clickable {
                            if (isLast) {
                                finished = true
                            } else {
                                currentIndex++
                                selectedIndex = -1
                                hintRevealed = false
                            }
                        }
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}

// ── Answer Button ──

private enum class AnswerState { DEFAULT, CORRECT, WRONG, NEUTRAL }

@Composable
private fun QuizAnswerButton(
    text: String,
    state: AnswerState,
    rationale: String = "",
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        when (state) {
            AnswerState.DEFAULT -> Term.surface
            AnswerState.CORRECT -> Term.green.copy(alpha = 0.15f)
            AnswerState.WRONG -> Term.red.copy(alpha = 0.15f)
            AnswerState.NEUTRAL -> Term.surface
        },
        animationSpec = tween(300),
        label = "answerBg",
    )
    val borderColor by animateColorAsState(
        when (state) {
            AnswerState.DEFAULT -> Term.border
            AnswerState.CORRECT -> Term.green
            AnswerState.WRONG -> Term.red
            AnswerState.NEUTRAL -> Term.border.copy(alpha = 0.3f)
        },
        animationSpec = tween(300),
        label = "answerBorder",
    )
    val textColor by animateColorAsState(
        when (state) {
            AnswerState.DEFAULT -> Term.text
            AnswerState.CORRECT -> Term.green
            AnswerState.WRONG -> Term.red
            AnswerState.NEUTRAL -> Term.disabled
        },
        animationSpec = tween(300),
        label = "answerText",
    )
    val icon = when (state) {
        AnswerState.CORRECT -> "✓ "
        AnswerState.WRONG -> "✗ "
        else -> ""
    }

    val shape = RoundedCornerShape(DS.buttonRadius)
    // Highlight barva pro $...$ markery — svetlejsi verze hlavni barvy textu
    val highlightColor = when (state) {
        AnswerState.DEFAULT -> Term.cyan
        AnswerState.CORRECT -> Term.green
        AnswerState.WRONG -> Term.red
        AnswerState.NEUTRAL -> Term.disabled
    }
    Column {
        Text(
            text = buildAnnotatedString {
                append(icon)
                append(highlightMarkers(text, highlightColor))
            },
            color = textColor,
            fontFamily = Term.font,
            fontSize = Term.fontSizeLg,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor)
                .border(DS.borderWidth, borderColor, shape)
                .clickable(enabled = state == AnswerState.DEFAULT, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
        // Rationale — zobraz po odpovedi u vsech variant
        if (rationale.isNotBlank() && state != AnswerState.DEFAULT) {
            val rationaleColor = when (state) {
                AnswerState.CORRECT -> Term.green
                AnswerState.WRONG -> Term.red
                else -> Term.textDim
            }
            Text(
                text = highlightMarkers(rationale, rationaleColor.copy(alpha = 0.7f)),
                color = rationaleColor.copy(alpha = 0.7f),
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontStyle = FontStyle.Italic,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

// ── Result Screen ──

@Composable
private fun QuizResultScreen(
    score: Int,
    total: Int,
    title: String,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = if (total > 0) (score * 100) / total else 0
    val resultColor = when {
        pct >= 80 -> Term.green
        pct >= 50 -> Term.yellow
        else -> Term.red
    }
    val resultText = when {
        pct >= 80 -> "Výborně!"
        pct >= 50 -> "Dobře!"
        else -> "Zkus znovu"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Term.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = Term.textDim,
            fontFamily = Term.font,
            fontSize = Term.fontSize,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "$score / $total",
            color = resultColor,
            fontFamily = Term.font,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$pct%  ·  $resultText",
            color = resultColor,
            fontFamily = Term.font,
            fontSize = Term.fontSizeLg,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailPill("Opakovat", Term.cyan) { onRestart() }
            DetailPill("Zpět", Term.textDim) { onBack() }
        }
    }
}

// ── Syntax highlighting pro $...$ markery z NotebookLM ──

/** Parsuje $...$ markery a vraci AnnotatedString se zvyraznenym textem (italic + barva) */
private fun highlightMarkers(text: String, highlightColor: Color): AnnotatedString {
    val regex = Regex("""\$([^$]+)\$""")
    return buildAnnotatedString {
        var lastIndex = 0
        for (match in regex.findAll(text)) {
            // Text pred markerem
            append(text.substring(lastIndex, match.range.first))
            // Zvyrazneny text
            withStyle(SpanStyle(color = highlightColor, fontStyle = FontStyle.Italic)) {
                append(match.groupValues[1])
            }
            lastIndex = match.range.last + 1
        }
        // Zbytek textu
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
