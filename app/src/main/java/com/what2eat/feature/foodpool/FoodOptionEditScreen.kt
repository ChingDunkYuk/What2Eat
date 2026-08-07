package com.what2eat.feature.foodpool

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOptionType

/**
 * 新增/编辑吃饭选项页。
 *
 * 底部固定「取消 / 保存」；名称为空时保存禁用；未保存返回时提示是否放弃；
 * 表单可滚动 + imePadding，键盘不遮挡当前字段；输入法「下一项」跳转下一个字段。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodOptionEditScreen(
    optionId: String?,
    onBack: () -> Unit,
    viewModel: FoodOptionEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(optionId) {
        viewModel.load(optionId)
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    // 返回（系统返回键 / 顶栏箭头 / 取消按钮统一走这里）；有修改则先提示放弃
    val requestBack: () -> Unit = {
        if (state.hasChanges) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = true) { requestBack() }

    // 键盘「下一项」焦点链
    val nameFocus = remember { FocusRequester() }
    val tagsFocus = remember { FocusRequester() }
    val areaFocus = remember { FocusRequester() }
    val minutesFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (optionId == null) "添加吃饭选项" else "编辑吃饭选项") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = requestBack,
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave && !state.isSaving,
                        modifier = Modifier.weight(1f)
                    ) { Text("保存") }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { tagsFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus)
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { areaFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(tagsFocus)
            )

            // 区域
            OutlinedTextField(
                value = state.areaText,
                onValueChange = viewModel::onAreaChange,
                label = { Text("区域") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { minutesFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(areaFocus)
            )

            // 预计用时（分钟）
            OutlinedTextField(
                value = state.estimatedMinutes,
                onValueChange = viewModel::onMinutesChange,
                label = { Text("预计用时（分钟）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { urlFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(minutesFocus)
            )

            // 原始链接
            OutlinedTextField(
                value = state.sourceUrl,
                onValueChange = viewModel::onUrlChange,
                label = { Text("原始链接") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { notesFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(urlFocus)
            )

            // 备注
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("备注") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(notesFocus)
            )

            // 是否启用
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = { viewModel.toggleEnabled() })
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃修改？") },
            text = { Text("未保存的修改将丢失，确定放弃吗？") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("放弃")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("取消") }
            }
        )
    }
}