package com.nutriscan.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0B4),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    tertiary = Color(0xFF39656B),
    background = Color(0xFFF7FBF2),
    surface = Color(0xFFF7FBF2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DD49A),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF145218),
    onPrimaryContainer = Color(0xFFB9F0B4),
    secondary = Color(0xFFB9CCB4),
    tertiary = Color(0xFFA1CED5),
)

/** Colores fijos para los macronutrientes en toda la app. */
object MacroColors {
    val protein = Color(0xFFE57373)
    val carbs = Color(0xFFFFB74D)
    val fat = Color(0xFF64B5F6)
}

@Composable
fun NutriScanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
