package com.what2eat.feature.foodpool

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.core.designsystem.icon.What2EatIcons
import com.what2eat.domain.model.TagPalette
import com.what2eat.domain.model.TagUsage
import kotlinx.coroutines.delay

/**
 * 标签管理面板（v0.9.2）。
 *
 * 列出全部标签（按使用数降序），行内菜单支持重命名与删除；
 * 重命名到已有标签自动合并（同选项重复关联去重）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                            TagRow(
                                tag = tag,
                                colorArgb = uiState.colorByTag[tag.name],
                                viewModel = viewModel
                            )
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

    // ── v1.6.0：色板对话框（8 预设色 + 默认） ──
    val coloring = uiState.coloringTag
    if (coloring != null) {
        val currentColor = uiState.colorByTag[coloring.name]
        AlertDialog(
            onDismissRequest = viewModel::dismissColoring,
            title = { Text("「${coloring.name}」的颜色") },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 默认色（中性灰点 + 斜杠语义：回落未设置）
                    ColorSwatch(
                        color = Color(TagPalette.DEFAULT),
                        selected = currentColor == null,
                        label = "默认",
                        onClick = { viewModel.pickColor(null) }
                    )
                    TagPalette.presets.forEach { preset ->
                        ColorSwatch(
                            color = Color(preset),
                            selected = currentColor == preset,
                            label = null,
                            onClick = { viewModel.pickColor(preset) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissColoring) { Text("关闭") }
            }
        )
    }
}

/** v1.6.0：色板圆点（选中带勾；默认色项显示「默认」小字） */
@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    label: String?,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else Modifier
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = What2EatIcons.Check,
                    contentDescription = "当前颜色",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单个标签行：色点 + 名称 + 使用数 + 行内操作菜单（v1.6.0 色点/颜色入口） */
@Composable
private fun TagRow(
    tag: TagUsage,
    colorArgb: Int?,
    viewModel: TagManageViewModel
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // v1.6.0：色点（有颜色时展示标签色，否则中性灰）
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.outlineVariant)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
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
                    text = { Text("颜色") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.outlineVariant)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        viewModel.startColoring(tag)
                    }
                )
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
