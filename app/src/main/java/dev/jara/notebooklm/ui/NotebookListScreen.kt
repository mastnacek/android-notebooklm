package dev.jara.notebooklm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.jara.notebooklm.rpc.NotebookLmApi

@Composable
fun NotebookListScreen(
    notebooks: List<NotebookLmApi.Notebook>,
    loading: Boolean,
    semanticResults: List<String>?,
    searchLoading: Boolean,
    embeddingStatus: String?,
    hasApiKey: Boolean,
    onNotebookClick: (NotebookLmApi.Notebook) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onSemanticSearch: (String) -> Unit,
    onClearSemantic: () -> Unit,
    onEmbedNotebooks: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Term.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surface)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "notebooklm",
                color = Term.green,
                fontFamily = Term.font,
                fontSize = Term.fontSizeXl,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "[cfg]",
                color = Term.purple,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier
                    .clickable { onSettings() }
                    .padding(horizontal = 6.dp),
            )
            Text(
                text = "[sync]",
                color = Term.cyan,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier
                    .clickable { onRefresh() }
                    .padding(horizontal = 6.dp),
            )
            Text(
                text = "[exit]",
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier
                    .clickable { onLogout() }
                    .padding(start = 4.dp),
            )
        }

        // Search bar s mode toggle
        var query by remember { mutableStateOf("") }
        var semanticMode by remember { mutableStateOf(false) }

        val fulltextFiltered = remember(notebooks, query, semanticMode) {
            if (semanticMode || query.isBlank()) notebooks
            else {
                val q = query.lowercase()
                notebooks.filter { nb ->
                    nb.title.lowercase().contains(q) ||
                    nb.emoji.lowercase().contains(q)
                }
            }
        }

        // Urceni zobrazeneho seznamu
        val displayList = if (semanticMode && semanticResults != null) {
            val order = semanticResults.withIndex().associate { (i, id) -> id to i }
            notebooks.filter { it.id in order }.sortedBy { order[it.id] ?: Int.MAX_VALUE }
        } else {
            fulltextFiltered
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surfaceLight)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mode toggle: / = fulltext, ? = semantic
            Text(
                text = if (semanticMode) "? " else "/ ",
                color = if (semanticMode) Term.purple else Term.green,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    semanticMode = !semanticMode
                    if (!semanticMode) onClearSemantic()
                },
            )
            BasicTextField(
                value = query,
                onValueChange = { newQuery ->
                    query = newQuery
                    if (!semanticMode) onClearSemantic()
                },
                textStyle = TextStyle(
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                ),
                cursorBrush = SolidColor(if (semanticMode) Term.purple else Term.green),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (semanticMode) ImeAction.Search else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (semanticMode && query.isNotBlank()) {
                            onSemanticSearch(query)
                        }
                    },
                    onDone = {},
                ),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = if (semanticMode) "semanticke hledani..." else "filtr sesitu...",
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
            if (query.isNotEmpty()) {
                Text(
                    text = "[x]",
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                    modifier = Modifier
                        .clickable {
                            query = ""
                            onClearSemantic()
                        }
                        .padding(start = 8.dp),
                )
            }
        }

        // Embedding status
        if (embeddingStatus != null) {
            Text(
                text = "  > $embeddingStatus",
                color = Term.orange,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Embed button (pokud je API klic a jsme v semantic mode)
        if (semanticMode && hasApiKey && embeddingStatus == null && notebooks.isNotEmpty()) {
            Text(
                text = "  [embed notebooky]",
                color = Term.cyan,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier
                    .clickable { onEmbedNotebooks() }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Stav
        if (loading || searchLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchLoading) "> hledam..." else "> nacitam sesity...",
                    color = Term.orange,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                )
            }
        } else if (notebooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "> zadne sesity",
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                )
            }
        } else {
            // Pocet
            val countText = when {
                semanticMode && semanticResults != null ->
                    "  ${displayList.size} vysledku (semantic)"
                query.isNotBlank() && !semanticMode ->
                    "  ${displayList.size}/${notebooks.size} sesitu"
                else -> "  ${notebooks.size} sesitu"
            }
            Text(
                text = countText,
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )

            // Seznam
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                itemsIndexed(displayList) { _, nb ->
                    NotebookItem(nb) { onNotebookClick(nb) }
                }
            }
        }
    }
}

@Composable
private fun NotebookItem(
    nb: NotebookLmApi.Notebook,
    onClick: () -> Unit,
) {
    val borderColor = Term.border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (nb.emoji.isNotEmpty()) {
            Text(
                text = nb.emoji,
                fontSize = Term.fontSizeXl,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nb.title,
                color = Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
            )
        }
        Text(
            text = ">",
            color = Term.textDim,
            fontFamily = Term.font,
            fontSize = Term.fontSizeLg,
        )
    }
}
