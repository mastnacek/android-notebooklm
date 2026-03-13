package dev.jara.notebooklm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════════════════════
// Sdílené UI komponenty pro detail sešitu
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun DetailPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(DS.buttonRadius)
    val isSingleChar = text.codePointCount(0, text.length) == 1
    val isSubdued = color == Term.textDim || color == Term.disabled
    // Plné pozadí pro výrazné barvy, border-only pro subdued (Zavřít, Zrušit)
    val bgMod = if (isSubdued) {
        Modifier.border(DS.borderWidth, color.copy(alpha = DS.borderAlpha), shape)
    } else {
        Modifier.background(color).border(DS.borderWidth, color, shape)
    }
    val textColor = if (isSubdued) color else Term.bg
    Text(
        text = text,
        color = textColor,
        fontFamily = Term.font,
        fontSize = Term.fontSize,
        fontWeight = FontWeight.SemiBold,
        textAlign = if (isSingleChar) androidx.compose.ui.text.style.TextAlign.Center else null,
        modifier = modifier
            .clip(shape)
            .then(bgMod)
            .clickable(onClick = onClick)
            .then(
                if (isSingleChar) Modifier.defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                    .padding(horizontal = 7.dp, vertical = 7.dp)
                else Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ),
    )
}

@Composable
internal fun IconMicroAction(icon: ImageVector, color: Color, contentDesc: String? = null, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = contentDesc,
        tint = color,
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(DS.microRadius))
            .clickable(onClick = onClick)
            .padding(2.dp),
    )
}

@Composable
internal fun IconPill(icon: ImageVector, color: Color, contentDesc: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DS.buttonRadius)
    Icon(
        imageVector = icon,
        contentDescription = contentDesc,
        tint = color,
        modifier = Modifier
            .size(30.dp)
            .clip(shape)
            .border(DS.borderWidth, color.copy(alpha = DS.borderAlpha), shape)
            .clickable(onClick = onClick)
            .padding(5.dp),
    )
}

@Composable
internal fun IconDetailPill(icon: ImageVector, color: Color, contentDesc: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DS.buttonRadius)
    val isSubdued = color == Term.textDim || color == Term.disabled
    val bgMod = if (isSubdued) {
        Modifier.border(DS.borderWidth, color.copy(alpha = DS.borderAlpha), shape)
    } else {
        Modifier.background(color).border(DS.borderWidth, color, shape)
    }
    Icon(
        imageVector = icon,
        contentDescription = contentDesc,
        tint = if (isSubdued) color else Term.bg,
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .then(bgMod)
            .clickable(onClick = onClick)
            .padding(7.dp),
    )
}

@Composable
internal fun MicroAction(icon: String, color: Color, onClick: () -> Unit) {
    Text(
        text = icon,
        color = color,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(DS.microRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp),
    )
}

@Composable
internal fun DetailInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    maxLines: Int = 1,
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
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DS.inputRadius))
                    .background(Term.bg)
                    .padding(12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = Term.textDim, fontFamily = Term.font, fontSize = Term.fontSize)
                }
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
