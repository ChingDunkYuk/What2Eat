package com.what2eat.feature.shareimport

import com.what2eat.core.designsystem.icon.What2EatBackIcon

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.core.designsystem.icon.What2EatIcons
import com.what2eat.data.share.FetchDebugLog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.what2eat.VerifyPassActivity
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.share.DuplicateCheckResult
import com.what2eat.domain.share.ShareImportDefaults
import kotlinx.coroutines.delay

/**
 * 分享收件确认页（收件箱模式）。
 *
 * 从美团/大众点评等分享进来的内容，一键收进吃饭池的「待整理」：
 * 名称/类型能解析就自动填，解析不出也不阻塞（用占位名兜底）。
 * 之后再进入吃饭池的「待整理」里慢慢整理，整理保存后自动归入正式池。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShareImportScreen(
    rawText: String?,
    subject: String?,
    sourcePackage: String?,
    onCancel: () -> Unit,
    onViewDetail: (String) -> Unit,
    onDone: () -> Unit,
    viewModel: ShareImportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.init(rawText, subject, sourcePackage)
    }

    // 成功页
    if (state.saved) {
        ShareImportSuccessScreen(
            name = state.name,
            onViewDetail = { state.savedOptionId?.let(onViewDetail) },
            onDone = onDone
        )
        return
    }

    // 不支持/空数据
    if (state.unsupported || state.draft == null) {
        UnsupportedScreen(onCancel = onCancel)
        return
    }

    val draft = state.draft!!
    val fallbackName = ShareImportDefaults.fallbackName(draft.detectedPlatform)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收到一条分享") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        What2EatBackIcon(contentDescription = "返回")
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
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = { viewModel.save() },
                        enabled = state.canSave && !state.isSaving,
                        modifier = Modifier.weight(1.4f)
                    ) { Text("收进待整理") }
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
            // 收件提示
            Text(
                "来自${draft.detectedPlatform.label}的内容已解析，先收进「待整理」，稍后再整理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 名称（可留空，自动兜底占位名）
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("名称（可稍后改）") },
                placeholder = { Text(fallbackName) },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } }
                    ?: when {
                        // 后台正在抓链接标题补店名（美团分享文字不带店名，
                        // 只能靠链接抓取，多候选最坏约 30 秒；不打断手动输入）
                        state.isResolvingName -> { { Text("正在从链接识别店名（约30秒），也可现在直接输入") } }
                        // 抓取已结束仍为空 → 美团反爬墙拦截，明确引导手动输入
                        state.name.isBlank() -> {
                            { Text("未能自动获取店名（美团限制），请直接输入；留空将用「$fallbackName」") }
                        }
                        else -> null
                    },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // v1.1.0：抓取诊断面板（无 adb 排障）——店名拿不到时展开看抓取链走到哪步
            FetchDebugPanel(isResolvingName = state.isResolvingName)

            // 类型（可留空，默认按来源推断）
            Text("类型（可稍后改）", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 仅餐厅/外卖/在家做（导入场景）
                listOf(
                    SavedOptionType.RESTAURANT,
                    SavedOptionType.TAKEOUT_STORE,
                    SavedOptionType.HOME_MEAL
                ).forEach { t ->
                    FilterChip(
                        selected = state.type == t,
                        onClick = { viewModel.onTypeChange(t) },
                        label = { Text(t.label) }
                    )
                }
            }

            // 所属列表
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

            // 标签
            OutlinedTextField(
                value = state.tags.joinToString("、"),
                onValueChange = { raw ->
                    viewModel.onTagsChange(
                        raw.split("、", ",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    )
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 重复提示
    val duplicate = state.duplicate
    if (duplicate != null) {
        AlertDialog(
            onDismissRequest = viewModel::consumeDuplicate,
            title = { Text("可能已经保存过") },
            text = {
                Column {
                    Text(duplicate.existing.name)
                    Text(
                        "当前在：" + duplicate.collections.joinToString { it.label },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeDuplicate(); viewModel.save(stillSaveDuplicate = true) }) {
                    Text("仍然保存")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::consumeDuplicate) { Text("取消") }
            }
        )
    }

    // v1.2.3：yoda 验证墙人工通过（全屏 Activity + 默认系统 UA——Dialog 内嵌 +
    // 微信 UA 会触发 yoda 反自动化破坏，滑块故意渲染残缺；通过后自动重试抓取）
    val context = LocalContext.current
    val verifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // v1.2.4：通过后落地店铺页可直接带回店名（非空则免重试）
            viewModel.onVerifyPassed(result.data?.getStringExtra(VerifyPassActivity.EXTRA_SHOP_NAME))
        } else {
            viewModel.onVerifyDismissed()
        }
    }
    LaunchedEffect(state.verifyUrl) {
        state.verifyUrl?.let { url ->
            verifyLauncher.launch(
                Intent(context, VerifyPassActivity::class.java)
                    .putExtra(VerifyPassActivity.EXTRA_PAGE_URL, url)
            )
        }
    }
}

/**
 * v1.1.0：抓取诊断面板（无 adb 排障）。
 *
 * 店名拿不到时点「抓取诊断」展开，实时显示抓取链日志（代理响应码/cookie/
 * 数据接口 URL/JS 回调/护栏判定）。截图发回即可远程定位。
 * 抓取中每 800ms 刷新，结束后定格。
 */
@Composable
private fun FetchDebugPanel(isResolvingName: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(FetchDebugLog.snapshot()) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // 抓取中或面板展开时定时刷新日志快照
    LaunchedEffect(expanded, isResolvingName) {
        while (expanded || isResolvingName) {
            logs = FetchDebugLog.snapshot()
            delay(800)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "收起抓取诊断 ▲" else "抓取诊断 ▼（店名识别失败时点我）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        "暂无日志",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    logs.takeLast(20).forEach { line ->
                        Text(
                            text = line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                            copied = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(if (copied) "已复制，粘贴发我" else "复制全部日志")
                    }
                }
            }
        }
    }
}

/** 不支持的动作类型提示。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnsupportedScreen(onCancel: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收到一条分享") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        What2EatBackIcon(contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("不支持此类型的分享内容", style = MaterialTheme.typography.titleMedium)
            Text("仅支持文本分享（text/plain）。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onCancel) { Text("返回") }
        }
    }
}

/** 收件成功页：吉祥物 + 去整理入口。 */
@Composable
private fun ShareImportSuccessScreen(
    name: String,
    onViewDetail: () -> Unit,
    onDone: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Icon(
                imageVector = What2EatIcons.Mascot,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(96.dp)
            )
            Text("已收进待整理", style = MaterialTheme.typography.headlineSmall)
            Text(
                "「$name」先放在吃饭池的待整理里，\n有空时整理一下就能用啦。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onViewDetail, modifier = Modifier.fillMaxWidth()) {
                Text("去整理")
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
        }
    }
}