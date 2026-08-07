package com.what2eat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.PersonProfile
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
 * 观察主要用户档案，提供名称输入和保存功能。
 * - 若主要用户不存在，保存时创建新档案。
 * - 若主要用户已存在，保存时更新名称。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PersonProfileRepository
) : ViewModel() {

    private val _nameInput = MutableStateFlow("")
    private val _isSaving = MutableStateFlow(false)
    private val _showSavedMessage = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observePrimary(),
        _nameInput,
        combine(_isSaving, _showSavedMessage) { saving, saved -> saving to saved }
    ) { primary, nameInput, (isSaving, showSavedMessage) ->
        SettingsUiState(
            nameInput = nameInput,
            savedName = primary?.name,
            isSaving = isSaving,
            showSavedMessage = showSavedMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState()
    )

    init {
        // 首次加载时，用数据库中的名称初始化输入框
        viewModelScope.launch {
            val primary = repository.observePrimary().first()
            if (primary?.name != null && _nameInput.value.isBlank()) {
                _nameInput.value = primary.name
            }
        }
    }

    /** 用户输入名称时调用 */
    fun onNameChange(name: String) {
        _nameInput.value = name
        _showSavedMessage.value = false
    }

    /** 保存主要用户名称 */
    fun saveName() {
        val name = _nameInput.value.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            _isSaving.value = true

            val primary = repository.observePrimary().first()

            if (primary != null) {
                // 更新现有档案
                repository.upsert(
                    PersonProfile(
                        id = primary.id,
                        name = name,
                        avatar = primary.avatar,
                        isPrimary = true,
                        createdAt = primary.createdAt
                    )
                )
            } else {
                // 创建新的主要用户档案
                repository.upsert(
                    PersonProfile(
                        name = name,
                        isPrimary = true
                    )
                )
            }

            _isSaving.value = false
            _showSavedMessage.value = true
        }
    }
}
