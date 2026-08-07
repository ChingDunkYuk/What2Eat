package com.what2eat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonCategoryPreference
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.repository.AppUsageModeRepository
import com.what2eat.domain.repository.PersonCategoryPreferenceRepository
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel。
 *
 * 管理使用模式、双人档案、偏好摘要。
 * 名称编辑通过对话框进行，保存后自动关闭。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val personProfileRepository: PersonProfileRepository,
    private val usageModeRepository: AppUsageModeRepository,
    private val preferenceRepository: PersonCategoryPreferenceRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        personProfileRepository.observeAll(),
        usageModeRepository.observe(),
        preferenceRepository.observeByPerson("person_primary"),
        preferenceRepository.observeByPerson("person_secondary"),
        combine(_isSaving, _message) { saving, msg -> saving to msg }
    ) { profiles, usageMode, primaryPrefs, secondaryPrefs, (isSaving, message) ->
        val primary = profiles.firstOrNull { it.isPrimary }
        val secondary = profiles.firstOrNull { !it.isPrimary }

        SettingsUiState(
            usageMode = usageMode,
            profiles = profiles,
            primaryProfile = primary,
            secondaryProfile = secondary,
            primaryPreferenceSummary = computeSummary(primaryPrefs),
            secondaryPreferenceSummary = computeSummary(secondaryPrefs),
            isLoading = false,
            isSaving = isSaving,
            message = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState()
    )

    /**
     * 从偏好列表计算摘要。
     * setCount: preferenceLevel != 0 的数量
     * excludedCount: hardExcluded = true 的数量
     */
    private fun computeSummary(prefs: List<PersonCategoryPreference>): PreferenceSummary {
        val setCount = prefs.count { it.preferenceLevel != 0 }
        val excludedCount = prefs.count { it.hardExcluded }
        return PreferenceSummary(setCount = setCount, excludedCount = excludedCount)
    }

    /**
     * 保存人物名称（通过对话框编辑后调用）。
     * @param personId 人物 ID（"person_primary" 或 "person_secondary"）
     * @param name 新名称（已去空格）
     * @return 保存是否成功
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
                    val isPrimary = personId == "person_primary"
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

    /** 切换使用模式 */
    fun setUsageMode(mode: AppUsageMode) {
        viewModelScope.launch {
            _isSaving.value = true
            usageModeRepository.set(mode)

            if (mode == AppUsageMode.SINGLE) {
                // 切换为单人模式：将第二人物设为未启用，不删除
                val allProfiles = personProfileRepository.observeAll().first()
                val secondary = allProfiles.firstOrNull { !it.isPrimary }
                secondary?.let {
                    personProfileRepository.upsert(it.copy(enabled = false, updatedAt = System.currentTimeMillis()))
                }
            } else {
                // 切换为双人模式：确保第二人物存在且启用
                val allProfiles = personProfileRepository.observeAll().first()
                val secondary = allProfiles.firstOrNull { !it.isPrimary }

                if (secondary != null) {
                    if (!secondary.enabled) {
                        personProfileRepository.upsert(secondary.copy(enabled = true, updatedAt = System.currentTimeMillis()))
                    }
                } else {
                    // 创建默认第二人物
                    personProfileRepository.upsert(
                        PersonProfile(
                            id = "person_secondary",
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
