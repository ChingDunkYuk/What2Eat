package com.what2eat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.repository.AppUsageModeRepository
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
 * Stage 1.1: 支持使用模式切换和双人档案管理。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val personProfileRepository: PersonProfileRepository,
    private val usageModeRepository: AppUsageModeRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    /** 主用户名称输入 */
    private val _primaryNameInput = MutableStateFlow("")
    /** 第二用户名称输入 */
    private val _secondaryNameInput = MutableStateFlow("")
    private val _initialized = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        personProfileRepository.observeAll(),
        usageModeRepository.observe(),
        combine(_isSaving, _message, _primaryNameInput, _secondaryNameInput) { saving, msg, pName, sName ->
            SettingsMisc(saving, msg, pName, sName)
        }
    ) { profiles, usageMode, misc ->
        val primary = profiles.firstOrNull { it.isPrimary }
        val secondary = profiles.firstOrNull { !it.isPrimary }

        // 首次加载时初始化输入框
        if (!_initialized.value) {
            primary?.name?.let { if (_primaryNameInput.value.isBlank()) _primaryNameInput.value = it }
            secondary?.name?.let { if (_secondaryNameInput.value.isBlank()) _secondaryNameInput.value = it }
            _initialized.value = true
        }

        SettingsUiState(
            usageMode = usageMode,
            profiles = profiles,
            primaryProfile = primary,
            secondaryProfile = secondary,
            isLoading = false,
            isSaving = misc.isSaving,
            message = misc.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState()
    )

    /** 主用户名称输入变化 */
    fun onPrimaryNameChange(name: String) {
        _primaryNameInput.value = name
        _message.value = null
    }

    /** 第二用户名称输入变化 */
    fun onSecondaryNameChange(name: String) {
        _secondaryNameInput.value = name
        _message.value = null
    }

    /** 获取主用户名称输入框当前值 */
    fun getPrimaryNameInput(): String = _primaryNameInput.value

    /** 获取第二用户名称输入框当前值 */
    fun getSecondaryNameInput(): String = _secondaryNameInput.value

    /** 保存主用户名称 */
    fun savePrimaryName() {
        val name = _primaryNameInput.value.trim()
        if (name.isBlank()) return
        if (name.length > 20) return

        viewModelScope.launch {
            _isSaving.value = true
            val primary = personProfileRepository.observePrimary().first()

            if (primary != null) {
                personProfileRepository.upsert(
                    primary.copy(name = name, updatedAt = System.currentTimeMillis())
                )
            } else {
                personProfileRepository.upsert(
                    PersonProfile(
                        id = "person_primary",
                        name = name,
                        isPrimary = true,
                        sortOrder = 0,
                        enabled = true
                    )
                )
            }
            _isSaving.value = false
            _message.value = "primary_saved"
        }
    }

    /** 保存第二用户名称 */
    fun saveSecondaryName() {
        val name = _secondaryNameInput.value.trim()
        if (name.isBlank()) return
        if (name.length > 20) return

        viewModelScope.launch {
            _isSaving.value = true

            // 查找现有的非主用户
            val allProfiles = personProfileRepository.observeAll().first()
            val secondary = allProfiles.firstOrNull { !it.isPrimary }

            if (secondary != null) {
                personProfileRepository.upsert(
                    secondary.copy(name = name, updatedAt = System.currentTimeMillis())
                )
            } else {
                personProfileRepository.upsert(
                    PersonProfile(
                        id = "person_secondary",
                        name = name,
                        isPrimary = false,
                        sortOrder = 1,
                        enabled = true
                    )
                )
            }
            _isSaving.value = false
            _message.value = "secondary_saved"
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

    private data class SettingsMisc(
        val isSaving: Boolean,
        val message: String?,
        val primaryNameInput: String,
        val secondaryNameInput: String
    )
}
