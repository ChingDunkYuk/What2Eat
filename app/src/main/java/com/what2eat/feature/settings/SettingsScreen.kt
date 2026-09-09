package com.what2eat.feature.settings

import com.what2eat.BuildConfig
import com.what2eat.core.designsystem.icon.What2EatIcons

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.R
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonProfile
import kotlinx.coroutines.delay

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
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val meituanLoggedIn by viewModel.meituanLoggedIn.collectAsStateWithLifecycle()

    // v1.1.0：从美团登录页返回时刷新登录态显示
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshMeituanLogin()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 编辑对话框状态
    var editingPerson by remember { mutableStateOf<PersonProfile?>(null) }

    // ── v0.9.3：备份/恢复 SAF 文件选择器 ──
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importFrom) }

    // 操作提示短暂显示后自动消失
    LaunchedEffect(backupState.message) {
        if (backupState.message != null) {
            delay(3200L)
            viewModel.consumeBackupMessage()
        }
    }

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
                    imageVector = What2EatIcons.WarningAmber,
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
            backupState = backupState,
            onExportClick = { exportLauncher.launch(viewModel.defaultBackupFileName()) },
            onImportClick = {
                // mime 放宽：部分文件管理器对 .json 报 octet-stream/text/plain
                importLauncher.launch(
                    arrayOf("application/json", "application/octet-stream", "text/plain")
                )
            },
            meituanLoggedIn = meituanLoggedIn,
            onMeituanLoginClick = viewModel::openMeituanLogin,
            onMeituanLogoutClick = viewModel::logoutMeituan,
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

    // ── v0.9.3：导入确认对话框（覆盖警告）──
    backupState.pendingImport?.let { pending ->
        val s = pending.summary
        AlertDialog(
            onDismissRequest = { if (!backupState.isWorking) viewModel.dismissImportPreview() },
            icon = { Icon(What2EatIcons.WarningAmber, contentDescription = null) },
            title = { Text("恢复这份备份？") },
            text = {
                Text(
                    "备份内容：${s.profileCount} 位人物、${s.optionCount} 家店、" +
                        "${s.tagCount} 个标签、${s.sessionCount} 条决定历史。\n\n" +
                        "导入将覆盖当前全部数据，且无法撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmImport,
                    enabled = !backupState.isWorking
                ) {
                    Text("覆盖恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissImportPreview,
                    enabled = !backupState.isWorking
                ) { Text("取消") }
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
    backupState: BackupUiState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    meituanLoggedIn: Boolean,
    onMeituanLoginClick: () -> Unit,
    onMeituanLogoutClick: () -> Unit,
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

        // ── v1.1.0：美团登录（可选，提升店名识别）──
        MeituanLoginSection(
            loggedIn = meituanLoggedIn,
            onLoginClick = onMeituanLoginClick,
            onLogoutClick = onMeituanLogoutClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── 备份与恢复（v0.9.3） ──
        BackupSection(
            backupState = backupState,
            onExportClick = onExportClick,
            onImportClick = onImportClick
        )

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
            // v0.9.0：从 BuildConfig 读取（此前双处硬编码，v0.8.9~v0.8.17 九个版本
            // 忘记同步设置页，一直显示 0.8.8）
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ── 主用户修复对话框（情况 B：有档案但无主用户）──
    if (uiState.needsPrimarySelection && uiState.candidatesForSelection.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(What2EatIcons.WarningAmber, contentDescription = null) },
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
 * v1.1.0：美团登录区块（可选，实验性）。
 *
 * v1.2.5 自查：登录会话只落 passport 域、H5 子域会话需独立 SSO（实测子域
 * 接口仍「用户未登陆」），且 yoda 风控墙/H5guard 签名与登录态无关——
 * 对店名识别帮助有限；滑块验证人工通过才是主通道。文案不夸大。
 */
@Composable
private fun MeituanLoginSection(
    loggedIn: Boolean,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Text(
        text = "美团登录",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = if (loggedIn) {
            "已登录美团通行证 · 注意：子域会话不共享，对店名识别帮助有限"
        } else {
            "实验性：美团 H5 各子域登录会话不互通，登录对店名识别帮助有限；识别以滑块验证为主，失败可手动输入"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (loggedIn) {
        OutlinedButton(onClick = onLogoutClick, modifier = Modifier.fillMaxWidth()) {
            Text("退出美团登录")
        }
    } else {
        OutlinedButton(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
            Text("登录美团（实验性）")
        }
    }
}

/**
 * 备份与恢复区块（v0.9.3）。
 *
 * 导出：全量数据 → SAF JSON 文件；导入：SAF 选文件 → 预览确认 → 全量替换。
 */
@Composable
private fun BackupSection(
    backupState: BackupUiState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Text(
        text = "备份与恢复",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "导出全部数据（吃饭池、历史、偏好、人物）为 JSON 文件；换机或重装后可从备份恢复。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    backupState.message?.let { msg ->
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (backupState.isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onExportClick,
            enabled = !backupState.isWorking,
            modifier = Modifier.weight(1f)
        ) {
            if (backupState.isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("导出备份")
        }
        OutlinedButton(
            onClick = onImportClick,
            enabled = !backupState.isWorking,
            modifier = Modifier.weight(1f)
        ) {
            Text("导入备份")
        }
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
                        imageVector = What2EatIcons.Edit,
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
                    imageVector = What2EatIcons.ChevronRight,
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
