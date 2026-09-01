package com.plutani.locust

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Void = Color(0xFF08090F)
val Surface1 = Color(0xFF12151F)
val Surface2 = Color(0xFF1A1E2B)
val Surface3 = Color(0xFF232838)
val LineColor = Color(0xFF262B3A)
val TextMain = Color(0xFFE8E9F0)
val TextMuted = Color(0xFF8A90A6)
val TextFaint = Color(0xFF5C627A)

/** Accent names match the web app so a profile carries across. */
val ACCENTS: Map<String, Pair<Color, Color>> = mapOf(
    "iris" to (Color(0xFFA855F7) to Color(0xFF7C3AED)),
    "ember" to (Color(0xFFF43F5E) to Color(0xFFBE123C)),
    "ocean" to (Color(0xFF38BDF8) to Color(0xFF0284C7)),
    "jade" to (Color(0xFF34D399) to Color(0xFF059669)),
    "amber" to (Color(0xFFFBBF24) to Color(0xFFD97706)),
    "rose" to (Color(0xFFEC4899) to Color(0xFFBE185D))
)

fun accentOf(key: String): Color = ACCENTS[key]?.first ?: ACCENTS.getValue("iris").first

private val LocustType = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun LocustTheme(accent: String = "iris", content: @Composable () -> Unit) {
    val a = accentOf(accent)
    val scheme = darkColorScheme(
        primary = a,
        onPrimary = Color.White,
        secondary = a,
        background = Void,
        onBackground = TextMain,
        surface = Surface1,
        onSurface = TextMain,
        surfaceVariant = Surface2,
        onSurfaceVariant = TextMuted,
        outline = LineColor,
        error = Color(0xFFFF8177)
    )
    MaterialTheme(colorScheme = scheme, typography = LocustType, content = content)
}
