package dev.jara.notebooklm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun TerminalScreen(
    lines: List<TermLine>,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    // Auto-scroll dolu pri novem radku
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Term.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .windowInsetsPadding(WindowInsets.ime)
            .padding(8.dp)
    ) {
        // Vypis terminalu
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(lines) { line ->
                Text(
                    text = line.text,
                    color = line.color,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                    lineHeight = Term.fontSize * 1.3f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Prompt
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surfaceLight)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "notebooklm> ",
                color = Term.green,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
            )
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                ),
                cursorBrush = SolidColor(Term.green),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (input.isNotBlank()) {
                            onCommand(input)
                            input = ""
                        }
                    }
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
