package com.what2eat.feature.foodpool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.core.designsystem.icon.What2EatIcons
import com.what2eat.domain.model.TagUsage
import kotlinx.coroutines.delay

/**
 * 标签管理面板（v0.9.2）。
 *
 * 列出全部标签（按使用数降序），行内菜单支持重命名与删除；
 * 重命名到已有标签自动合并（同选项重复关联去重）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageSheet(
    onDismiss: () -> Unit,
    viewModel: TagManageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 操作结果提示：短暂显示后自动消失
    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            delay(2400L)
            viewModel.consumeMessage()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onSheetDismissed()
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "标签管理",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "按使用次数排序；重命名到已有标签会自动合并",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            uiState.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.tags.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "还没有标签\n在店铺编辑页添加后，这里可统一管理",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.tags, key = { it.name }) { tag ->
                            TagRow(tag = tag, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // ── 重命名对话框 ──
    val renaming = uiState.renamingTag
    if (renaming != null) {
        val input = uiState.renameInput.trim()
        val targetExists = uiState.tags.any { it.name == input && input != renaming.name }
        AlertDialog(
            onDismissRequest = viewModel::dismissRename,
            title = { Text("重命名标签") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.renameInput,
                        onValueChange = viewModel::onRenameInput,
                        label = { Text("标签名") },
                        singleLine = true,
                        isError = input.isEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (targetExists) {
                        Text(
                            text = "「$input」已存在，保存后将合并为一个标签",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRename,
                    enabled = input.isNotEmpty()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRename) { Text("取消") }
            }
        )
    }

    // ── 删除确认对话框 ──
    val deleting = uiState.deletingTag
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("删除标签") },
            text = {
                Text("移除「${deleting.name}」？将解除 ${deleting.usageCount} 家店与该标签的关联，店铺本身不受影响。")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
            }
        )
    }
}

/** 单个标签行：名称 + 使用数 + 行内操作菜单 */
@Composable
private fun TagRow(
    tag: TagUsage,
    viewModel: TagManageViewModel
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${tag.usageCount} 家店",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = What2EatIcons.MoreVert,
                    contentDescription = "标签操作"
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    leadingIcon = { Icon(What2EatIcons.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        viewModel.startRename(tag)
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { Icon(What2EatIcons.Block, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        viewModel.requestDelete(tag)
                    }
                )
            }
        }
    }
}
