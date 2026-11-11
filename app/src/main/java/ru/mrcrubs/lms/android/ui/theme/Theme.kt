package ru.mrcrubs.lms.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    MaterialTheme(colorScheme = colors,
        shapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(26.dp),extraLarge=RoundedCornerShape(32.dp)),
        typography=Typography(
            headlineLarge=TextStyle(fontSize=36.sp,lineHeight=42.sp,fontWeight=FontWeight.SemiBold),
            headlineMedium=TextStyle(fontSize=29.sp,lineHeight=35.sp,fontWeight=FontWeight.SemiBold),
            titleLarge=TextStyle(fontSize=22.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold),
            titleMedium=TextStyle(fontSize=16.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold),
            labelLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
        ),content=content)
}
