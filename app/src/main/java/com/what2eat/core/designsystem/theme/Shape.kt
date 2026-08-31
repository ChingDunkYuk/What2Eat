package com.what2eat.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * What2Eat 形状体系 —— 温馨圆润风。
 *
 * - extraSmall(8dp)：OutlinedTextField
 * - small(12dp)：FilterChip
 * - medium(16dp)：Card
 * - large(24dp)：FAB（近圆）
 * - extraLarge(28dp)：AlertDialog / ModalBottomSheet
 */
val What2EatShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
