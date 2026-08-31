package com.what2eat.feature.preference

import com.what2eat.core.designsystem.icon.What2EatIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.R

/**
 * 偏好设置页面。
 *
 * 按一级分类分组显示具体餐饮分类，
 * 每个分类支持五档偏好等级和独立的长期硬排除开关。
 * 支持搜索和筛选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceScreen(
    onBack: () -> Unit,
    viewModel: PreferenceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val personName = uiState.person?.name ?: "用户"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${personName}的饮食偏好") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = What2EatIcons.Close,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 搜索栏 ──
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.preference_search_hint)) },
                leadingIcon = { Icon(What2EatIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(What2EatIcons.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true
            )

            // ── 筛选 Chips ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.filter == PreferenceFilter.ALL,
                    onClick = { viewModel.setFilter(PreferenceFilter.ALL) },
                    label = { Text(stringResource(R.string.preference_filter_all)) }
                )
                FilterChip(
                    selected = uiState.filter == PreferenceFilter.SET_ONLY,
                    onClick = { viewModel.setFilter(PreferenceFilter.SET_ONLY) },
                    label = { Text(stringResource(R.string.preference_filter_set)) }
                )
                FilterChip(
                    selected = uiState.filter == PreferenceFilter.EXCLUDED_ONLY,
                    onClick = { viewModel.setFilter(PreferenceFilter.EXCLUDED_ONLY) },
                    label = { Text(stringResource(R.string.preference_filter_excluded)) }
                )
            }

            // ── 分类列表 ──
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.preference_loading))
                }
            } else if (uiState.categoryGroups.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.preference_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.categoryGroups) { group ->
                        PreferenceGroupCard(group, viewModel)
                    }
                }
            }
        }
    }
}

/**
 * 一级分类分组卡片，包含子分类列表。
 */
@Composable
private fun PreferenceGroupCard(
    group: CategoryPreferenceGroup,
    viewModel: PreferenceViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = group.rootCategory.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            group.items.forEach { item ->
                PreferenceItemRow(item, viewModel)
            }
        }
    }
}

/**
 * 单个分类偏好行。
 * 包含分类名称、五档偏好选择和硬排除开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferenceItemRow(
    item: CategoryPreferenceItem,
    viewModel: PreferenceViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isHardExcluded) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (item.isHardExcluded) FontWeight.Bold else FontWeight.Normal
            )

            // 硬排除开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = What2EatIcons.Block,
                    contentDescription = null,
                    tint = if (item.isHardExcluded) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.padding(end = 2.dp)
                )
                Text(
                    text = stringResource(R.string.preference_hard_exclude),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isHardExcluded) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Switch(
                    checked = item.isHardExcluded,
                    onCheckedChange = { excluded ->
                        viewModel.setHardExcluded(item.category.id, excluded)
                    }
                )
            }
        }

        // 硬排除时禁用偏好选择（心情脸小表情：大哭 → 眯眼大笑）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            MoodFaceSelector(
                selectedLevel = item.preferenceLevel,
                enabled = !item.isHardExcluded,
                onLevelChange = { level ->
                    viewModel.setPreferenceLevel(item.category.id, level)
                }
            )
        }

        // 硬排除时显示提示
        if (item.isHardExcluded) {
            Text(
                text = stringResource(R.string.preference_hard_excluded_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
