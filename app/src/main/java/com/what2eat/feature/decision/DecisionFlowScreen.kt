package com.what2eat.feature.decision

import com.what2eat.core.designsystem.icon.What2EatBackIcon
import com.what2eat.core.designsystem.icon.What2EatIcons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.SelectionType
import com.what2eat.feature.common.PlatformSearchSheet

/**
 * 决策流程主页面。
 * 根据当前 step 渲染不同的步骤内容。
 *
 * Stage 2.1 精简流程：
 * CONDITIONS(本次条件，合并参与人物+用餐方式+状态+预算+距离)
 * → HANDOFF(双人交接) → CATEGORY_SELECT(每人本次选择) → RESULTS(候选结果)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionFlowScreen(
    onExit: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: DecisionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 展示搜索操作结果 Snackbar（地图/浏览器/复制反馈）
    LaunchedEffect(uiState.searchMessage) {
        val msg = uiState.searchMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSearchMessage()
    }

    val stepTitle = when (uiState.step) {
        DecisionStep.CONDITIONS -> "本次条件"
        DecisionStep.HANDOFF -> "交接"
        DecisionStep.CATEGORY_SELECT -> "本次选择"
        DecisionStep.RESULTS -> "候选结果"
        DecisionStep.RECOMMENDATION -> "最终推荐"
        DecisionStep.COMPLETED -> "完成"
    }

    // BackHandler：拦截系统返回键
    BackHandler {
        when (uiState.step) {
            DecisionStep.CONDITIONS -> viewModel.handleBack(onExit)
            DecisionStep.HANDOFF,
            DecisionStep.CATEGORY_SELECT,
            DecisionStep.RESULTS,
            DecisionStep.RECOMMENDATION -> viewModel.showExitDialog()
            DecisionStep.COMPLETED -> onExit()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stepTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (uiState.step) {
                            DecisionStep.CONDITIONS -> viewModel.handleBack(onExit)
                            DecisionStep.HANDOFF,
                            DecisionStep.CATEGORY_SELECT,
                            DecisionStep.RESULTS,
                            DecisionStep.RECOMMENDATION -> viewModel.showExitDialog()
                            DecisionStep.COMPLETED -> onExit()
                        }
                    }) {
                        What2EatBackIcon(contentDescription = "返回")
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
                    DecisionStep.CONDITIONS -> ConditionsStep(uiState, viewModel)
                    DecisionStep.HANDOFF -> HandoffStep(uiState, viewModel)
                    DecisionStep.CATEGORY_SELECT -> CategorySelectStep(
                        uiState = uiState,
                        viewModel = viewModel,
                        keyboardController = keyboardController
                    )
                    DecisionStep.RESULTS -> ResultsStep(uiState, viewModel, onCompleted, keyboardController)
                    DecisionStep.RECOMMENDATION -> RecommendationStep(uiState, viewModel)
                    DecisionStep.COMPLETED -> CompletedStep(uiState, viewModel, onExit, onCompleted)
                }
            }
        }
    }

    // ── Dialogs ──

    // 条件页已修改时返回首页的草稿提示
    if (uiState.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDiscardDialog() },
            icon = { Icon(What2EatIcons.WarningAmber, contentDescription = null) },
            title = { Text("保存草稿？") },
            text = { Text("你已修改了本次条件。是否保存草稿以便下次继续？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hideDiscardDialog()
                    onExit()
                }) {
                    Text("保存草稿并退出")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.confirmDiscard()
                    onExit()
                }) {
                    Text("放弃修改")
                }
            }
        )
    }

    // 其他步骤返回首页的退出提示
    if (uiState.showExitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideExitDialog() },
            icon = { Icon(What2EatIcons.WarningAmber, contentDescription = null) },
            title = { Text("退出决策流程") },
            text = { Text("你有一个进行中的决策流程，是否继续？") },
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

    // 放弃的二次确认
    if (uiState.showCancelDialog) {
        val title = if (uiState.cancelForNewSession) "放弃并重新开始？" else "确认放弃"
        val text = if (uiState.cancelForNewSession)
            "放弃当前决策后将重新开始。当前数据将被删除，确定吗？"
        else
            "放弃后本次决策的所有数据将被删除，无法恢复。确定要放弃吗？"
        AlertDialog(
            onDismissRequest = { viewModel.hideCancelDialog() },
            icon = { Icon(What2EatIcons.WarningAmber, contentDescription = null) },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmCancelSession()
                    if (!uiState.cancelForNewSession) onExit()
                }) {
                    Text(if (uiState.cancelForNewSession) "确认放弃" else "确认放弃")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCancelDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // 已有活动会话时点击"先决定吃什么"
    if (uiState.showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelNewSession() },
            icon = { Icon(What2EatIcons.WarningAmber, contentDescription = null) },
            title = { Text("已有未完成的决策") },
            text = { Text("你有一个进行中的决策流程。要继续现有决定，还是放弃并重新开始？") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmNewSession() }) {
                    Text("放弃并重新开始")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelNewSession() }) {
                    Text("继续现有决定")
                }
            }
        )
    }

    // ── Stage 3.2 平台承接底部面板（关闭不影响已完成决策）──
    if (uiState.showSearchPanel) {
        PlatformSearchSheet(
            query = viewModel.completedSearchQuery(),
            onDismiss = viewModel::hideSearchPanel,
            onPlatformSearch = viewModel::onPlatformSearch,
            onCopySearch = viewModel::onCopySearch
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
            imageVector = What2EatIcons.WarningAmber,
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

// ── Step: CONDITIONS（合并条件页）──

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConditionsStep(
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
        // 参与人物（仅双人模式显示）
        if (uiState.usageMode == AppUsageMode.COUPLE) {
            Text(
                text = "参与人物",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            uiState.availableProfiles.forEach { profile ->
                val selected = uiState.selectedParticipantIds.contains(profile.id)
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
                                What2EatIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 怎么吃
        Text(
            text = "怎么吃（可多选）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MealMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.mealModes.contains(mode),
                    onClick = { viewModel.toggleMealMode(mode) },
                    label = { Text(mode.label) }
                )
            }
        }

        // 今天状态
        Text(
            text = "今天的状态（可多选）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MoodTag.entries.forEach { tag ->
                FilterChip(
                    selected = uiState.moodTags.contains(tag),
                    onClick = { viewModel.toggleMoodTag(tag) },
                    label = { Text(tag.label) }
                )
            }
        }

        // 预算
        Text(
            text = "预算",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
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
                        Icon(What2EatIcons.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // 距离
        Text(
            text = "距离",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
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
                        Icon(What2EatIcons.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 就地防御性提示（不跳转整页错误）
        uiState.conditionsInlineError?.let { inlineError ->
            Text(
                text = inlineError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // 底部统一按钮
        Button(
            onClick = { viewModel.confirmConditions() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("下一步：选择想吃什么")
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
            imageVector = What2EatIcons.Handshake,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySelectStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
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
        Text(
            text = "选择会自动保存",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 搜索栏（实时过滤 + 结果数量 + 清除按钮）
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("搜索分类") },
            leadingIcon = { Icon(What2EatIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearSearch() }) {
                        Icon(What2EatIcons.Close, contentDescription = "清除搜索")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
            })
        )

        // 搜索结果数量提示
        if (uiState.searchQuery.trim().isNotEmpty()) {
            Text(
                text = "找到 ${uiState.searchResultCount} 个分类",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

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
                    imageVector = What2EatIcons.Search,
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
                contentPadding = PaddingValues(
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
                        onSelect = viewModel::setCategorySelection,
                        onClear = viewModel::clearCategorySelection
                    )
                }
            }
        }

        // 底部生成候选按钮（IME 弹出时自动上移，不被遮挡）
        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.completeCurrentPersonSelection()
            },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            enabled = uiState.currentWantCount + uiState.currentAcceptCount > 0
        ) {
            Text("生成候选")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryGroupCard(
    rootCategory: FoodCategory,
    children: List<FoodCategory>,
    hardExcludedIds: Set<String>,
    selections: Map<String, SelectionType>,
    onSelect: (String, SelectionType) -> Unit,
    onClear: (String) -> Unit
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

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    What2EatIcons.Block,
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
                        }
                    }

                    if (!isExcluded) {
                        // 四选一状态：想吃/可以/不吃/未选择
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SelectionChip(
                                label = "想吃",
                                selected = currentSelection == SelectionType.WANT,
                                color = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    if (currentSelection == SelectionType.WANT) onClear(child.id)
                                    else onSelect(child.id, SelectionType.WANT)
                                }
                            )
                            SelectionChip(
                                label = "可以",
                                selected = currentSelection == SelectionType.ACCEPT,
                                color = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    if (currentSelection == SelectionType.ACCEPT) onClear(child.id)
                                    else onSelect(child.id, SelectionType.ACCEPT)
                                }
                            )
                            SelectionChip(
                                label = "不吃",
                                selected = currentSelection == SelectionType.NOT_TODAY,
                                color = MaterialTheme.colorScheme.error,
                                onClick = {
                                    if (currentSelection == SelectionType.NOT_TODAY) onClear(child.id)
                                    else onSelect(child.id, SelectionType.NOT_TODAY)
                                }
                            )
                            SelectionChip(
                                label = "未选择",
                                selected = currentSelection == null,
                                color = MaterialTheme.colorScheme.outline,
                                onClick = { onClear(child.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionChip(
    label: String,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color
        )
    )
}

// ── Step: Results ──

@Composable
private fun ResultsStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel,
    onCompleted: () -> Unit,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
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
                    imageVector = What2EatIcons.WarningAmber,
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
                    Icon(What2EatIcons.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("返回修改选择")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.showExitDialog() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("放弃本次决定")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
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

            // 生成最终推荐（Stage 2.2）
            Button(
                onClick = { viewModel.generateRecommendation() },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp)
            ) {
                Icon(What2EatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("生成最终推荐")
            }

            // 保存候选（会话保持 READY），返回首页后显示"继续本次决定"
            OutlinedButton(
                onClick = {
                    keyboardController?.hide()
                    onCompleted()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
            ) {
                Text("保存候选，返回首页")
            }
        }
    }
}

/**
 * 最终推荐页（Stage 2.2）。
 * 展示推荐分类、推荐原因、匹配度，支持换一个/就吃这个/看看其他候选。
 */
@Composable
private fun RecommendationStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    if (uiState.isComputingRecommendation) {
        // 正在计算推荐（含换一个）
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(
                text = "正在为你挑选...",
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 原本就无候选 / 全部换完
    if (uiState.recommendationExhausted || uiState.recommendation == null) {
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
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = when {
                    uiState.candidates.isEmpty() -> "今天没有符合所有条件的选择"
                    // 有候选、也有换过的记录 → 真的看完了
                    uiState.rejectedIds.isNotEmpty() -> "候选已经看完了"
                    // 有候选但一个都没被换过 → 是条件把候选全过滤了，不是看完了
                    else -> "候选都没能通过本次条件\n试试放宽用餐方式或预算"
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (uiState.candidates.isNotEmpty() && uiState.rejectedIds.isNotEmpty()) {
                Button(
                    onClick = { viewModel.resetRejectedAndRecompute() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重新看看这些选项")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { viewModel.returnToConditionsFromRecommendation() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("返回修改条件")
            }
        }
        return
    }

    val recommendation = uiState.recommendation!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "今晚吃：",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        recommendation.parentCategoryName?.let { parent ->
            Text(
                text = parent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = recommendation.categoryName,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // 匹配度
        Text(
            text = "匹配度：${viewModel.matchLevelLabel(recommendation)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 推荐原因
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
                    text = "为什么推荐它",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                recommendation.reasonTypes.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = What2EatIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = viewModel.reasonText(type),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 操作按钮
        Button(
            onClick = { viewModel.confirmRecommendation() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("就吃这个")
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isLastRecommendation) {
            Text(
                text = "只剩这个选择了",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            OutlinedButton(
                onClick = { viewModel.rerollRecommendation() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isComputingRecommendation
            ) {
                Icon(What2EatIcons.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("换一个")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { viewModel.goBackToCandidates() }) {
            Text("看看其他候选")
        }
    }
}

/**
 * 决定完成页（Stage 2.2）。
 */
@Composable
private fun CompletedStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel,
    onExit: () -> Unit,
    onCompleted: () -> Unit
) {
    val completed = uiState.completedCategory

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = What2EatIcons.Check,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "决定好了！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (completed != null) {
            completed.parentCategoryName?.let { parent ->
                Text(
                    text = parent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Text(
                text = "今晚吃：",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = completed.categoryName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = viewModel::showSearchPanel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("去找餐厅")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回首页")
        }
    }
}

// ── Stage 3.2 平台承接 —— 复用 feature.common.PlatformSearchSheet ──