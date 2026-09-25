package ru.mrcrubs.lms.android.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    secondary = Color(0xFF4F6379),
    tertiary = Color(0xFF2E7D5B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC5FF),
    secondary = Color(0xFFB7C8DD),
    tertiary = Color(0xFF8BD6AE),
)

/** Material 3 theme; uses wallpaper colors on Android 12+. */
@Composable
fun LmsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
