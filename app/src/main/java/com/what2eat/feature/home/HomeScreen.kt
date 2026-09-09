package com.what2eat.feature.home

import com.what2eat.core.designsystem.animation.bounceClickable
import com.what2eat.core.designsystem.animation.entranceBounce
import com.what2eat.core.designsystem.animation.gentleBob
import com.what2eat.core.designsystem.icon.What2EatIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.R

/**
 * 首页。
 *
 * 显示主要用户名称问候 + 主入口卡片。
 * 有未完成决策时显示"继续上次决定"入口。
 */
@Composable
fun HomeScreen(
    onDecideFirstClick: () -> Unit,
    onPoolDecideClick: () -> Unit,
    onContinueSessionClick: () -> Unit = {},
    onReviewClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 问候区域 ──
        val greeting = if (uiState.hasUserName) {
            stringResource(R.string.home_greeting, uiState.primaryUserName!!)
        } else {
            stringResource(R.string.home_greeting_default)
        }

        // 随机小文案：每次进入首页换一句，增添活泼感
        val defaultSubtitle = stringResource(R.string.home_subtitle)
        val subtitle = remember(defaultSubtitle) {
            listOf(
                defaultSubtitle,
                "今天也想一起好好吃饭",
                "选择困难？交给我就好啦",
                "美味不等人，开饭啦",
                "干饭人的快乐从决定开始"
            ).random()
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = What2EatIcons.Mascot,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(40.dp)
                    .gentleBob()
            )
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 交错入场：按可见顺序依次弹出
        var nextEntranceDelay = 0

        // ── 继续本次决定（有活动会话时显示）──
        if (uiState.hasActiveSession) {
            val candidateDesc = if (uiState.candidateCount > 0) {
                "已有 ${uiState.candidateCount} 个候选"
            } else {
                "你有一个未完成的选择"
            }
            EntryCard(
                icon = What2EatIcons.PlayArrow,
                title = "继续本次决定",
                description = candidateDesc,
                onClick = onContinueSessionClick,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                entranceDelayMillis = nextEntranceDelay
            )
            nextEntranceDelay += 60
        }

        // ── 待整理提醒（v0.9.0：分享收件堆积会退出池决策，主动提醒整理）──
        if (uiState.needsReviewCount > 0) {
            EntryCard(
                icon = What2EatIcons.PlayArrow,
                title = "待整理 · ${uiState.needsReviewCount} 家店",
                description = "整理好才会进入吃饭池参与推荐",
                onClick = onReviewClick,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                entranceDelayMillis = nextEntranceDelay
            )
            nextEntranceDelay += 60
        }

        // ── 主入口卡片 ──
        EntryCard(
            icon = What2EatIcons.Category,
            title = stringResource(R.string.home_entry_decide_first),
            description = stringResource(R.string.home_entry_decide_first_desc),
            onClick = onDecideFirstClick,
            entranceDelayMillis = nextEntranceDelay
        )
        nextEntranceDelay += 60

        EntryCard(
            icon = What2EatIcons.Restaurant,
            title = stringResource(R.string.home_entry_pool),
            description = stringResource(R.string.home_entry_pool_desc),
            onClick = onPoolDecideClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            entranceDelayMillis = nextEntranceDelay
        )
    }
}

/**
 * 主入口卡片组件。
 *
 * v0.9.4：入场弹跳（交错延迟）+ 按压缩放回弹。
 */
@Composable
private fun EntryCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
    entranceDelayMillis: Int = 0
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .entranceBounce(key = title, delayMillis = entranceDelayMillis)
            .bounceClickable(onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(contentColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
