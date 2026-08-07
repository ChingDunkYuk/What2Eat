package com.what2eat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonCategoryPreference
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.repository.PRIMARY_PROFILE_ID
import com.what2eat.domain.repository.SECONDARY_PROFILE_ID
import com.what2eat.domain.repository.AppUsageModeRepository
import com.what2eat.domain.repository.PersonCategoryPreferenceRepository
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel。
 *
 * 管理使用模式、双人档案、偏好摘要。
 * 主用户身份以稳定 id = "person_primary" 为准，不依赖名称或列表顺序。
 * 支持加载失败态、重试与"修复人物档案"动作。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val personProfileRepository: PersonProfileRepository,
    private val usageModeRepository: AppUsageModeRepository,
    private val preferenceRepository: PersonCategoryPreferenceRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState(isLoading = true))

    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(isLoading = true)
            try {
                combine(
                    personProfileRepository.observeAll(),
                    usageModeRepository.observe(),
                    preferenceRepository.observeByPerson(PRIMARY_PROFILE_ID),
                    preferenceRepository.observeByPerson(SECONDARY_PROFILE_ID),
                    combine(_isSaving, _message) { saving, msg -> saving to msg }
                ) { profiles, usageMode, primaryPrefs, secondaryPrefs, (isSaving, message) ->
                    // 主用户：稳定 id 优先，其次 isPrimary=true（不依赖列表顺序）
                    val primary = profiles.firstOrNull { it.id == PRIMARY_PROFILE_ID }
                        ?: profiles.firstOrNull { it.isPrimary }
                    // 第二人物：稳定 id 优先，其次已启用且非主用户
                    val secondary = profiles.firstOrNull { it.id == SECONDARY_PROFILE_ID }
                        ?: profiles.firstOrNull { !it.isPrimary }

                    SettingsUiState(
                        usageMode = usageMode,
                        profiles = profiles,
                        primaryProfile = primary,
                        secondaryProfile = secondary,
                        primaryPreferenceSummary = computeSummary(primaryPrefs),
                        secondaryPreferenceSummary = computeSummary(secondaryPrefs),
                        // 有档案但无主用户：需要修复
                        needsPrimarySelection = profiles.isNotEmpty() && primary == null,
                        candidatesForSelection = profiles,
                        isLoading = false,
                        isSaving = isSaving,
                        message = message
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(
                    isLoading = false,
                    loadError = e.message ?: "加载人物档案失败"
                )
            }
        }
    }

    /** 重试加载 */
    fun retryLoad() {
        observe()
    }

    /**
     * 由用户指定某档案为主用户（情况 B 修复）。
     * 调起仓库将所选档案提升为主用户并重命名为稳定 id。
     */
    fun repairPrimary(profileId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                personProfileRepository.promoteProfileToPrimary(profileId)
                _message.value = "primary_repaired"
            } catch (e: Exception) {
                _message.value = "primary_repair_error"
            }
            _isSaving.value = false
        }
    }

    /**
     * 从偏好列表计算摘要。
     */
    private fun computeSummary(prefs: List<PersonCategoryPreference>): PreferenceSummary {
        val setCount = prefs.count { it.preferenceLevel != 0 }
        val excludedCount = prefs.count { it.hardExcluded }
        return PreferenceSummary(setCount = setCount, excludedCount = excludedCount)
    }

    /**
     * 保存人物名称（通过对话框编辑后调用）。
     */
    fun saveName(personId: String, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        if (trimmedName.length > 20) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val existing = personProfileRepository.getById(personId)
                if (existing != null) {
                    personProfileRepository.upsert(
                        existing.copy(name = trimmedName, updatedAt = System.currentTimeMillis())
                    )
                } else {
                    val isPrimary = personId == PRIMARY_PROFILE_ID
                    personProfileRepository.upsert(
                        PersonProfile(
                            id = personId,
                            name = trimmedName,
                            isPrimary = isPrimary,
                            sortOrder = if (isPrimary) 0 else 1,
                            enabled = true
                        )
                    )
                }
                _message.value = "name_saved"
            } catch (e: Exception) {
                _message.value = "name_save_error"
            }
            _isSaving.value = false
        }
    }

    /**
     * 切换使用模式。
     * 只启停第二人物（稳定 id = person_secondary），绝不动主用户。
     */
    fun setUsageMode(mode: AppUsageMode) {
        viewModelScope.launch {
            _isSaving.value = true
            usageModeRepository.set(mode)

            if (mode == AppUsageMode.SINGLE) {
                // 单人：仅停用第二人物，不删除、不改主用户
                val secondary = personProfileRepository.getById(SECONDARY_PROFILE_ID)
                    ?: personProfileRepository.getAllNow().firstOrNull { !it.isPrimary }
                secondary?.let {
                    personProfileRepository.upsert(it.copy(enabled = false, updatedAt = System.currentTimeMillis()))
                }
            } else {
                // 双人：确保第二人物存在且启用
                val secondary = personProfileRepository.getById(SECONDARY_PROFILE_ID)
                    ?: personProfileRepository.getAllNow().firstOrNull { !it.isPrimary }
                if (secondary != null) {
                    if (!secondary.enabled) {
                        personProfileRepository.upsert(secondary.copy(enabled = true, updatedAt = System.currentTimeMillis()))
                    }
                } else {
                    personProfileRepository.upsert(
                        PersonProfile(
                            id = SECONDARY_PROFILE_ID,
                            name = "另一半",
                            isPrimary = false,
                            sortOrder = 1,
                            enabled = true
                        )
                    )
                }
            }
            _isSaving.value = false
            _message.value = "mode_changed"
        }
    }

    /** 清除消息 */
    fun clearMessage() {
        _message.value = null
    }
}