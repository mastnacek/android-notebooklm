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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════════════════════
// GRUVBOX PALETA (prevzato z braingate)
// ══════════════════════════════════════════════════════════════════════════════

object Gruvbox {
    val Dark0Hard = Color(0xFF1D2021)
    val Dark0 = Color(0xFF282828)
    val Dark0Soft = Color(0xFF32302F)
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
    text = Gruvbox.Light2,
    textDim = Gruvbox.Gray,          // #928374 — kontrast 5.3:1 (WCAG AA), čitelnější než Dark4
    white = Gruvbox.Light1,
    border = Gruvbox.Dark2,
)

val LightAppColors = AppColors(
    bg = Gruvbox.Light0,
    surface = Gruvbox.Light1,
    surfaceLight = Gruvbox.Light2,
    green = Gruvbox.FadedGreen,
    cyan = Gruvbox.FadedBlue,
    orange = Gruvbox.FadedOrange,
    red = Gruvbox.FadedRed,
    purple = Gruvbox.FadedPurple,
    text = Gruvbox.Dark0Hard,       // #1D2021 — max kontrast na Light0 pozadí
    textDim = Gruvbox.Dark4,        // #7C6F64 — čitelnější než Light4
    white = Gruvbox.Dark0,          // #282828 — důrazný text
    border = Gruvbox.Light3,
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// ══════════════════════════════════════════════════════════════════════════════
// VOLBA MOTIVU
// ══════════════════════════════════════════════════════════════════════════════

enum class ThemeMode(val label: String) {
    SYSTEM("Systém"),
    DARK("Tmavý"),
    LIGHT("Světlý");

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
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
            primary = Gruvbox.FadedBlue,
            secondary = Gruvbox.FadedGreen,
            tertiary = Gruvbox.FadedOrange,
            background = Gruvbox.Light0,
            surface = Gruvbox.Light0,
            surfaceVariant = Gruvbox.Light2,
            onPrimary = Gruvbox.Light0,
            onSecondary = Gruvbox.Light0,
            onBackground = Gruvbox.Dark0Hard,
            onSurface = Gruvbox.Dark0Hard,
            onSurfaceVariant = Gruvbox.Dark3,
            error = Gruvbox.FadedRed,
            outline = Gruvbox.Light4,
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
            window.statusBarColor = if (darkTheme) 0xFF1D2021.toInt() else 0xFFEBDBB2.toInt()
            @Suppress("DEPRECATION")
            window.navigationBarColor = if (darkTheme) 0xFF282828.toInt() else 0xFFEBDBB2.toInt()
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
// ZPETNA KOMPATIBILITA — Term objekt deleguje na aktualni motiv
// Vsech 283 referenci v UI souborech zustava beze zmeny.
// ══════════════════════════════════════════════════════════════════════════════

// ══════════════════════════════════════════════════════════════════════════════
// DESIGN SYSTEM KONSTANTY — standardizovane hodnoty pro konzistentni UI
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
