package dev.jara.notebooklm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jara.notebooklm.rpc.*

// ══════════════════════════════════════════════════════════════════════════════
// NOTES TAB
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesTab(
    detail: DetailState,
    onDeleteNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (detail.notes.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                    contentDescription = null,
                    tint = Term.orange,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Zatím žádné poznámky",
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ulož si odpovědi z chatu jako poznámky",
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(detail.notes, key = { it.id }) { note ->
                SwipeToDismissNoteCard(note, onDeleteNote)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDismissNoteCard(
    note: Note,
    onDeleteNote: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { false }  // nikdy auto-dismiss
    )

    // Detekce překročení prahu swipe — stejný pattern jako NotebookListComponents
    LaunchedEffect(dismissState) {
        var triggered = false
        snapshotFlow { dismissState.progress to dismissState.targetValue }.collect { (progress, target) ->
            val dragProgress = if (target != SwipeToDismissBoxValue.Settled) progress else 0f
            if (dragProgress >= 0.4f && !triggered) {
                triggered = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (target == SwipeToDismissBoxValue.Settled && triggered) {
                triggered = false
                confirmDelete = true
            }
            if (dragProgress < 0.3f) {
                triggered = false
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                DetailPill("Smazat", Term.red) {
                    onDeleteNote(note.id)
                    confirmDelete = false
                }
            },
            dismissButton = {
                DetailPill("Zrušit", Term.textDim) { confirmDelete = false }
            },
            title = {
                Text("Smazat poznámku?", color = Term.white, fontFamily = Term.font,
                    fontSize = Term.fontSizeLg, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(note.title.ifBlank { note.content.take(60) }, color = Term.text,
                    fontFamily = Term.font, fontSize = Term.fontSize)
            },
            containerColor = Term.surface,
            shape = RoundedCornerShape(DS.dialogRadius),
        )
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
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Smazat",
                    tint = Term.white,
                    modifier = Modifier.size(22.dp).scale(scale),
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        NoteCard(note)
    }
}

@Composable
internal fun NoteCard(note: Note) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val context = LocalContext.current

    val displayTitle = note.title.ifBlank {
        note.content.lineSequence()
            .map { it.trimStart('#', ' ', '*', '-') }
            .firstOrNull { it.isNotBlank() }
            ?.take(60) ?: "Poznámka"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Term.surface)
            .clickable { expanded = !expanded }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Sbalit" else "Rozbalit",
                tint = Term.textDim,
                modifier = Modifier.size(18.dp).padding(end = 2.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = displayTitle,
                color = Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconMicroAction(Icons.Outlined.ContentCopy, Term.textDim, "Kopírovat") {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("note", note.content))
            }
            IconMicroAction(Icons.Default.Share, Term.textDim, "Sdílet") {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, note.content)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }
        }
        if (expanded && note.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownText(text = note.content, color = Term.text)
        }
    }
}
