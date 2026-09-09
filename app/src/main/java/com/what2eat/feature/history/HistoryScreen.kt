package com.what2eat.feature.history

import com.what2eat.core.designsystem.icon.What2EatIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.R
import com.what2eat.domain.history.HistoryStats
import com.what2eat.feature.common.PlatformSearchSheet

/**
 * 历史页面。
 *
 * Stage 2.2 封版：最小历史展示（最终分类、完成时间、decisionMode、参与人物）。
 * Stage 3.2：新增"再次搜索"按钮，复用决策完成页同一套平台承接 BottomSheet。
 * v0.8.1：顶部统计卡（总次数/本月/平均换一个/最常吃 Top5）+ 按日分组时间线；
 *         移除卡片上的 sessionId 调试信息。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 展示平台承接结果提示
    LaunchedEffect(uiState.searchMessage) {
        val msg = uiState.searchMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSearchMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "加载历史记录…",
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.isEmpty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Icon(
                            imageVector = What2EatIcons.History,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(top = 24.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                        Text(
                            text = stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "title") {
                            Text(
                                text = stringResource(R.string.history_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // ── v0.8.1：统计卡 ──
                        uiState.stats?.let { stats ->
                            item(key = "stats") {
                                StatsCard(stats = stats)
                            }
                        }

                        // ── v0.8.1：按日分组时间线 ──
                        uiState.groups.forEach { group ->
                            item(key = "header_${group.label}") {
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(group.items, key = { it.sessionId }) { item ->
                                HistoryCard(
                                    item = item,
                                    onSearchAgain = { viewModel.showSearchPanel(item.categoryName, item.areaText) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Stage 3.2：再次搜索平台承接面板（复用统一组件）
    if (uiState.showSearchPanel) {
        PlatformSearchSheet(
            query = uiState.searchQuery,
            onDismiss = viewModel::hideSearchPanel,
            onPlatformSearch = viewModel::onPlatformSearch,
            onCopySearch = viewModel::onCopySearch
        )
    }
}

/**
 * v0.8.1 统计卡：总决定 / 本月决定 / 平均换一个 + 最常吃 Top 5。
 */
@Composable
private fun StatsCard(stats: HistoryStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = What2EatIcons.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "吃饭小统计",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // 三个统计数字
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatBlock(
                    value = stats.totalDecisions.toString(),
                    label = "总决定",
                    modifier = Modifier.weight(1f)
                )
                StatBlock(
                    value = stats.thisMonthDecisions.toString(),
                    label = "本月",
                    modifier = Modifier.weight(1f)
                )
                StatBlock(
                    value = formatReroll(stats.averageRerollCount),
                    label = "平均换一个",
                    modifier = Modifier.weight(1f)
                )
            }

            // 最常吃 Top 5
            if (stats.topFoods.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f)
                )
                Text(
                    text = "最常吃",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                stats.topFoods.forEachIndexed { index, entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = What2EatIcons.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${index + 1}. ${entry.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "×${entry.count}次",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/** 单个统计数字块 */
@Composable
private fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** 平均换一个格式化：整数省略小数，非整数保留 1 位 */
private fun formatReroll(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        "${value.toLong()}次"
    } else {
        "${value}次"
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    onSearchAgain: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = What2EatIcons.Restaurant,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "完成时间：${item.completedAtText}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "模式：${item.decisionModeText}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "参与人物：${item.participants.joinToString("、")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Stage 3.2：再次搜索
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSearchAgain) {
                    Icon(
                        imageVector = What2EatIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("再次搜索", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
