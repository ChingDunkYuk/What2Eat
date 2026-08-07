package com.what2eat.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.what2eat.domain.search.SearchPlatform

/**
 * 通用"去找餐厅"平台承接底部面板。
 *
 * Stage 3.2：大众点评 / 美团 / 地图 / 浏览器 / 复制关键词 统一复用此组件。
 * UI 仅暴露平台枚举，不直接处理包名、URI、Intent 异常 —— 具体承接由
 * PlatformSearchLauncher 完成。
 *
 * @param query 搜索关键词
 * @param onDismiss 关闭面板
 * @param onPlatformSearch 平台承接（DIANPING / MEITUAN / MAP / BROWSER）
 * @param onCopySearch 复制关键词
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSearchSheet(
    query: String,
    onDismiss: () -> Unit,
    onPlatformSearch: (SearchPlatform) -> Unit,
    onCopySearch: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "去找餐厅",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "搜索关键词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = query,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 平台快捷入口（大众点评 / 美团）
            PlatformButton(
                label = "大众点评",
                icon = Icons.Outlined.Search,
                onClick = { onPlatformSearch(SearchPlatform.DIANPING) }
            )
            PlatformButton(
                label = "美团",
                icon = Icons.Outlined.Search,
                onClick = { onPlatformSearch(SearchPlatform.MEITUAN) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 通用搜索
            PlatformButton(
                label = "地图搜索",
                icon = Icons.Outlined.Map,
                onClick = { onPlatformSearch(SearchPlatform.MAP) }
            )
            PlatformButton(
                label = "浏览器搜索",
                icon = Icons.Outlined.Language,
                onClick = { onPlatformSearch(SearchPlatform.BROWSER) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onCopySearch,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text("复制关键词", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PlatformButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}