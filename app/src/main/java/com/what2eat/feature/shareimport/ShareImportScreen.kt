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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.core.designsystem.icon.What2EatIcons
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.share.DuplicateCheckResult
import com.what2eat.domain.share.ShareImportDefaults

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
                        // 后台正在抓链接标题补店名
                        state.isResolvingName -> { { Text("正在识别店名…") } }
                        state.name.isBlank() -> { { Text("未识别到店名，将使用「$fallbackName」") } }
                        else -> null
                    },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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