package dev.jara.notebooklm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jara.notebooklm.rpc.NotebookLmApi

@Composable
fun NotebookListScreen(
    notebooks: List<NotebookLmApi.Notebook>,
    loading: Boolean,
    onNotebookClick: (NotebookLmApi.Notebook) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
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
                text = "[refresh]",
                color = Term.cyan,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier
                    .clickable { onRefresh() }
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = "[logout]",
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier
                    .clickable { onLogout() }
                    .padding(start = 4.dp),
            )
        }

        // Search
        var query by remember { mutableStateOf("") }
        val filtered = remember(notebooks, query) {
            if (query.isBlank()) notebooks
            else {
                val q = query.lowercase()
                notebooks.filter { nb ->
                    nb.title.lowercase().contains(q) ||
                    nb.emoji.lowercase().contains(q)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surfaceLight)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "/ ",
                color = Term.green,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
            )
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = TextStyle(
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                ),
                cursorBrush = SolidColor(Term.green),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "hledat sesity...",
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
                        .clickable { query = "" }
                        .padding(start = 8.dp),
                )
            }
        }

        // Stav
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "> nacitam sesity...",
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
            val countText = if (query.isNotBlank()) {
                "  ${filtered.size}/${notebooks.size} sesitu"
            } else {
                "  ${notebooks.size} sesitu"
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
                itemsIndexed(filtered) { _, nb ->
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
        // Emoji
        if (nb.emoji.isNotEmpty()) {
            Text(
                text = nb.emoji,
                fontSize = Term.fontSizeXl,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        // Nazev
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nb.title,
                color = Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
            )
        }

        // Sipka
        Text(
            text = ">",
            color = Term.textDim,
            fontFamily = Term.font,
            fontSize = Term.fontSizeLg,
        )
    }
}
