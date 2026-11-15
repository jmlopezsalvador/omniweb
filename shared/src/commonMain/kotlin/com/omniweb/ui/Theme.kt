package com.omniweb.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tema oscuro propio. Antes se usaba el tema por defecto (claro) sobre fondo negro: los campos de texto salían
 * con letra oscura sobre negro y no se leían. Aquí fondo, superficies y texto salen juntos de la misma paleta.
 */
private val OmniColors = darkColorScheme(
    primary = Color(0xFFA394FF),
    onPrimary = Color(0xFF1B1448),
    primaryContainer = Color(0xFF2E2766),
    onPrimaryContainer = Color(0xFFE7E2FF),
    secondary = Color(0xFF55D6C8),
    onSecondary = Color(0xFF00201D),
    background = Color(0xFF0E0F14),
    onBackground = Color(0xFFECEDF3),
    surface = Color(0xFF171923),
    onSurface = Color(0xFFECEDF3),
    surfaceVariant = Color(0xFF232636),
    onSurfaceVariant = Color(0xFFAEB2C6),
    outline = Color(0xFF454A60),
    outlineVariant = Color(0xFF2C3043),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0000),
)

private val OmniTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private val OmniShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun OmniTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OmniColors, typography = OmniTypography, shapes = OmniShapes, content = content)
}
