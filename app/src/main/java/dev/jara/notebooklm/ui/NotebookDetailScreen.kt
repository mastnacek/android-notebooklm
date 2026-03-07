package dev.jara.notebooklm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jara.notebooklm.rpc.NotebookLmApi

@Composable
fun NotebookDetailScreen(
    notebook: NotebookLmApi.Notebook,
    detail: DetailState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Term.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Header s back
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surface)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "< back",
                color = Term.cyan,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 16.dp),
            )
            val prefix = if (notebook.emoji.isNotEmpty()) "${notebook.emoji} " else ""
            Text(
                text = "$prefix${notebook.title}",
                color = Term.green,
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        }

        if (detail.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "> nacitam...",
                    color = Term.orange,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                // Souhrn
                if (!detail.summary.isNullOrBlank()) {
                    item {
                        SectionHeader("SOUHRN")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = detail.summary,
                            color = Term.text,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                            lineHeight = Term.fontSize * 1.5f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Term.surface)
                                .padding(12.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Zdroje
                item {
                    SectionHeader("ZDROJE (${detail.sources.size})")
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (detail.sources.isEmpty()) {
                    item {
                        Text(
                            text = "  zadne zdroje",
                            color = Term.textDim,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                        )
                    }
                } else {
                    itemsIndexed(detail.sources) { _, src ->
                        SourceItem(src)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val borderColor = Term.border
    Text(
        text = "── $title ──",
        color = Term.cyan,
        fontFamily = Term.font,
        fontSize = Term.fontSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
            .padding(bottom = 4.dp),
    )
}

@Composable
private fun SourceItem(src: NotebookLmApi.Source) {
    val borderColor = Term.border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = src.type.icon,
            fontSize = Term.fontSizeLg,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = src.title,
            color = Term.text,
            fontFamily = Term.font,
            fontSize = Term.fontSize,
            modifier = Modifier.weight(1f),
        )
    }
}
