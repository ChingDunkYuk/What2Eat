package com.what2eat.core.designsystem.icon

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * 返回箭头（自定义可爱版，RTL 自动镜像）。
 *
 * 替代 Icons.AutoMirrored.Outlined.ArrowBack：
 * 自定义 ImageVector 不带 AutoMirrored 语义，这里用 graphicsLayer 在 RTL 布局下水平翻转补回。
 */
@Composable
fun What2EatBackIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        imageVector = What2EatIcons.ArrowBack,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer { scaleX = if (mirrored) -1f else 1f },
        tint = tint
    )
}
