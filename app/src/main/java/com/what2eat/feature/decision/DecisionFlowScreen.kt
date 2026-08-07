package com.what2eat.feature.decision

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Handshake
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.R
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stepTitle) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "退出")
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
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
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

        uiState.availableProfiles.forEach { profile ->
            val selected = uiState.selectedParticipantIds.contains(profile.id)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod -> mod },
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
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
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
            Text("开始")
        }
    }
}

// ── Step: Meal Mode ──

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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "选择用餐方式（可多选）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MealMode.entries.forEach { mode ->
            val selected = uiState.mealModes.contains(mode)
            FilterChip(
                selected = selected,
                onClick = { viewModel.toggleMealMode(mode) },
                label = { Text(mode.label) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
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

        MoodTag.entries.forEach { tag ->
            val selected = uiState.moodTags.contains(tag)
            FilterChip(
                selected = selected,
                onClick = { viewModel.toggleMoodTag(tag) },
                label = { Text(tag.label) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
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
            ) { Text("开始选择") }
        }
    }
}

// ── Step: Handoff ──

@Composable
private fun HandoffStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    val nextPerson = uiState.availableProfiles.firstOrNull { it.id == uiState.currentSelectingPersonId }

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

        Text(
            text = "请将手机交给",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 24.dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = nextPerson?.name ?: "",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (uiState.currentSelectingPersonIndex > 0) {
            Text(
                text = "上一位已完成选择",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.loadCurrentPersonSelections()
                viewModel.startHandoffSelection()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("我准备好了")
        }
    }
}

// ── Step: Category Selection ──

@Composable
private fun CategorySelectStep(
    uiState: DecisionUiState,
    viewModel: DecisionViewModel
) {
    val personName = uiState.currentSelectingPerson?.name ?: "用户"
    val rootCategories = uiState.allCategories.filter { it.parentId == null }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 标题
        Text(
            text = "$personName 的本次选择",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        Text(
            text = "想吃 / 可以接受 / 今天不想吃",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rootCategories) { root ->
                val children = uiState.allCategories.filter { it.parentId == root.id }
                if (children.isNotEmpty()) {
                    CategoryGroupCard(
                        rootCategory = root,
                        children = children,
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
                .padding(16.dp)
        ) {
            Text("完成选择")
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
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "候选分类",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        if (uiState.candidates.isEmpty()) {
            // 无候选
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "没有符合条件的候选分类",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "尝试调整本次选择或长期偏好后再试",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
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
                            Text(
                                text = when (candidate.rank) {
                                    0 -> "双方都想吃"
                                    1 -> "一方想吃一方接受"
                                    else -> "双方都接受"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                viewModel.completeSession()
                onCompleted()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("完成本次决策")
        }
    }
}
