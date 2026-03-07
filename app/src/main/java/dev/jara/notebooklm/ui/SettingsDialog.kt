package dev.jara.notebooklm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsDialog(
    currentApiKey: String,
    onSave: (apiKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var apiKey by remember { mutableStateOf(currentApiKey) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Term.surface)
                .padding(20.dp),
        ) {
            Text(
                text = "── NASTAVENI ──",
                color = Term.cyan,
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "OpenRouter API klic:",
                color = Term.text,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
            )
            Spacer(modifier = Modifier.height(6.dp))

            BasicTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                textStyle = TextStyle(
                    color = Term.white,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                ),
                cursorBrush = SolidColor(Term.green),
                singleLine = true,
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Term.bg)
                            .padding(10.dp),
                    ) {
                        if (apiKey.isEmpty()) {
                            Text(
                                text = "sk-or-...",
                                color = Term.textDim,
                                fontFamily = Term.font,
                                fontSize = Term.fontSize,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "model: qwen/qwen3-embedding-8b",
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "[zrusit]",
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "[ulozit]",
                    color = Term.green,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            onSave(apiKey.trim())
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
