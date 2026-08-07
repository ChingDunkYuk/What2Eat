package com.what2eat.feature.decision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.SelectionType

/**
 * 决策流程主页面。
 * 根据当前 step 渲染不同的步骤内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionFlowScreen(
    onExit: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: DecisionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val stepTitle = when (uiState.step) {
        DecisionStep.PARTICIPANTS -> "选择参与人物"
        DecisionStep.MEAL_MODE -> "用餐方式"
        DecisionStep.MOOD -> "今天的状态"
        DecisionStep.BUDGET -> "预算"
        DecisionStep.DISTANCE -> "距离"
        DecisionStep.HANDOFF -> "交接"
        DecisionStep.CATEGORY_SELECT -> "本次选择"
        DecisionStep.RESULTS -> "候选结果"
    }

    // BackHandler：拦截系统返回键
    BackHandler {
        when (uiState.step) {
            DecisionStep.MEAL_MODE,
            DecisionStep.MOOD,
            DecisionStep.BUDGET,
            DecisionStep.DISTANCE -> {
                viewModel.goBack()
            }
            DecisionStep.PARTICIPANTS -> {
                if (uiState.hasActiveSession) {
                    viewModel.showExitDialog()
                } else {
                    onExit()
                }
            }
            DecisionStep.HANDOFF,
            DecisionStep.CATEGORY_SELECT,
            DecisionStep.RESULTS -> {
                viewModel.showExitDialog()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stepTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (uiState.step) {
                            DecisionStep.MEAL_MODE,
                            DecisionStep.MOOD,
                            DecisionStep.BUDGET,
                            DecisionStep.DISTANCE -> viewModel.goBack()
                            DecisionStep.PARTICIPANTS -> {
                                if (uiState.hasActiveSession) {
                                    viewModel.showExitDialog()
                                } else {
                                    onExit()
                                }
                            }
                            DecisionStep.HANDOFF,
                            DecisionStep.CATEGORY_SELECT,
                            DecisionStep.RESULTS -> viewModel.showExitDialog()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "正在准备你的决策流程",
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.isGeneratingCandidates -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "正在生成候选...",
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.hasError -> {
                    ErrorStateView(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.dismissError() },
                        onExit = onExit
                    )
                }
                else -> when (uiState.step) {
                    DecisionStep.PARTICIPANTS -> ParticipantStep(uiState, viewModel)
                    DecisionStep.MEAL_MODE -> MealModeStep(uiState, viewModel)
                    DecisionStep.MOOD -> MoodStep(uiState, viewModel)
                    DecisionStep.BUDGET -> BudgetStep(uiState, viewModel)
                    DecisionStep.DISTANCE -> DistanceStep(uiState, viewModel)
                    DecisionStep.HANDOFF -> HandoffStep(uiState, viewModel)
                    DecisionStep.CATEGORY_SELECT -> CategorySelectStep(uiState, viewModel)
                    DecisionStep.RESULTS -> ResultsStep(uiState, viewModel, onCompleted)
                }
            }
        }
    }

    // ── Dialogs ──

    if (uiState.showExitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideExitDialog() },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text("退出决策流程") },
            text = { Text("你有一个未完成的决策流程，是否继续？") },
            confirmButton = {
                TextButton(onClick = { viewModel.hideExitDialog() }) {
                    Text("继续本次决定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCancelDialog() }) {
                    Text("放弃本次决定")
                }
            }
        )
    }

    if (uiState.showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideCancelDialog() },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text("确认放弃") },
            text = { Text("放弃后本次决策的所有数据将被删除，无法恢复。确定要放弃吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCancelSession() }) {
                    Text("确认放弃")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCancelDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    if (uiState.showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelNewSession() },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text("已有未完成的决策") },
            text = { Text("你有一个未完成的决策流程。开始新决策将放弃当前流程，确定吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmNewSession() }) {
                    Text("放弃并开始新的")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelNewSession() }) {
                    Text("取消")
                }
            }
        )
    }
}

// ── Error State ──

@Composable
private fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
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
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = "出错了",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onRetry) {
                Text("重试")
            }
            Button(onClick = onExit) {
                Text("返回首页")
            }
        }
    }
}

// ── Step: Participants ──

@Composable
private fun ParticipantStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "选择本次参与决策的人物",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = if (uiState.usageMode == com.what2eat.domain.model.AppUsageMode.SINGLE)
                "单人模式：使用主用户"
            else
                "双人模式：默认勾选两位，可只选其中一位",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        uiState.availableProfiles.forEach { profile ->
            val selected = uiState.selectedParticipantIds.contains(profile.id)
            val isPrimary = profile.isPrimary
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.toggleParticipant(profile.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (profile.isPrimary) {
                            Text(
                                text = "主用户",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.confirmParticipantsAndStart() },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.selectedParticipantIds.isNotEmpty() && !uiState.isSaving
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("开始")
            }
        }
    }
}

// ── Step: Meal Mode ──

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MealModeStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "选择用餐方式（可多选）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MealMode.entries.forEach { mode ->
                val selected = uiState.mealModes.contains(mode)
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleMealMode(mode) },
                    label = { Text(mode.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.goBack() },
                modifier = Modifier.weight(1f)
            ) { Text("上一步") }

            Button(
                onClick = { viewModel.confirmMealMode() },
                modifier = Modifier.weight(1f),
                enabled = uiState.mealModes.isNotEmpty()
            ) { Text("下一步") }
        }
    }
}

// ── Step: Mood ──

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MoodStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "今天的状态（可多选）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MoodTag.entries.forEach { tag ->
                val selected = uiState.moodTags.contains(tag)
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleMoodTag(tag) },
                    label = { Text(tag.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.goBack() },
                modifier = Modifier.weight(1f)
            ) { Text("上一步") }

            Button(
                onClick = { viewModel.confirmMood() },
                modifier = Modifier.weight(1f),
                enabled = uiState.moodTags.isNotEmpty()
            ) { Text("下一步") }
        }
    }
}

// ── Step: Budget ──

@Composable
private fun BudgetStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "选择预算",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        BudgetLevel.entries.forEach { level ->
            val selected = uiState.budgetLevel == level
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.setBudget(level) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(level.label, style = MaterialTheme.typography.bodyLarge)
                    if (selected) {
                        Icon(Icons.Outlined.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.goBack() },
                modifier = Modifier.weight(1f)
            ) { Text("上一步") }

            Button(
                onClick = { viewModel.confirmBudget() },
                modifier = Modifier.weight(1f)
            ) { Text("下一步") }
        }
    }
}

// ── Step: Distance ──

@Composable
private fun DistanceStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "选择距离",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DistanceLevel.entries.forEach { level ->
            val selected = uiState.distanceLevel == level
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.setDistance(level) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(level.label, style = MaterialTheme.typography.bodyLarge)
                    if (selected) {
                        Icon(Icons.Outlined.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.goBack() },
                modifier = Modifier.weight(1f)
            ) { Text("上一步") }

            Button(
                onClick = { viewModel.confirmDistanceAndSaveConditions() },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("开始选择")
                }
            }
        }
    }
}

// ── Step: Handoff ──

@Composable
private fun HandoffStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    val nextPerson = uiState.currentSelectingPerson
    val previousPersonName = viewModel.getPreviousPersonName()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Handshake,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        if (previousPersonName != null) {
            Text(
                text = "$previousPersonName 已完成选择",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        }

        Text(
            text = "请将手机交给",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = nextPerson?.name ?: "",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.startHandoffSelection() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("${nextPerson?.name ?: ""}开始选择")
        }
    }
}

// ── Step: Category Selection ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelectStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    val personName = uiState.currentSelectingPerson?.name ?: "用户"

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 标题
        Text(
            text = "$personName 的本次选择",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // 选择计数 + 自动保存提示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "想吃 ${uiState.currentWantCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "接受 ${uiState.currentAcceptCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "不吃 ${uiState.currentNotTodayCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = "已自动保存",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 搜索栏
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索分类") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true
        )

        if (uiState.isSearchEmpty) {
            // 搜索无结果空状态
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "未找到与「${uiState.searchQuery.trim()}」相关的分类",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            // 分类列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.filteredGroups) { group ->
                    CategoryGroupCard(
                        rootCategory = group.root,
                        children = group.children,
                        hardExcludedIds = uiState.hardExcludedCategoryIds,
                        selections = uiState.currentPersonSelections,
                        onSelectionChange = viewModel::setCategorySelection
                    )
                }
            }
        }

        // 底部完成按钮
        Button(
            onClick = { viewModel.completeCurrentPersonSelection() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = uiState.currentWantCount + uiState.currentAcceptCount > 0
        ) {
            Text("完成本次选择")
        }
    }
}

@Composable
private fun CategoryGroupCard(
    rootCategory: FoodCategory,
    children: List<FoodCategory>,
    hardExcludedIds: Set<String>,
    selections: Map<String, SelectionType>,
    onSelectionChange: (String, SelectionType) -> Unit
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = rootCategory.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            children.forEach { child ->
                val isExcluded = hardExcludedIds.contains(child.id)
                val currentSelection = selections[child.id]

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isExcluded) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface
                    )

                    if (isExcluded) {
                        // 长期硬排除，不可选
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "长期不吃",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        // 三选一
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SelectionType.entries.forEach { type ->
                                val label = when (type) {
                                    SelectionType.WANT -> "想吃"
                                    SelectionType.ACCEPT -> "接受"
                                    SelectionType.NOT_TODAY -> "不吃"
                                }
                                FilterChip(
                                    selected = currentSelection == type,
                                    onClick = { onSelectionChange(child.id, type) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Step: Results ──

@Composable
private fun ResultsStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel,
    onCompleted: () -> Unit
) {
    val title = if (uiState.isDualMode) "你们的共同候选" else "符合本次条件的候选"

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        if (uiState.candidates.isEmpty()) {
            // 无候选 - 显示原因和返回修改入口
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
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "没有符合条件的候选分类",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "可能的原因：\n• 双方共同想吃/接受的分类太少\n• 某些分类被标记为「今天不想吃」\n• 长期硬排除限制了可选范围\n\n尝试调整本次选择后重新生成",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.goBackToFirstPersonSelection() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("返回修改选择")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.showExitDialog()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("放弃本次决定")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.candidates) { candidate ->
                    val reasonLines = viewModel.getCandidateReasonLines(candidate)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (candidate.rank) {
                                0 -> MaterialTheme.colorScheme.primaryContainer
                                1 -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = candidate.categoryName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            candidate.parentCategoryName?.let { parent ->
                                Text(
                                    text = parent,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            reasonLines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 本轮只生成候选，不进入最终决定。保存候选(会话保持 READY)，返回首页后可"继续上次决定"。
            Button(
                onClick = { onCompleted() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("保存候选，稍后继续")
            }
        }
    }
}
