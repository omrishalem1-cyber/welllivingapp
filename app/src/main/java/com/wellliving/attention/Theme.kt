package com.wellliving.attention

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wellliving.trainer.Activity

/** Night-and-candlelight palette: deep indigo grounds with a warm amber accent. */
object Brand {
    val Amber = Color(0xFFF2B45A)
    val AmberDeep = Color(0xFF9A5B00)
    val Night = Color(0xFF12131C)
    val Indigo = Color(0xFF2B2F6B)
    val Violet = Color(0xFF5B3B8C)
    val OnNight = Color(0xFFF4F1EA)
    val OnNightMuted = Color(0xFFC9C4DA)

    /** Always-dark hero surfaces (headers, session and quote screens). */
    val heroGradient = Brush.linearGradient(listOf(Indigo, Violet))
    val nightGradient = Brush.verticalGradient(listOf(Color(0xFF0E0F1A), Indigo, Color(0xFF3A2A63)))
}

private val DarkColors = darkColorScheme(
    primary = Brand.Amber,
    onPrimary = Color(0xFF2A1A00),
    secondary = Color(0xFF9FB2FF),
    onSecondary = Color(0xFF0E1A4D),
    background = Brand.Night,
    onBackground = Brand.OnNight,
    surface = Brand.Night,
    onSurface = Brand.OnNight,
    surfaceVariant = Color(0xFF242636),
    onSurfaceVariant = Color(0xFFC3C0D0),
    surfaceContainer = Color(0xFF1C1E2B),
    surfaceContainerHigh = Color(0xFF242636),
    outline = Color(0xFF4A4C5E),
    outlineVariant = Color(0xFF34364A),
)

private val LightColors = lightColorScheme(
    primary = Brand.AmberDeep,
    onPrimary = Color.White,
    secondary = Color(0xFF3B4BA8),
    onSecondary = Color.White,
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFFAF7F2),
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFF0EBE3),
    onSurfaceVariant = Color(0xFF5D5A66),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF3EEE6),
    outline = Color(0xFFCFC8BD),
    outlineVariant = Color(0xFFE4DED4),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val typography = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = typography,
        content = content,
    )
}

val QuoteStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 40.sp)

/**
 * Categorical colours for activities, in a fixed order validated for colour-vision
 * deficiency. Colour follows the activity (its position in the full list, archived
 * included), so filters never repaint it. Past eight activities, a neutral gray.
 */
private val SeriesLight = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF6250D6, 0xFFE34948)
private val SeriesDark = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767)

@Composable
fun activityColor(activities: List<Activity>, id: String): Color {
    val index = activities.indexOfFirst { it.id == id }
    val series = if (isSystemInDarkTheme()) SeriesDark else SeriesLight
    return if (index in series.indices) Color(series[index]) else Color(0xFF8A8894)
}
