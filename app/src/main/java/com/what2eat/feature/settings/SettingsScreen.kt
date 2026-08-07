package com.what2eat.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.R
import com.what2eat.domain.model.AppUsageMode

/**
 * 设置页面。
 * Stage 1.1: 新增使用模式、双人档案管理、偏好入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPreference: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val primaryInput = remember(uiState.primaryProfile) {
        viewModel.getPrimaryNameInput().ifBlank { uiState.primaryProfile?.name ?: "" }
    }
    val secondaryInput = remember(uiState.secondaryProfile) {
        viewModel.getSecondaryNameInput().ifBlank { uiState.secondaryProfile?.name ?: "" }
    }

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

        // 主用户
        ProfileCard(
            label = stringResource(R.string.settings_profile_primary),
            nameInput = primaryInput,
            onNameChange = viewModel::onPrimaryNameChange,
            onSave = viewModel::savePrimaryName,
            isSaving = uiState.isSaving,
            savedMessage = if (uiState.message == "primary_saved") stringResource(R.string.settings_saved) else null,
            onSetPreference = { onNavigateToPreference("person_primary") }
        )

        // 第二用户（仅双人模式显示）
        if (uiState.usageMode == AppUsageMode.COUPLE) {
            ProfileCard(
                label = stringResource(R.string.settings_profile_secondary),
                nameInput = secondaryInput,
                onNameChange = viewModel::onSecondaryNameChange,
                onSave = viewModel::saveSecondaryName,
                isSaving = uiState.isSaving,
                savedMessage = if (uiState.message == "secondary_saved") stringResource(R.string.settings_saved) else null,
                onSetPreference = { onNavigateToPreference("person_secondary") }
            )
        }

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
            text = stringResource(R.string.settings_version, "0.2.0"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 人物档案卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCard(
    label: String,
    nameInput: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
    savedMessage: String?,
    onSetPreference: () -> Unit
) {
    var localInput by remember(nameInput) { mutableStateOf(nameInput) }

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
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = localInput,
                onValueChange = {
                    localInput = it
                    onNameChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                enabled = !isSaving
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSave,
                    enabled = localInput.trim().isNotBlank() && localInput.trim().length <= 20 && !isSaving
                ) {
                    Text(stringResource(R.string.settings_save))
                }

                TextButton(onClick = onSetPreference) {
                    Text(stringResource(R.string.settings_set_preference))
                }
            }

            savedMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
