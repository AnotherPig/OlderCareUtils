package com.spanzy.oldercare.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 暖色配色方案
private val WarmColorScheme = lightColorScheme(
    primary = WarmPrimaryText,
    onPrimary = Color.White,
    secondary = WarmSecondaryText,
    onSecondary = Color.White,
    tertiary = WarmTertiaryText,
    onTertiary = Color.White,
    background = WarmBackground,
    onBackground = WarmPrimaryText,
    surface = WarmButtonBackground,
    onSurface = WarmPrimaryText
)

// 深色配色方案
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryText,
    onPrimary = Color.Black,
    secondary = DarkSecondaryText,
    onSecondary = Color.Black,
    tertiary = DarkTertiaryText,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkButtonBackground,
    onSurface = DarkPrimaryText
)

/**
 * 应用主题
 * @param darkMode 用户手动控制的深色模式开关（覆盖系统设置）
 * @param content 主题内容
 */
@Composable
fun MyOlderCareUtilTheme(
    darkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkMode) DarkColorScheme else WarmColorScheme

    // 根据主题动态设置状态栏图标颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            // 深色模式：状态栏图标用浅色（白色）；浅色模式：状态栏图标用深色（黑色）
            insetsController.isAppearanceLightStatusBars = !darkMode
            insetsController.isAppearanceLightNavigationBars = !darkMode
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
