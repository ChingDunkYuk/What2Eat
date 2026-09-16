package com.what2eat.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 标签 Chip（v1.6.0 标签颜色）。
 *
 * 全场景统一组件（管理面板/详情页/编辑联想/历史筛选）：
 * 圆角 50 小胶囊 = 色点 + 标签名；colorArgb 为 null（无元数据行/默认色）时
 * 中性底色，与染色前视觉零差异；selected 时以标签色描边。
 * onClick 为 null 时纯展示（不响应点击）。
 *
 * 不基于 FilterChip 着色——避开 M3 chip 复杂色槽，自绘保证四场景观感一致。
 */
@Composable
fun TagChip(
    name: String,
    colorArgb: Int?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val tagColor = colorArgb?.let { Color(it) }
    val container = if (tagColor != null) {
        tagColor.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val border = when {
        selected && tagColor != null -> BorderStroke(1.5.dp, tagColor)
        selected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }

    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(50),
        color = container,
        border = border
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (tagColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tagColor)
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = if (tagColor != null) Modifier.padding(start = 6.dp) else Modifier
            )
        }
    }
}
