package dev.jara.notebooklm.ui

import androidx.compose.ui.graphics.Color

sealed class TermLine(val text: String, val color: Color) {
    class Ok(msg: String) : TermLine("[OK] $msg", Gruvbox.BrightGreen)
    class Error(msg: String) : TermLine("[!!] $msg", Gruvbox.BrightRed)
    class Warn(msg: String) : TermLine("[..] $msg", Gruvbox.BrightYellow)
    class Info(msg: String) : TermLine("[--] $msg", Gruvbox.BrightBlue)
    class Input(msg: String) : TermLine(msg, Gruvbox.BrightPurple)
    class Text(msg: String) : TermLine(msg, Gruvbox.Light2)
    object Blank : TermLine("", Color.Transparent)
}
