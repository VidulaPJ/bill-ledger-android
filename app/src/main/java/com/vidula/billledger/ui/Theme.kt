package com.vidula.billledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Control-room palette: steel panel, black ink, white cards, one alarm red. */
@Immutable
data class Ledger(val panel: Color, val card: Color, val ink: Color, val mute: Color, val line: Color, val red: Color, val bar: Color, val onInk: Color)

private val LightLedger = Ledger(Color(0xFFD5DBE0), Color.White, Color(0xFF0D0F11), Color(0xFF4D565E), Color(0xFFB9C1C8), Color(0xFFE8251A), Color(0xFF0D0F11), Color.White)
private val DarkLedger = Ledger(Color(0xFF22272B), Color(0xFF1C2024), Color(0xFFEEF1F3), Color(0xFFA3ACB4), Color(0xFF353B41), Color(0xFFE8251A), Color(0xFFDFE4E8), Color(0xFF16191C))

val LocalLedger = staticCompositionLocalOf { LightLedger }

@Composable
fun BillLedgerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val l = if (dark) DarkLedger else LightLedger
    val scheme = if (dark) darkColorScheme(
        primary = l.ink, onPrimary = l.onInk, secondary = l.red, onSecondary = Color.White,
        background = Color(0xFF16191C), surface = Color(0xFF16191C), surfaceVariant = l.panel, onSurface = l.ink, onBackground = l.ink,
        error = l.red, outline = l.line, secondaryContainer = l.red, onSecondaryContainer = Color.White,
    ) else lightColorScheme(
        primary = l.ink, onPrimary = l.onInk, secondary = l.red, onSecondary = Color.White,
        background = Color.White, surface = Color.White, surfaceVariant = l.panel, onSurface = l.ink, onBackground = l.ink,
        error = l.red, outline = l.line, secondaryContainer = l.red, onSecondaryContainer = Color.White,
    )
    val base = Typography()
    val type = base.copy(
        headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalLedger provides l) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
