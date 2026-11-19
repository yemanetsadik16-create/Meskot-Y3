package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MeskotLightColorScheme = lightColorScheme(
    primary = MeskotGold,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5D9),
    onPrimaryContainer = MeskotGoldDeep,
    secondary = MeskotDarkAction,
    onSecondary = Color.White,
    secondaryContainer = MeskotPaper2,
    onSecondaryContainer = MeskotInk,
    tertiary = MeskotCross,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE7EA),
    onTertiaryContainer = MeskotCross,
    background = MeskotPaper,
    onBackground = MeskotInk,
    surface = MeskotCard,
    onSurface = MeskotInk,
    surfaceVariant = MeskotPaper2,
    onSurfaceVariant = MeskotMuted,
    outline = MeskotLine,
    outlineVariant = Color(0xFFECECEF),
    error = MeskotCross,
    onError = Color.White
)

private val MeskotShapes = Shapes(
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MeskotTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MeskotLightColorScheme,
        typography = MeskotTypography,
        shapes = MeskotShapes,
        content = content
    )
}
