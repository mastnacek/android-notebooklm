package dev.jara.notebooklm.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun SettingsDialog(
    currentApiKey: String,
    currentDownloadPath: String,
    currentClassifyModel: String,
    currentThemeMode: ThemeMode,
    onSave: (apiKey: String, classifyModel: String) -> Unit,
    onPickFolder: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var classifyModel by remember { mutableStateOf(currentClassifyModel) }

    val displayPath = if (currentDownloadPath.isNotEmpty()) {
        try {
            val decoded = Uri.decode(currentDownloadPath)
            // Vytáhni cestu za "primary:" nebo "document/"
            val raw = decoded.substringAfter("document/primary:", "")
                .ifEmpty { decoded.substringAfter("document/", "") }
                .ifEmpty { decoded }
            // Zobraz max poslední 2 segmenty cesty
            val segments = raw.split("/").filter { it.isNotEmpty() }
            if (segments.size <= 2) segments.joinToString("/")
            else segments.takeLast(2).joinToString("/", prefix = ".../")
        } catch (_: Exception) {
            currentDownloadPath.substringAfterLast("/")
        }
    } else {
        "Downloads/notebooklm"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Term.surface)
                .padding(24.dp),
        ) {
            // Header
            Text(
                text = "Nastavení",
                color = Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSizeXl,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── Motiv — ikonky ──
            SettingsLabel("Motiv")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                data class ThemeOption(val mode: ThemeMode, val icon: String)
                val options = listOf(
                    ThemeOption(ThemeMode.SYSTEM, "📱"),
                    ThemeOption(ThemeMode.DARK, "🌙"),
                    ThemeOption(ThemeMode.LIGHT, "☀️"),
                )
                for (opt in options) {
                    val selected = opt.mode == currentThemeMode
                    val shape = RoundedCornerShape(12.dp)
                    Text(
                        text = opt.icon,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(shape)
                            .then(
                                if (selected) Modifier
                                    .background(Term.green.copy(alpha = 0.15f))
                                    .border(1.5.dp, Term.green.copy(alpha = 0.5f), shape)
                                else Modifier.background(Term.bg)
                            )
                            .clickable { onThemeChange(opt.mode) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── OpenRouter API key ──
            SettingsLabel("OpenRouter API klíč")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsInput(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = "sk-or-...",
            )
            Text(
                text = "embedding: qwen/qwen3-embedding-8b",
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Klasifikacni model ──
            SettingsLabel("Model pro klasifikaci")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsInput(
                value = classifyModel,
                onValueChange = { classifyModel = it },
                placeholder = "google/gemini-...",
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Slozka pro stahovani ──
            SettingsLabel("Složka pro stahování")
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = displayPath,
                    color = Term.text,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Term.bg)
                        .padding(12.dp),
                    maxLines = 2,
                )
                Spacer(modifier = Modifier.width(8.dp))
                SettingsPill("📂", Term.orange) { onPickFolder() }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ── Tlacitka ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsPill("Zrušit", Term.textDim) { onDismiss() }
                Spacer(modifier = Modifier.width(10.dp))
                val shape = RoundedCornerShape(12.dp)
                Text(
                    text = "Uložit",
                    color = Term.bg,
                    fontFamily = Term.font,
                    fontSize = Term.fontSizeLg,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(shape)
                        .background(Term.green)
                        .clickable {
                            onSave(apiKey.trim(), classifyModel.trim())
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        color = Term.textDim,
        fontFamily = Term.font,
        fontSize = Term.fontSize,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(Term.bg)
                    .padding(12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
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
}

@Composable
private fun SettingsPill(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = color,
        fontFamily = Term.font,
        fontSize = Term.fontSizeLg,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
