package com.what2eat.feature.foodpool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOptionType

/**
 * 新增/编辑吃饭选项页。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodOptionEditScreen(
    optionId: String?,
    onBack: () -> Unit,
    viewModel: FoodOptionEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(optionId) {
        viewModel.load(optionId)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (optionId == null) "添加吃饭选项" else "编辑吃饭选项") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 名称（必填）
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("名称 *") },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 类型（必填）
            Text("类型 *", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SavedOptionType.entries.forEach { t ->
                    FilterChip(
                        selected = state.type == t,
                        onClick = { viewModel.onTypeChange(t) },
                        label = { Text(t.label) }
                    )
                }
            }

            // 所属列表（多选）
            Text("所属列表", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollectionType.entries.forEach { c ->
                    FilterChip(
                        selected = c in state.collections,
                        onClick = { viewModel.toggleCollection(c) },
                        label = { Text(c.label) }
                    )
                }
            }

            // 标签（逗号分隔）
            OutlinedTextField(
                value = state.tags.joinToString("、"),
                onValueChange = { raw ->
                    viewModel.onTagsChange(raw.split("、", ",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                },
                label = { Text("标签（如：火锅、潮汕）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 区域
            OutlinedTextField(
                value = state.areaText,
                onValueChange = viewModel::onAreaChange,
                label = { Text("区域") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 预计用时（分钟）
            OutlinedTextField(
                value = state.estimatedMinutes,
                onValueChange = viewModel::onMinutesChange,
                label = { Text("预计用时（分钟）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 原始链接
            OutlinedTextField(
                value = state.sourceUrl,
                onValueChange = viewModel::onUrlChange,
                label = { Text("原始链接") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 备注
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth()
            )

            // 是否启用
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("启用", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = { viewModel.toggleEnabled() })
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}