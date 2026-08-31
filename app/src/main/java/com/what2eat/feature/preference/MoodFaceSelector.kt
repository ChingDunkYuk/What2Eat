package com.what2eat.feature.preference

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.what2eat.core.designsystem.icon.What2EatIcons

/**
 * 偏好五档「心情脸」选择器。
 *
 * 从非常不喜欢到非常喜欢，用小表情表示心情：
 * 大哭 😢 / 皱眉 / 平静 / 微笑 / 眯眼大笑 ⭐
 * 选中项高亮为品牌色圆底，下方实时显示当前心情文字。
 */
@Composable
fun MoodFaceSelector(
    selectedLevel: Int,
    enabled: Boolean = true,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        MoodEntry(-2, What2EatIcons.MoodCry, "非常不喜欢"),
        MoodEntry(-1, What2EatIcons.MoodFrown, "不太喜欢"),
        MoodEntry(0, What2EatIcons.MoodNeutral, "无所谓"),
        MoodEntry(1, What2EatIcons.MoodSmile, "喜欢"),
        MoodEntry(2, What2EatIcons.MoodLove, "非常喜欢")
    )
    val selectedLabel = entries.firstOrNull { it.level == selectedLevel }?.label

    Column(modifier = modifier.alpha(if (enabled) 1f else 0.38f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            entries.forEach { entry ->
                val isSelected = selectedLevel == entry.level
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                        .semantics { contentDescription = entry.label }
                        .clickable(enabled = enabled) { onLevelChange(entry.level) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        modifier = Modifier.size(27.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        if (selectedLabel != null) {
            Text(
                text = "现在的心情：$selectedLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp)
            )
        }
    }
}

private data class MoodEntry(
    val level: Int,
    val icon: ImageVector,
    val label: String
)
