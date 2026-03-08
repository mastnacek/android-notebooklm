package dev.jara.notebooklm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Jednoduchy Markdown renderer pro chat a summary texty.
 * Podporuje: **bold**, *italic*, `inline code`, ```code blocks```,
 * ## headings, - bullet lists.
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color = Term.text,
    modifier: Modifier = Modifier,
) {
    val blocks = parseMarkdownBlocks(text)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> {
                    val fontSize = when (block.level) {
                        1 -> 18.sp
                        2 -> 16.sp
                        else -> 15.sp
                    }
                    Text(
                        text = parseInlineMarkdown(block.text, color),
                        fontFamily = Term.font,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = Term.cyan,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                is MdBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Term.bg)
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                    ) {
                        Text(
                            text = block.code,
                            color = Term.green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }

                is MdBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = "• ",
                            color = Term.orange,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, color),
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                            lineHeight = Term.fontSize * 1.4f,
                        )
                    }
                }

                is MdBlock.Table -> {
                    TableBlock(block, color)
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, color),
                        fontFamily = Term.font,
                        fontSize = Term.fontSize,
                        lineHeight = Term.fontSize * 1.4f,
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// BLOKOVY PARSER
// ══════════════════════════════════════════════════════════════════════════════

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class CodeBlock(val code: String, val lang: String) : MdBlock()
    data class ListItem(val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
}

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block — ``` ... ```
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // preskoc zaviraci ```
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            continue
        }

        // Heading — # ## ###
        val headingMatch = Regex("^(#{1,4})\\s+(.+)").matchEntire(line.trimStart())
        if (headingMatch != null) {
            blocks.add(MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2]))
            i++
            continue
        }

        // List item — - nebo *
        val listMatch = Regex("^\\s*[-*+]\\s+(.+)").matchEntire(line)
        if (listMatch != null) {
            blocks.add(MdBlock.ListItem(listMatch.groupValues[1]))
            i++
            continue
        }

        // Numbered list — 1. 2. atd.
        val numListMatch = Regex("^\\s*\\d+\\.\\s+(.+)").matchEntire(line)
        if (numListMatch != null) {
            blocks.add(MdBlock.ListItem(numListMatch.groupValues[1]))
            i++
            continue
        }

        // Tabulka — | col1 | col2 | s oddelovacim radkem |---|---|
        if (line.trimStart().startsWith("|") && i + 1 < lines.size &&
            Regex("^\\s*\\|[\\s:]*-+[\\s:]*\\|").containsMatchIn(lines[i + 1])
        ) {
            val headerCells = parseTableRow(line)
            i += 2 // preskoc header + separator
            val tableRows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                tableRows.add(parseTableRow(lines[i]))
                i++
            }
            blocks.add(MdBlock.Table(headerCells, tableRows))
            continue
        }

        // Prazdny radek
        if (line.isBlank()) {
            i++
            continue
        }

        // Paragraph — sber po sobe jdoucich radku
        val paraLines = mutableListOf(line)
        i++
        while (i < lines.size &&
            lines[i].isNotBlank() &&
            !lines[i].trimStart().startsWith("```") &&
            !lines[i].trimStart().startsWith("#") &&
            !lines[i].trimStart().startsWith("|") &&
            !Regex("^\\s*[-*+]\\s+").containsMatchIn(lines[i]) &&
            !Regex("^\\s*\\d+\\.\\s+").containsMatchIn(lines[i])
        ) {
            paraLines.add(lines[i])
            i++
        }
        blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
    }

    return blocks
}

// ══════════════════════════════════════════════════════════════════════════════
// INLINE PARSER — **bold**, *italic*, `code`
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun parseInlineMarkdown(text: String, baseColor: Color): AnnotatedString {
    val codeColor = Term.green
    val codeBg = Term.bg

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // **bold**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Term.white)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // `inline code`
                text[i] == '`' && !text.startsWith("```", i) -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeBg,
                        )) {
                            append(" ${text.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // *italic* (ale ne ** ktere jsme uz zpracovali)
                text[i] == '*' && (i + 1 < text.length && text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Term.textDim)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                else -> {
                    // Normalni text — sber az do dalsiho specialniho znaku
                    val next = findNextSpecial(text, i + 1)
                    withStyle(SpanStyle(color = baseColor)) {
                        append(text.substring(i, next))
                    }
                    i = next
                }
            }
        }
    }
}

/**
 * Renderuje MD tabulku jako monospace text s paddingem sloupcu.
 * Pouziva horizontalni scroll pro siroke tabulky.
 */
@Composable
private fun TableBlock(table: MdBlock.Table, baseColor: Color) {
    // Spocitej max sirku kazdeho sloupce
    val colCount = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    val colWidths = (0 until colCount).map { col ->
        val headerW = table.headers.getOrElse(col) { "" }.length
        val maxRowW = table.rows.maxOfOrNull { it.getOrElse(col) { "" }.length } ?: 0
        maxOf(headerW, maxRowW, 3)
    }

    fun formatRow(cells: List<String>): String = buildString {
        append("│ ")
        for (col in 0 until colCount) {
            val cell = cells.getOrElse(col) { "" }
            append(cell.padEnd(colWidths[col]))
            if (col < colCount - 1) append(" │ ")
        }
        append(" │")
    }

    val separator = buildString {
        append("├─")
        for (col in 0 until colCount) {
            append("─".repeat(colWidths[col]))
            if (col < colCount - 1) append("─┼─")
        }
        append("─┤")
    }

    val tableText = buildString {
        appendLine(formatRow(table.headers))
        appendLine(separator)
        for (row in table.rows) {
            appendLine(formatRow(row))
        }
    }.trimEnd()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Term.bg)
            .horizontalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        Text(
            text = tableText,
            color = baseColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

/** Rozparsuje radek tabulky: | a | b | c | -> ["a", "b", "c"] */
private fun parseTableRow(line: String): List<String> {
    val trimmed = line.trim()
    val content = if (trimmed.startsWith("|")) trimmed.substring(1) else trimmed
    val withoutTrailing = if (content.endsWith("|")) content.dropLast(1) else content
    return withoutTrailing.split("|").map { it.trim() }
}

private fun findNextSpecial(text: String, from: Int): Int {
    for (i in from until text.length) {
        if (text[i] == '*' || text[i] == '`') return i
    }
    return text.length
}
