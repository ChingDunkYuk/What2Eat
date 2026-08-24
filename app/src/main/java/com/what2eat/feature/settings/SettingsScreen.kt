package com.what2eat.feature.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.R
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonProfile

/**
 * 设置页面。
 *
 * 人物档案以卡片形式展示名称、编辑入口和饮食偏好入口（含摘要）。
 * 名称编辑通过对话框完成，不再长期显示输入框和保存按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPreference: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 编辑对话框状态
    var editingPerson by remember { mutableStateOf<PersonProfile?>(null) }

    when {
        // ── 加载中 ──
        uiState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // ── 加载失败 ──
        uiState.hasLoadError -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.settings_load_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = uiState.loadError ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.retryLoad() }) {
                    Text(stringResource(R.string.settings_retry))
                }
            }
        }

        else -> SettingsContent(
            uiState = uiState,
            onNavigateToPreference = onNavigateToPreference,
            onEditName = { editingPerson = it },
            onRepairPrimary = viewModel::repairPrimary,
            viewModel = viewModel
        )
    }

    // ── 名称编辑对话框 ──
    editingPerson?.let { person ->
        EditNameDialog(
            currentName = person.name,
            isSaving = uiState.isSaving,
            onDismiss = { editingPerson = null },
            onSave = { newName ->
                viewModel.saveName(person.id, newName)
                editingPerson = null
            }
        )
    }
}

/**
 * 设置页主体内容。
 */
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onNavigateToPreference: (String) -> Unit,
    onEditName: (PersonProfile) -> Unit,
    onRepairPrimary: (String) -> Unit,
    viewModel: SettingsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 标题 ──
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // ── 使用模式 ──
        Text(
            text = stringResource(R.string.settings_usage_mode),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.usageMode == AppUsageMode.SINGLE,
                onClick = { viewModel.setUsageMode(AppUsageMode.SINGLE) },
                label = { Text(stringResource(R.string.settings_mode_single)) }
            )
            FilterChip(
                selected = uiState.usageMode == AppUsageMode.COUPLE,
                onClick = { viewModel.setUsageMode(AppUsageMode.COUPLE) },
                label = { Text(stringResource(R.string.settings_mode_couple)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 人物档案 ──
        Text(
            text = stringResource(R.string.settings_profiles),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 主用户档案卡片
        uiState.primaryProfile?.let { primary ->
            ProfileCard(
                profile = primary,
                preferenceSummary = uiState.primaryPreferenceSummary,
                onEditClick = { onEditName(primary) },
                onPreferenceClick = { onNavigateToPreference(primary.id) }
            )
        }

        // 第二用户档案卡片（仅双人模式显示）
        if (uiState.usageMode == AppUsageMode.COUPLE) {
            uiState.secondaryProfile?.let { secondary ->
                ProfileCard(
                    profile = secondary,
                    preferenceSummary = uiState.secondaryPreferenceSummary,
                    onEditClick = { onEditName(secondary) },
                    onPreferenceClick = { onNavigateToPreference(secondary.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 关于 ──
        Text(
            text = stringResource(R.string.settings_about),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.settings_about_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.settings_version, "0.7.1"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ── 主用户修复对话框（情况 B：有档案但无主用户）──
    if (uiState.needsPrimarySelection && uiState.candidatesForSelection.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_repair_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_repair_desc))
                    uiState.candidatesForSelection.forEach { candidate ->
                        Button(
                            onClick = { onRepairPrimary(candidate.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(candidate.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

/**
 * 人物档案卡片。
 *
 * 展示：人物名称 + 编辑入口 + 饮食偏好入口（含摘要）。
 */
@Composable
private fun ProfileCard(
    profile: PersonProfile,
    preferenceSummary: PreferenceSummary,
    onEditClick: () -> Unit,
    onPreferenceClick: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 名称行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_edit),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── 饮食偏好入口 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPreferenceClick() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_preference),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildPreferenceSummaryText(preferenceSummary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 构建偏好摘要文本。
 */
@Composable
private fun buildPreferenceSummaryText(summary: PreferenceSummary): String {
    return if (summary.isNotSet) {
        stringResource(R.string.pref_summary_not_set)
    } else {
        val setPart = stringResource(R.string.pref_summary_set, summary.setCount)
        val excludedPart = if (summary.excludedCount > 0) {
            " · " + stringResource(R.string.pref_summary_excluded, summary.excludedCount)
        } else {
            " · " + stringResource(R.string.pref_summary_none_excluded)
        }
        setPart + excludedPart
    }
}

/**
 * 名称编辑对话框。
 *
 * 规则：
 * - 名称不能为空
 * - 自动去除首尾空格
 * - 长度限制 1-20 字符
 * - 名称没有变化时保存按钮禁用
 * - 保存成功后自动关闭
 * - 保存失败时保留编辑内容并显示错误
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNameDialog(
    currentName: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var inputText by remember { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }

    val trimmedInput = inputText.trim()
    val hasChanges = trimmedInput != currentName.trim()
    val isValid = trimmedInput.isNotBlank() && trimmedInput.length <= 20

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.settings_edit_name)) },
        text = {
            Column {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_name_hint)) },
                    isError = error != null,
                    supportingText = {
                        error?.let { errText ->
                            Text(
                                text = errText,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    enabled = !isSaving
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = inputText.trim()
                    when {
                        trimmed.isBlank() -> error = "名称不能为空"
                        trimmed.length > 20 -> error = "名称不能超过20个字符"
                        else -> onSave(trimmed)
                    }
                },
                enabled = hasChanges && isValid && !isSaving
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}
