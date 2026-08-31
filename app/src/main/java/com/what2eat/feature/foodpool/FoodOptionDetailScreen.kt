package com.what2eat.feature.foodpool

import com.what2eat.core.designsystem.icon.What2EatBackIcon

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.domain.model.OptionPreferenceLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 吃饭选项详情页：展示全部字段 + Klaus/晴偏好 + 编辑/停用/删除/打开原链接。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodOptionDetailScreen(
    optionId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: FoodOptionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(optionId) { viewModel.load(optionId) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    // 已加载但数据为空（例如已被删除）→ 直接返回列表，不闪整页加载
    LaunchedEffect(state.loaded, state.option) {
        if (state.loaded && state.option == null) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("吃饭选项详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        What2EatBackIcon(contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        val option = state.option
        if (option == null) {
            if (!state.loaded) {
                Text("加载中…", modifier = Modifier.padding(innerPadding).padding(24.dp))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(option.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            FilterChip(selected = true, onClick = {}, label = { Text(option.optionType.label) })
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("状态：", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (option.enabled) "启用中" else "已停用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (option.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            // 所属列表
            InfoSection(title = "所属列表") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.collections.forEach { Text("· $it") }
                }
            }

            // 标签
            if (state.tags.isNotEmpty()) {
                InfoSection(title = "标签") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tags.forEach { Text("· $it") }
                    }
                }
            }

            // 预算/区域/用时
            InfoSection(title = "信息") {
                option.areaText?.let { Text("区域：$it") }
                option.priceLevel?.let { Text("预算档位：${it}") }
                option.estimatedMinutes?.let { Text("预计用时：${it} 分钟") }
                option.sourcePlatform.let { Text("来源：${it.label}") }
                option.sourceUrl?.let { Text("原始链接：$it") }
            }

            // 备注
            option.notes?.let {
                InfoSection(title = "备注") { Text(it) }
            }

            // 时间
            InfoSection(title = "时间") {
                Text("创建：${fmt(option.createdAt)}")
                option.lastChosenAt?.let { Text("最近选择：${fmt(it)}") }
            }

            HorizontalDivider()

            // 人物偏好
            Text("人物偏好", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.preferences.forEach { pref ->
                PreferenceRow(pref = pref, onLevel = { l -> viewModel.setPreference(pref.personId, l) },
                    onHardExcluded = { v -> viewModel.setHardExcluded(pref.personId, v) })
            }

            HorizontalDivider()

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onEdit(option.id) }) { Text("编辑") }
                if (option.enabled) {
                    TextButton(onClick = viewModel::disable) { Text("停用") }
                } else {
                    TextButton(onClick = viewModel::enable) { Text("启用") }
                }
                TextButton(onClick = { showDeleteConfirm = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
            if (option.sourceUrl != null) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(option.sourceUrl))
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("打开原链接") }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确定删除这个吃饭选项吗？") },
            text = { Text("若该选项已有历史记录，将改为停用而不是删除。") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferenceRow(
    pref: OptionPreferenceUi,
    onLevel: (OptionPreferenceLevel) -> Unit,
    onHardExcluded: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pref.personName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPreferenceLevel.entries.forEach { l ->
                FilterChip(
                    selected = pref.level == l,
                    onClick = { onLevel(l) },
                    label = { Text(l.label) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("长期排除", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = pref.hardExcluded, onCheckedChange = onHardExcluded)
        }
    }
}

private fun fmt(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))