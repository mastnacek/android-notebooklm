package dev.jara.notebooklm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import dev.jara.notebooklm.rpc.*

// ══════════════════════════════════════════════════════════════════════════════
// CHAT TAB
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ChatTab(
    detail: DetailState,
    onSendChat: (String) -> Unit,
    onSaveAsNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var previewText by remember { mutableStateOf<String?>(null) }

    // Scroll na konec — summary item je navíc (+1 pokud existuje)
    val hasSummary = !detail.summary.isNullOrBlank()
    LaunchedEffect(detail.chatMessages.size) {
        if (detail.chatMessages.isNotEmpty()) {
            val lastIndex = detail.chatMessages.size - 1 + if (hasSummary) 1 else 0
            listState.animateScrollToItem(lastIndex)
        }
    }

    Box(modifier = modifier) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary — vždy nahoře, sbalená
            if (!detail.summary.isNullOrBlank()) {
                item {
                    SummaryCard(detail.summary)
                    if (detail.chatMessages.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Zeptej se na cokoliv...",
                            color = Term.textDim,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                        )
                    }
                }
            }

            items(detail.chatMessages) { msg ->
                ChatBubble(msg, onSaveAsNote = onSaveAsNote)
            }

            if (detail.chatAnswering) {
                item {
                    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
                    val cursorAlpha by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "cursor_blink",
                    )
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Přemýšlím",
                            color = Term.orange,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                        )
                        Text(
                            text = " │",
                            color = Term.green.copy(alpha = cursorAlpha),
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Prompt suggestion chips — zobraz vždy když jsou k dispozici (ne během odpovídání)
        if (detail.promptSuggestions.isNotEmpty() && !detail.chatAnswering) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (suggestion in detail.promptSuggestions) {
                    SuggestionChip(
                        text = suggestion,
                        onClick = { onSendChat(suggestion) },
                        onPreview = { showing -> previewText = if (showing) suggestion else null },
                    )
                }
            }
        }

        // Input card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(DS.searchRadius))
                .background(Term.surfaceLight)
                .border(DS.borderWidth, Term.green.copy(alpha = DS.borderAlpha), RoundedCornerShape(DS.searchRadius))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                ),
                cursorBrush = SolidColor(Term.green),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (input.isNotBlank() && !detail.chatAnswering) {
                            onSendChat(input.trim())
                            input = ""
                        }
                    },
                ),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                text = "Zeptej se...",
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
            if (input.isNotBlank() && !detail.chatAnswering) {
                Spacer(modifier = Modifier.width(8.dp))
                DetailPill("Odeslat", Term.yellow) {
                    onSendChat(input.trim())
                    input = ""
                }
            }
        }
    } // Column

    // Overlay pro náhled otázky — uprostřed obrazovky, teal glassmorphism
    if (previewText != null) {
        val overlayBg = Color(0xFF316263).copy(alpha = 0.85f)       // Transformative Teal
        val overlayBorder = Color(0xFF00F5D4).copy(alpha = 0.4f)    // Plasma Teal
        val overlayText = Color(0xFFE9EEF5)                         // světle šedomodrá
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(overlayBg)
                    .border(1.dp, overlayBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = previewText ?: "",
                    color = overlayText,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    lineHeight = Term.lineHeightRead,
                )
            }
        }
    }
    } // Box
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit, onPreview: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(DS.chipRadius)
    Text(
        text = text,
        color = Term.cyan,
        fontFamily = Term.font,
        fontSize = Term.fontSize,
        maxLines = 2,
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(shape)
            .border(1.dp, Term.cyan.copy(alpha = DS.borderAlpha), shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onPreview(true) },
                    onPress = {
                        tryAwaitRelease()
                        onPreview(false)
                    },
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SummaryCard(summary: String) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Term.surface.copy(alpha = 0.7f))
            .border(1.dp, Term.cyan.copy(alpha = 0.3f), shape)
            .clickable { expanded = !expanded }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Sbalit" else "Rozbalit",
                tint = Term.textDim,
                modifier = Modifier.size(18.dp).padding(end = 4.dp),
            )
            Icon(
                imageVector = Icons.Filled.Summarize,
                contentDescription = null,
                tint = Term.cyan,
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
            )
            Text(
                text = "Souhrn",
                color = Term.cyan,
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownText(text = summary, color = Term.text)
        }
    }
}

@Composable
internal fun ChatBubble(
    msg: ChatMessage,
    onSaveAsNote: (String) -> Unit,
) {
    val isUser = msg.role == ChatRole.USER
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Akce — ikonky
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (isUser) "Ty" else "NLM",
                color = if (isUser) Term.cyan else Term.green,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(6.dp))

            if (!isUser) {
                var saved by remember { mutableStateOf(false) }
                IconMicroAction(
                    if (saved) Icons.Filled.Check else Icons.AutoMirrored.Outlined.NoteAdd,
                    if (saved) Term.green else Term.textDim,
                    "Uložit jako poznámku",
                ) {
                    if (!saved) { onSaveAsNote(msg.text); saved = true }
                }
            }

            var copied by remember { mutableStateOf(false) }
            IconMicroAction(
                if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                if (copied) Term.green else Term.textDim,
                "Kopírovat",
            ) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("chat", msg.text))
                copied = true
            }
            IconMicroAction(Icons.Default.Share, Term.textDim, "Sdílet") {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, msg.text)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Bublina
        val bubbleBg = if (isUser) Term.surfaceLight else Term.surface.copy(alpha = 0.7f)
        val bubbleBorder = if (isUser) Term.cyan.copy(alpha = DS.borderAlpha) else Term.green.copy(alpha = DS.borderAlpha)
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 0.92f)
                .clip(shape)
                .background(bubbleBg)
                .border(1.dp, bubbleBorder, shape)
                .padding(14.dp),
        ) {
            if (isUser) {
                Text(
                    text = msg.text,
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeRead,
                    lineHeight = Term.lineHeightRead,
                )
            } else {
                MarkdownText(text = msg.text, color = Term.text)
            }
        }
    }
}
