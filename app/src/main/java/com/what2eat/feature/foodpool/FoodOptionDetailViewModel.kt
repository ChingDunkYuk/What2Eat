package com.what2eat.feature.foodpool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.OptionPreferenceLevel
import com.what2eat.domain.model.PersonOptionPreference
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.repository.PersonProfileRepository
import com.what2eat.domain.repository.SavedOptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OptionPreferenceUi(
    val personId: String,
    val personName: String,
    val level: OptionPreferenceLevel,
    val hardExcluded: Boolean
)

data class FoodOptionDetailState(
    val option: SavedOption? = null,
    val collections: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val preferences: List<OptionPreferenceUi> = emptyList(),
    val message: String? = null,
    val deleted: Boolean = false,
    /** 是否已成功加载过数据（用于区分首次加载与删除后 option 为空） */
    val loaded: Boolean = false
)

@HiltViewModel
class FoodOptionDetailViewModel @Inject constructor(
    private val optionRepository: SavedOptionRepository,
    private val personRepository: PersonProfileRepository
) : ViewModel() {

    private val optionIdFlow = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(FoodOptionDetailState())
    val uiState: StateFlow<FoodOptionDetailState> = _uiState.asStateFlow()

    fun load(optionId: String) {
        if (optionIdFlow.value == optionId) return
        optionIdFlow.value = optionId
        viewModelScope.launch {
            combine(
                optionRepository.observeById(optionId),
                optionRepository.observeCollections(optionId),
                optionRepository.observeTags(optionId),
                optionRepository.observePreferences(optionId),
                personRepository.observeEnabled()
            ) { option, cols, tags, prefs, profiles ->
                val profileByName = profiles.associateBy { it.id }
                val prefUi = prefs.map { p ->
                    OptionPreferenceUi(
                        personId = p.personId,
                        personName = profileByName[p.personId]?.name ?: p.personId,
                        level = p.preferenceLevel,
                        hardExcluded = p.hardExcluded
                    )
                }
                FoodOptionDetailState(
                    option = option,
                    collections = cols.map { it.collectionType.label },
                    tags = tags,
                    preferences = prefUi
                )
            }.collect { state ->
                // 保留瞬时状态（message/deleted），并标记已加载
                _uiState.value = state.copy(
                    loaded = true,
                    message = _uiState.value.message,
                    deleted = _uiState.value.deleted
                )
            }
        }
    }

    fun setPreference(personId: String, level: OptionPreferenceLevel) {
        val optionId = parseOptionId() ?: return
        viewModelScope.launch {
            val existing = optionRepository.getPreferences(optionId)
                .firstOrNull { it.personId == personId }
            optionRepository.setPreference(
                PersonOptionPreference(
                    personId = personId,
                    savedOptionId = optionId,
                    preferenceLevel = level,
                    hardExcluded = existing?.hardExcluded ?: false
                )
            )
        }
    }

    fun setHardExcluded(personId: String, value: Boolean) {
        val optionId = parseOptionId() ?: return
        viewModelScope.launch {
            val existing = optionRepository.getPreferences(optionId)
                .firstOrNull { it.personId == personId }
            optionRepository.setPreference(
                PersonOptionPreference(
                    personId = personId,
                    savedOptionId = optionId,
                    preferenceLevel = existing?.preferenceLevel ?: OptionPreferenceLevel.NEUTRAL,
                    hardExcluded = value
                )
            )
        }
    }

    fun disable() {
        val id = parseOptionId() ?: return
        viewModelScope.launch {
            optionRepository.setEnabled(id, false)
            _uiState.value = _uiState.value.copy(message = "已停用")
        }
    }

    fun enable() {
        val id = parseOptionId() ?: return
        viewModelScope.launch {
            optionRepository.setEnabled(id, true)
            _uiState.value = _uiState.value.copy(message = "已启用")
        }
    }

    fun delete() {
        val id = parseOptionId() ?: return
        viewModelScope.launch {
            val referenced = optionRepository.isReferencedByHistory(id)
            if (referenced) {
                // 已被历史引用：不允许物理删除，改为停用
                optionRepository.setEnabled(id, false)
                _uiState.value = _uiState.value.copy(
                    message = "该选项已有历史记录，不能删除，已改为停用"
                )
            } else {
                optionRepository.delete(id)
                _uiState.value = _uiState.value.copy(
                    deleted = true,
                    message = "已删除"
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun parseOptionId(): String? {
        return _uiState.value.option?.id
    }
}