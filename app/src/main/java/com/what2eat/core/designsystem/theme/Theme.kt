package com.what2eat.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = md_primary_light,
    onPrimary = on_primary_light,
    primaryContainer = primary_container_light,
    onPrimaryContainer = on_primary_container_light,
    secondary = secondary_light,
    onSecondary = on_secondary_light,
    secondaryContainer = secondary_container_light,
    onSecondaryContainer = on_secondary_container_light,
    tertiary = tertiary_light,
    onTertiary = on_tertiary_light,
    tertiaryContainer = tertiary_container_light,
    onTertiaryContainer = on_tertiary_container_light,
    background = background_light,
    onBackground = on_background_light,
    surface = surface_light,
    onSurface = on_surface_light,
    surfaceVariant = surface_variant_light,
    onSurfaceVariant = on_surface_variant_light,
    error = error_light,
    onError = on_error_light,
    errorContainer = error_container_light,
    onErrorContainer = on_error_container_light,
    outline = outline_light,
    // 以下槽位必须显式传入，否则回落 Material3 紫色默认值（紫色泄漏）
    outlineVariant = outline_variant_light,
    scrim = scrim_light,
    surfaceTint = surface_tint_light,
    inverseSurface = inverse_surface_light,
    inverseOnSurface = inverse_on_surface_light,
    inversePrimary = inverse_primary_light,
    surfaceDim = surface_dim_light,
    surfaceBright = surface_bright_light,
    surfaceContainerLowest = surface_container_lowest_light,
    surfaceContainerLow = surface_container_low_light,
    surfaceContainer = surface_container_light,
    surfaceContainerHigh = surface_container_high_light,
    surfaceContainerHighest = surface_container_highest_light
)

private val DarkColorScheme = darkColorScheme(
    primary = md_primary_dark,
    onPrimary = on_primary_dark,
    primaryContainer = primary_container_dark,
    onPrimaryContainer = on_primary_container_dark,
    secondary = secondary_dark,
    onSecondary = on_secondary_dark,
    secondaryContainer = secondary_container_dark,
    onSecondaryContainer = on_secondary_container_dark,
    tertiary = tertiary_dark,
    onTertiary = on_tertiary_dark,
    tertiaryContainer = tertiary_container_dark,
    onTertiaryContainer = on_tertiary_container_dark,
    background = background_dark,
    onBackground = on_background_dark,
    surface = surface_dark,
    onSurface = on_surface_dark,
    surfaceVariant = surface_variant_dark,
    onSurfaceVariant = on_surface_variant_dark,
    error = error_dark,
    onError = on_error_dark,
    errorContainer = error_container_dark,
    onErrorContainer = on_error_container_dark,
    outline = outline_dark,
    // 以下槽位必须显式传入，否则回落 Material3 紫色默认值（紫色泄漏）
    outlineVariant = outline_variant_dark,
    scrim = scrim_dark,
    surfaceTint = surface_tint_dark,
    inverseSurface = inverse_surface_dark,
    inverseOnSurface = inverse_on_surface_dark,
    inversePrimary = inverse_primary_dark,
    surfaceDim = surface_dim_dark,
    surfaceBright = surface_bright_dark,
    surfaceContainerLowest = surface_container_lowest_dark,
    surfaceContainerLow = surface_container_low_dark,
    surfaceContainer = surface_container_dark,
    surfaceContainerHigh = surface_container_high_dark,
    surfaceContainerHighest = surface_container_highest_dark
)

/**
 * What2Eat 主题 —— 「奶油橘 + 米白」品牌色。
 *
 * 默认使用品牌色板（dynamicColor = false），保证全设备一致的温馨视觉；
 * dynamicColor 参数保留，未来可作为设置项开关启用动态取色。
 */
@Composable
fun What2EatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = What2EatTypography,
        shapes = What2EatShapes,
        content = content
    )
}
