package com.what2eat.feature.foodpool

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOptionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 新增/编辑吃饭选项页。
 *
 * 布局结构（避免重复叠加 imePadding / navigationBarsPadding / contentPadding）：
 * Scaffold
 * ├── TopAppBar
 * ├── Column(verticalScroll)  ← 主体滚动区，单独 imePadding
 * └── Bottom action bar       ← 固定，仅 navigationBarsPadding，不挤占主体
 *
 * 特性：
 * - 底部固定「取消 / 保存」；名称为空 / 保存中禁用保存
 * - 未保存返回弹「放弃修改？」
 * - 键盘弹出时主体可滚动，聚焦字段自动滚入视野（bringIntoView）
 * - 输入法「下一项」在名称→标签→区域→预计用时→原始链接→备注间跳转，最后 Done 收起
 * - 点击空白处收起键盘
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodOptionEditScreen(
    optionId: String?,
    onBack: () -> Unit,
    viewModel: FoodOptionEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(optionId) { viewModel.load(optionId) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    // 返回（系统返回键 / 顶栏箭头 / 取消按钮统一）；有修改则先提示放弃
    val requestBack: () -> Unit = {
        if (state.hasChanges) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = true) { requestBack() }

    // 各输入字段的「聚焦自动滚入视野」句柄
    val nameField = rememberFieldHandle()
    val tagsField = rememberFieldHandle()
    val areaField = rememberFieldHandle()
    val minutesField = rememberFieldHandle()
    val urlField = rememberFieldHandle()
    val notesField = rememberFieldHandle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (optionId == null) "添加吃饭选项" else "编辑吃饭选项") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        // 底部操作栏：仅处理系统导航栏安全区，不做 imePadding（避免键盘弹出时高度膨胀挤占主体）
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
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
        },
        // 关闭 Scaffold 默认系统栏 inset，避免与 TopAppBar / bottomBar 安全区重复叠加
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        // 主体滚动区单独处理 imePadding；点击空白收起键盘
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
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
                keyboardActions = KeyboardActions(onNext = { tagsField.focus.requestFocus() }),
                modifier = Modifier.fillMaxWidth().field(nameField)
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
                    viewModel.onTagsChange(
                        raw.split("、", ",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    )
                },
                label = { Text("标签（如：火锅、潮汕）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { areaField.focus.requestFocus() }),
                modifier = Modifier.fillMaxWidth().field(tagsField)
            )

            // 区域
            OutlinedTextField(
                value = state.areaText,
                onValueChange = viewModel::onAreaChange,
                label = { Text("区域") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { minutesField.focus.requestFocus() }),
                modifier = Modifier.fillMaxWidth().field(areaField)
            )

            // 预计用时（分钟）
            OutlinedTextField(
                value = state.estimatedMinutes,
                onValueChange = viewModel::onMinutesChange,
                label = { Text("预计用时（分钟）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { urlField.focus.requestFocus() }),
                modifier = Modifier.fillMaxWidth().field(minutesField)
            )

            // 原始链接
            OutlinedTextField(
                value = state.sourceUrl,
                onValueChange = viewModel::onUrlChange,
                label = { Text("原始链接") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { notesField.focus.requestFocus() }),
                modifier = Modifier.fillMaxWidth().field(urlField)
            )

            // 备注（末尾字段：Done 收起键盘）
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("备注") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().field(notesField)
            )

            // 是否启用
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = { viewModel.toggleEnabled() })
            }

            Spacer(modifier = Modifier.height(24.dp))
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

/** 输入字段句柄：携带 FocusRequester（键盘跳转）与 BringIntoViewRequester（聚焦自动滚入视野）。 */
@OptIn(ExperimentalFoundationApi::class)
private class FieldHandle(
    val focus: FocusRequester,
    val requester: BringIntoViewRequester,
    val scope: CoroutineScope
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberFieldHandle(): FieldHandle {
    val focus = remember { FocusRequester() }
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return FieldHandle(focus, requester, scope)
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.field(handle: FieldHandle): Modifier =
    this.focusRequester(handle.focus)
        .bringIntoViewRequester(handle.requester)
        .onFocusChanged { if (it.isFocused) handle.scope.launch { handle.requester.bringIntoView() } }