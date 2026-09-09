package com.what2eat.feature.pooldecision

import com.what2eat.core.designsystem.animation.bouncyPress
import com.what2eat.core.designsystem.animation.entranceBounce
import com.what2eat.core.designsystem.animation.gentleBob
import com.what2eat.core.designsystem.icon.What2EatBackIcon
import com.what2eat.core.designsystem.icon.What2EatIcons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.domain.engine.MatchLevel
import com.what2eat.domain.engine.PoolReasonType
import com.what2eat.domain.model.MealMode
import com.what2eat.feature.common.PlatformSearchSheet

/**
 * 「从吃饭池决定」页面。
 *
 * 轻量单页流程：列表/用餐方式筛选 → 加权随机推荐 → 换一个 / 就吃这个 → 完成态搜索承接。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolDecisionScreen(
    onExit: () -> Unit,
    onGoToPool: () -> Unit,
    viewModel: PoolDecisionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.searchMessage) {
        uiState.searchMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSearchMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("从吃饭池决定") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        What2EatBackIcon(contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                uiState.isLoading -> LoadingContent()

                uiState.poolIsEmpty -> EmptyPoolContent(onGoToPool = onGoToPool)

                else -> {
                    // ── 筛选区 ──
                    FilterSection(uiState = uiState, viewModel = viewModel)

                    // ── 结果区 ──
                    when {
                        uiState.isExhausted -> ExhaustedContent(
                            onReset = viewModel::resetRejected
                        )

                        uiState.recommendation == null -> NoMatchContent(
                            onAdjust = viewModel::resetLists
                        )

                        else -> RecommendationContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            onExit = onExit
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 完成态：找找这家店
    if (uiState.showSearchPanel) {
        PlatformSearchSheet(
            query = uiState.searchQuery,
            onDismiss = viewModel::hideSearchPanel,
            onPlatformSearch = viewModel::onPlatformSearch,
            onCopySearch = viewModel::onCopySearch
        )
    }
}

// ── 筛选区 ──

@Composable
private fun FilterSection(
    uiState: PoolDecisionUiState,
    viewModel: PoolDecisionViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "从哪些列表挑",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(PoolListFilter.entries) { filter ->
                FilterChip(
                    selected = filter in uiState.selectedLists,
                    onClick = { viewModel.toggleList(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        Text(
            text = "今天怎么吃",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(mealModeOptions) { mode ->
                FilterChip(
                    selected = uiState.mealMode == mode,
                    onClick = { viewModel.setMealMode(mode) },
                    label = { Text(mode.label) }
                )
            }
        }
    }
}

private val mealModeOptions = listOf(
    MealMode.ANY,
    MealMode.DINE_OUT,
    MealMode.TAKEOUT,
    MealMode.PACK,
    MealMode.COOK_HOME
)

// ── 结果区 ──

@Composable
private fun RecommendationContent(
    uiState: PoolDecisionUiState,
    viewModel: PoolDecisionViewModel,
    onExit: () -> Unit
) {
    val recommendation = uiState.recommendation ?: return
    val option = uiState.currentOption ?: return

    // 结果卡（v0.9.4：换一个后随 option.id 变化重新弹跳出场）
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .entranceBounce(key = option.id),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = What2EatIcons.RestaurantMenu,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        text = option.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(option.optionType.label)
                            uiState.currentCollections.forEach { append(" · ${it.label}") }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 匹配度 + 上次选择
            Text(
                text = buildString {
                    append(matchLevelLabel(recommendation.matchLevel))
                    append(" · ")
                    append(lastChosenText(option.lastChosenAt))
                },
                style = MaterialTheme.typography.bodyMedium
            )

            // 推荐原因
            if (recommendation.reasons.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recommendation.reasons.take(3).forEach { reason ->
                        Text(
                            text = reasonText(reason),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .padding(horizontal = 2.dp)
                        )
                    }
                }
            }

            // 备注
            if (!option.notes.isNullOrBlank()) {
                Text(
                    text = option.notes!!,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            // 完成态提示
            if (uiState.isConfirmed) {
                Text(
                    text = "已记入历史，今天就吃这个吧！",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // 底部操作（v0.9.4：按钮按压弹性）
    if (!uiState.isConfirmed) {
        val rerollInteraction = remember { MutableInteractionSource() }
        val confirmInteraction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::reroll,
                interactionSource = rerollInteraction,
                modifier = Modifier
                    .weight(1f)
                    .bouncyPress(rerollInteraction)
            ) {
                Icon(
                    imageVector = What2EatIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text("换一个", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = viewModel::confirmPick,
                interactionSource = confirmInteraction,
                modifier = Modifier
                    .weight(1f)
                    .bouncyPress(confirmInteraction)
            ) {
                Icon(
                    imageVector = What2EatIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text("就吃这个", modifier = Modifier.padding(start = 8.dp))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::showSearchPanel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = What2EatIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text("找找这家店", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = What2EatIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text("完成", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

// ── 空态 / 耗尽态 ──

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyPoolContent(onGoToPool: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = What2EatIcons.Mascot,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier
                .size(72.dp)
                .gentleBob()
        )
        Text(
            text = "吃饭池还是空的",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "先把想吃的店收藏进来，再来让饭饭帮你挑",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        val goPoolInteraction = remember { MutableInteractionSource() }
        Button(
            onClick = onGoToPool,
            interactionSource = goPoolInteraction,
            modifier = Modifier.bouncyPress(goPoolInteraction)
        ) {
            Text("去吃饭池看看")
        }
    }
}

@Composable
private fun ExhaustedContent(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = What2EatIcons.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier
                .size(64.dp)
                .gentleBob()
        )
        Text(
            text = "全部换过一遍了",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "要不要重新看看这些选择？",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onReset) {
            Icon(
                imageVector = What2EatIcons.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text("重新开始", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun NoMatchContent(onAdjust: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = What2EatIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier
                .size(64.dp)
                .gentleBob()
        )
        Text(
            text = "当前筛选没有合适的选项",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "试试勾选更多列表，或换一种用餐方式",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onAdjust) {
            Text("恢复默认条件")
        }
    }
}

// ── 文案辅助 ──

private fun matchLevelLabel(level: MatchLevel): String = when (level) {
    MatchLevel.HIGH -> "匹配度很高"
    MatchLevel.MEDIUM -> "匹配度较高"
    MatchLevel.LOW -> "匹配度一般"
}

private fun reasonText(reason: PoolReasonType): String = when (reason) {
    PoolReasonType.POOL_LIKED -> "大家都喜欢"
    PoolReasonType.POOL_NEVER_CHOSEN -> "还没去过"
    PoolReasonType.POOL_RECENTLY_CHOSEN -> "最近刚吃过"
    PoolReasonType.POOL_WANT_TO_TRY -> "在待尝试清单里"
}

private fun lastChosenText(lastChosenAt: Long?): String {
    if (lastChosenAt == null) return "从未选中过"
    val days = (System.currentTimeMillis() - lastChosenAt) / (24L * 60 * 60 * 1000)
    return when {
        days <= 0L -> "今天刚选中过"
        days == 1L -> "昨天选中过"
        days < 30L -> "${days}天前选中过"
        else -> "很久以前选中过"
    }
}
