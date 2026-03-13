package dev.jara.notebooklm.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════════════════════
// GRUVBOX PALETA (prevzato z braingate)
// ══════════════════════════════════════════════════════════════════════════════

object Gruvbox {
    val Dark0Hard = Color(0xFF1D2021)
    val Dark0 = Color(0xFF282828)
    val Dark1 = Color(0xFF3C3836)
    val Dark2 = Color(0xFF504945)
    val Dark3 = Color(0xFF665C54)
    val Dark4 = Color(0xFF7C6F64)

    val Light0 = Color(0xFFFBF1C7)
    val Light1 = Color(0xFFEBDBB2)
    val Light2 = Color(0xFFD5C4A1)
    val Light3 = Color(0xFFBDAE93)
    val Light4 = Color(0xFFA89984)

    val BrightRed = Color(0xFFFB4934)
    val BrightGreen = Color(0xFFB8BB26)
    val BrightYellow = Color(0xFFFABD2F)
    val BrightBlue = Color(0xFF83A598)
    val BrightPurple = Color(0xFFD3869B)
    val BrightAqua = Color(0xFF8EC07C)
    val BrightOrange = Color(0xFFFE8019)

    val NeutralRed = Color(0xFFCC241D)
    val NeutralGreen = Color(0xFF98971A)
    val NeutralYellow = Color(0xFFD79921)
    val NeutralBlue = Color(0xFF458588)
    val NeutralPurple = Color(0xFFB16286)
    val NeutralAqua = Color(0xFF689D6A)
    val NeutralOrange = Color(0xFFD65D0E)

    val FadedRed = Color(0xFF9D0006)
    val FadedGreen = Color(0xFF79740E)
    val FadedYellow = Color(0xFFB57614)
    val FadedBlue = Color(0xFF076678)
    val FadedPurple = Color(0xFF8F3F71)
    val FadedAqua = Color(0xFF427B58)
    val FadedOrange = Color(0xFFAF3A03)

    val Gray = Color(0xFF928374)
}

// ══════════════════════════════════════════════════════════════════════════════
// SEMANTICKE BARVY APLIKACE
// ══════════════════════════════════════════════════════════════════════════════

data class AppColors(
    val bg: Color,
    val surface: Color,
    val surfaceLight: Color,
    val green: Color,
    val cyan: Color,
    val orange: Color,
    val red: Color,
    val purple: Color,
    val yellow: Color,              // primary CTA — hlavní akce
    val disabled: Color,            // neaktivní prvky
    val text: Color,
    val textDim: Color,
    val white: Color,
    val border: Color,
)

val DarkAppColors = AppColors(
    bg = Gruvbox.Dark0Hard,
    surface = Gruvbox.Dark0,
    surfaceLight = Gruvbox.Dark1,
    green = Gruvbox.BrightGreen,
    cyan = Gruvbox.BrightBlue,
    orange = Gruvbox.BrightOrange,
    red = Gruvbox.BrightRed,
    purple = Gruvbox.BrightPurple,
    yellow = Gruvbox.BrightYellow,   // #FABD2F — primary CTA, kontrast 9.5:1 na Dark0Hard
    disabled = Gruvbox.Dark3,        // #665C54 — tlumená šedohnědá pro neaktivní prvky
    text = Gruvbox.Light2,
    textDim = Gruvbox.Gray,          // #928374 — kontrast 5.3:1 (WCAG AA), čitelnější než Dark4
    white = Gruvbox.Light1,
    border = Gruvbox.Dark2,
)

val LightAppColors = AppColors(
    bg = Color(0xFFF9F5D7),          // Light0_hard — světlejší pozadí, karty vystoupí
    surface = Gruvbox.Light1,        // #EBDBB2 — povrch karet
    surfaceLight = Gruvbox.Light2,   // #D5C4A1 — zvýrazněná plocha (search, input)
    green = Gruvbox.NeutralGreen,    // #98971A — sytější zelená (Neutral místo Faded)
    cyan = Gruvbox.NeutralBlue,      // #458588 — sytější modrá
    orange = Gruvbox.NeutralOrange,  // #D65D0E — sytější oranžová
    red = Gruvbox.NeutralRed,        // #CC241D — sytější červená
    purple = Gruvbox.NeutralPurple,  // #B16286 — sytější fialová
    yellow = Gruvbox.NeutralYellow,  // #D79921 — primary CTA
    disabled = Gruvbox.Light4,       // #A89984 — tlumená pro neaktivní prvky
    text = Gruvbox.Dark0Hard,        // #1D2021 — max kontrast
    textDim = Gruvbox.Dark2,         // #504945 — kontrast 5.9:1 na Light0_hard (WCAG AA)
    white = Gruvbox.Dark0,           // #282828 — důrazný text
    border = Gruvbox.Dark4,          // #7C6F64 — ostré terminálové bordery, jasně viditelné
)

// ══════════════════════════════════════════════════════════════════════════════
// DOOM ONE DARK PALETA (Atom One Dark inspired)
// ══════════════════════════════════════════════════════════════════════════════

object DoomOne {
    val Bg = Color(0xFF282C34)
    val BgCard = Color(0xFF2C323C)
    val Border = Color(0xFF3E4451)
    val Fg = Color(0xFFABB2BF)
    val FgDim = Color(0xFF5C6370)

    val Blue = Color(0xFF61AFEF)
    val Green = Color(0xFF98C379)
    val Red = Color(0xFFE06C75)
    val Yellow = Color(0xFFE5C07B)
    val Purple = Color(0xFFC678DD)
    val Cyan = Color(0xFF56B6C2)
    val Orange = Color(0xFFD19A66)
}

val DoomOneDarkColors = AppColors(
    bg = DoomOne.Bg,                 // #282C34
    surface = DoomOne.BgCard,        // #2C323C
    surfaceLight = DoomOne.Border,   // #3E4451
    green = DoomOne.Green,           // #98C379
    cyan = DoomOne.Cyan,             // #56B6C2
    orange = DoomOne.Orange,         // #D19A66
    red = DoomOne.Red,               // #E06C75
    purple = DoomOne.Purple,         // #C678DD
    yellow = DoomOne.Yellow,         // #E5C07B — CTA
    disabled = DoomOne.FgDim,        // #5C6370
    text = DoomOne.Fg,               // #ABB2BF
    textDim = DoomOne.FgDim,         // #5C6370
    white = Color(0xFFE5E9F0),       // near-white
    border = DoomOne.Border,         // #3E4451
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// ══════════════════════════════════════════════════════════════════════════════
// VOLBA MOTIVU
// ══════════════════════════════════════════════════════════════════════════════

enum class ThemeMode(val label: String) {
    SYSTEM("Systém"),
    DARK("Tmavý"),
    LIGHT("Světlý"),
    DOOM("DoD");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

// ══════════════════════════════════════════════════════════════════════════════
// THEME PROVIDER
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun NotebookLmTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK, ThemeMode.DOOM -> true
        ThemeMode.LIGHT -> false
    }
    val appColors = when (themeMode) {
        ThemeMode.DOOM -> DoomOneDarkColors
        ThemeMode.LIGHT -> LightAppColors
        ThemeMode.DARK -> DarkAppColors
        ThemeMode.SYSTEM -> if (darkTheme) DarkAppColors else LightAppColors
    }

    val colorScheme = when (themeMode) {
        ThemeMode.DOOM -> darkColorScheme(
            primary = DoomOne.Blue,
            secondary = DoomOne.Green,
            tertiary = DoomOne.Orange,
            background = DoomOne.Bg,
            surface = DoomOne.BgCard,
            surfaceVariant = DoomOne.Border,
            onPrimary = DoomOne.Bg,
            onSecondary = DoomOne.Bg,
            onBackground = DoomOne.Fg,
            onSurface = DoomOne.Fg,
            onSurfaceVariant = DoomOne.FgDim,
            error = DoomOne.Red,
            outline = DoomOne.Border,
        )
        ThemeMode.LIGHT -> lightColorScheme(
            primary = Gruvbox.FadedBlue,
            secondary = Gruvbox.FadedGreen,
            tertiary = Gruvbox.FadedOrange,
            background = Color(0xFFF9F5D7),
            surface = Color(0xFFF9F5D7),
            surfaceVariant = Gruvbox.Light2,
            onPrimary = Gruvbox.Light0,
            onSecondary = Gruvbox.Light0,
            onBackground = Gruvbox.Dark0Hard,
            onSurface = Gruvbox.Dark0Hard,
            onSurfaceVariant = Gruvbox.Dark3,
            error = Gruvbox.FadedRed,
            outline = Gruvbox.Light4,
        )
        else -> darkColorScheme(
            primary = Gruvbox.BrightYellow,
            secondary = Gruvbox.BrightGreen,
            tertiary = Gruvbox.BrightOrange,
            background = Gruvbox.Dark0Hard,
            surface = Gruvbox.Dark0,
            surfaceVariant = Gruvbox.Dark1,
            onPrimary = Gruvbox.Dark0,
            onSecondary = Gruvbox.Dark0,
            onBackground = Gruvbox.Light1,
            onSurface = Gruvbox.Light1,
            onSurfaceVariant = Gruvbox.Light4,
            error = Gruvbox.BrightRed,
            outline = Gruvbox.Dark4,
        )
    }

    // Nastavení barvy ikon a pozadí systémových barů podle motivu
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            // Neprůhledný status bar — app nekrvácí do systémové oblasti
            @Suppress("DEPRECATION")
            window.statusBarColor = when (themeMode) {
                ThemeMode.DOOM -> 0xFF282C34.toInt()
                ThemeMode.LIGHT -> 0xFFF9F5D7.toInt()
                else -> if (darkTheme) 0xFF1D2021.toInt() else 0xFFF9F5D7.toInt()
            }
            @Suppress("DEPRECATION")
            window.navigationBarColor = when (themeMode) {
                ThemeMode.DOOM -> 0xFF2C323C.toInt()
                ThemeMode.LIGHT -> 0xFFEBDBB2.toInt()
                else -> if (darkTheme) 0xFF282828.toInt() else 0xFFEBDBB2.toInt()
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DESIGN SYSTEM KONSTANTY
// ══════════════════════════════════════════════════════════════════════════════

object DS {
    // Corner radii
    val cardRadius = 14.dp
    val buttonRadius = 10.dp
    val dialogRadius = 16.dp
    val chipRadius = 8.dp
    val microRadius = 6.dp
    val inputRadius = 10.dp
    val searchRadius = 16.dp
    val snackbarRadius = 12.dp

    // Border
    const val borderAlpha = 0.3f
    const val selectionAlpha = 0.12f
    val borderWidth = 1.dp
    val borderWidthSelected = 1.5.dp

    // Shimmer
    const val shimmerDurationMs = 1000
}

object Term {
    val bg: Color @Composable get() = LocalAppColors.current.bg
    val surface: Color @Composable get() = LocalAppColors.current.surface
    val surfaceLight: Color @Composable get() = LocalAppColors.current.surfaceLight
    val green: Color @Composable get() = LocalAppColors.current.green
    val cyan: Color @Composable get() = LocalAppColors.current.cyan
    val orange: Color @Composable get() = LocalAppColors.current.orange
    val red: Color @Composable get() = LocalAppColors.current.red
    val purple: Color @Composable get() = LocalAppColors.current.purple
    val yellow: Color @Composable get() = LocalAppColors.current.yellow
    val disabled: Color @Composable get() = LocalAppColors.current.disabled
    val text: Color @Composable get() = LocalAppColors.current.text
    val textDim: Color @Composable get() = LocalAppColors.current.textDim
    val white: Color @Composable get() = LocalAppColors.current.white
    val border: Color @Composable get() = LocalAppColors.current.border

    val font = FontFamily.Monospace
    val fontSize = 13.sp
    val fontSizeLg = 15.sp
    val fontSizeXl = 18.sp
    val fontSizeRead = 15.sp      // pro čtený obsah (summary, chat odpovědi)
    val lineHeightRead = 23.sp    // 1.53× — optimální pro delší texty
}
