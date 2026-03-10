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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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

    LaunchedEffect(detail.chatMessages.size) {
        if (detail.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(detail.chatMessages.size - 1)
        }
    }

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary
            if (!detail.summary.isNullOrBlank() && detail.chatMessages.isEmpty()) {
                item {
                    SummaryCard(detail.summary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Zeptej se na cokoliv...",
                        color = Term.textDim,
                        fontFamily = Term.font,
                        fontSize = Term.fontSize,
                    )
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
                    SuggestionChip(text = suggestion) {
                        onSendChat(suggestion)
                    }
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
                DetailPill("Odeslat", Term.green) {
                    onSendChat(input.trim())
                    input = ""
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SummaryCard(summary: String) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Term.surface.copy(alpha = 0.7f))
            .border(1.dp, Term.cyan.copy(alpha = 0.3f), shape)
            .padding(16.dp),
    ) {
        Text(
            text = "📋 Souhrn",
            color = Term.cyan,
            fontFamily = Term.font,
            fontSize = Term.fontSizeLg,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MarkdownText(text = summary, color = Term.text)
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
                MicroAction(if (saved) "✓" else "📝", if (saved) Term.green else Term.textDim) {
                    if (!saved) { onSaveAsNote(msg.text); saved = true }
                }
            }

            var copied by remember { mutableStateOf(false) }
            MicroAction(if (copied) "✓" else "📋", if (copied) Term.green else Term.textDim) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("chat", msg.text))
                copied = true
            }
            IconMicroAction(Icons.Default.Share, Term.textDim) {
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
