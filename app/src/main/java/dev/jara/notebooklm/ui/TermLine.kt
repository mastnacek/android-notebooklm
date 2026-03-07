package dev.jara.notebooklm.ui

import androidx.compose.ui.graphics.Color

sealed class TermLine(val text: String, val color: Color) {
    class Ok(msg: String) : TermLine("[OK] $msg", Color(0xFF58D68D))
    class Error(msg: String) : TermLine("[!!] $msg", Color(0xFFE74C3C))
    class Warn(msg: String) : TermLine("[..] $msg", Color(0xFFF39C12))
    class Info(msg: String) : TermLine("[--] $msg", Color(0xFF5DADE2))
    class Input(msg: String) : TermLine(msg, Color(0xFFBB86FC))
    class Text(msg: String) : TermLine(msg, Color(0xFFC0C0C0))
    object Blank : TermLine("", Color.Transparent)
}
