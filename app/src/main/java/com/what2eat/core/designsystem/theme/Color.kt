package com.what2eat.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * What2Eat 品牌色板 —— 「奶油橘 + 米白」温馨风。
 *
 * 设计基调：
 * - 主色：奶油橘（食欲感、温度感）
 * - 背景：米白暖调（非冷白）
 * - 辅助：暖棕 + 燕麦米色容器
 * - tertiary：鼠尾草绿（香草点缀，与 error 红区分度高）
 * - 深色模式：暖炭棕背景 + 亮橘主色，保持温馨感
 *
 * 注意：所有槽位（含 surfaceContainer 梯度 / outlineVariant / inverse 系）
 * 必须显式定义并传入 ColorScheme，否则会回落 Material3 紫色默认值造成"紫色泄漏"。
 */

// ── Light Theme（奶油橘 + 米白）──
val md_primary_light = Color(0xFFA84B17)
val on_primary_light = Color(0xFFFFFFFF)
val primary_container_light = Color(0xFFFFDBC8)
val on_primary_container_light = Color(0xFF3A1700)

val secondary_light = Color(0xFF77563C)
val on_secondary_light = Color(0xFFFFFFFF)
val secondary_container_light = Color(0xFFF0E4CF)
val on_secondary_container_light = Color(0xFF2A1A04)

val tertiary_light = Color(0xFF586720)
val on_tertiary_light = Color(0xFFFFFFFF)
val tertiary_container_light = Color(0xFFDCE89B)
val on_tertiary_container_light = Color(0xFF414E0A)

val background_light = Color(0xFFFFF8F2)
val on_background_light = Color(0xFF221A14)
val surface_light = Color(0xFFFFF8F2)
val on_surface_light = Color(0xFF221A14)
val surface_variant_light = Color(0xFFEADDCF)
val on_surface_variant_light = Color(0xFF50443A)

val error_light = Color(0xFFB3261E)
val on_error_light = Color(0xFFFFFFFF)
val error_container_light = Color(0xFFF9DEDC)
val on_error_container_light = Color(0xFF410E0B)

val outline_light = Color(0xFF7A685A)
val outline_variant_light = Color(0xFFD8C7B7)
val scrim_light = Color(0xFF000000)

val surface_tint_light = Color(0xFFA84B17)
val inverse_surface_light = Color(0xFF382E26)
val inverse_on_surface_light = Color(0xFFF7EFE7)
val inverse_primary_light = Color(0xFFFFB784)

val surface_dim_light = Color(0xFFE5DBD0)
val surface_bright_light = Color(0xFFFFF8F2)
val surface_container_lowest_light = Color(0xFFFFFFFF)
val surface_container_low_light = Color(0xFFFCF1E8)
val surface_container_light = Color(0xFFF6EBE1)
val surface_container_high_light = Color(0xFFF0E4D9)
val surface_container_highest_light = Color(0xFFEADDCF)

// ── Dark Theme（暖炭棕 + 亮橘）──
val md_primary_dark = Color(0xFFFFB687)
val on_primary_dark = Color(0xFF4A2000)
val primary_container_dark = Color(0xFF7A3500)
val on_primary_container_dark = Color(0xFFFFDBC8)

val secondary_dark = Color(0xFFE0C2A3)
val on_secondary_dark = Color(0xFF3D2B15)
val secondary_container_dark = Color(0xFF54402A)
val on_secondary_container_dark = Color(0xFFF6DFC2)

val tertiary_dark = Color(0xFFBFD18A)
val on_tertiary_dark = Color(0xFF2C360F)
val tertiary_container_dark = Color(0xFF434E11)
val on_tertiary_container_dark = Color(0xFFDBED9F)

val background_dark = Color(0xFF17120E)
val on_background_dark = Color(0xFFEEE0D3)
val surface_dark = Color(0xFF17120E)
val on_surface_dark = Color(0xFFEEE0D3)
val surface_variant_dark = Color(0xFF4A4136)
val on_surface_variant_dark = Color(0xFFD5C3B8)

val error_dark = Color(0xFFF2B8B5)
val on_error_dark = Color(0xFF601410)
val error_container_dark = Color(0xFF8C1D18)
val on_error_container_dark = Color(0xFFF9DEDC)

val outline_dark = Color(0xFFA08D80)
val outline_variant_dark = Color(0xFF52453B)
val scrim_dark = Color(0xFF000000)

val surface_tint_dark = Color(0xFFFFB687)
val inverse_surface_dark = Color(0xFFEFE2D6)
val inverse_on_surface_dark = Color(0xFF2B2218)
val inverse_primary_dark = Color(0xFFA84B17)

val surface_dim_dark = Color(0xFF110D09)
val surface_bright_dark = Color(0xFF4B443C)
val surface_container_lowest_dark = Color(0xFF0F0C09)
val surface_container_low_dark = Color(0xFF1E1814)
val surface_container_dark = Color(0xFF231C17)
val surface_container_high_dark = Color(0xFF2E2620)
val surface_container_highest_dark = Color(0xFF38312A)
