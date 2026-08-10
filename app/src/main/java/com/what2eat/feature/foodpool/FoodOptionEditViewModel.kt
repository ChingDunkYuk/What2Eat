package com.what2eat.feature.foodpool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.foodpool.FoodOptionForm
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.repository.SavedOptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodOptionFormState(
    val optionId: String? = null,
    val name: String = "",
    val type: SavedOptionType = SavedOptionType.RESTAURANT,
    val collections: Set<CollectionType> = emptySet(),
    val tags: LinkedHashSet<String> = linkedSetOf(),
    val areaText: String = "",
    val priceLevel: Int? = null,
    val estimatedMinutes: String = "",
    val notes: String = "",
    val sourceUrl: String = "",
    val enabled: Boolean = true,
    val importStatus: ImportStatus = ImportStatus.COMPLETE,
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val hasChanges: Boolean = false
) {
    /** 名称已 trim 且非空，可保存 */
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class FoodOptionEditViewModel @Inject constructor(
    private val repository: SavedOptionRepository
) : ViewModel() {

    /** 是否有未保存的修改（新增有输入或编辑有改动） */
    val hasChanges: Boolean
        get() = _uiState.value.hasChanges

    private val _uiState = MutableStateFlow(FoodOptionFormState())
    val uiState: StateFlow<FoodOptionFormState> = _uiState.asStateFlow()

    private var loaded = false

    fun load(optionId: String?) {
        if (loaded) return
        loaded = true
        if (optionId == null) return
        viewModelScope.launch {
            val option = repository.getById(optionId) ?: return@launch
            val collections = repository.observeCollections(optionId).first().map { it.collectionType }.toSet()
            val tags = repository.observeTags(optionId).first().toSet()
            _uiState.value = _uiState.value.copy(
                optionId = option.id,
                name = option.name,
                type = option.optionType,
                collections = collections,
                tags = LinkedHashSet(tags),
                areaText = option.areaText ?: "",
                priceLevel = option.priceLevel,
                estimatedMinutes = option.estimatedMinutes?.toString() ?: "",
                notes = option.notes ?: "",
                sourceUrl = option.sourceUrl ?: "",
                enabled = option.enabled,
                importStatus = option.importStatus,
                hasChanges = false
            )
        }
    }

    fun onNameChange(v: String) {
        _uiState.value = _uiState.value.copy(name = v, nameError = null, hasChanges = true)
    }

    fun onTypeChange(t: SavedOptionType) {
        _uiState.value = _uiState.value.copy(type = t, hasChanges = true)
    }

    fun toggleCollection(c: CollectionType) {
        val cur = _uiState.value.collections
        val next = if (c in cur) cur - c else cur + c
        _uiState.value = _uiState.value.copy(collections = next, hasChanges = true)
    }

    fun onAreaChange(v: String) = _uiState.value.copy(areaText = v, hasChanges = true).let { _uiState.value = it }
    fun onNotesChange(v: String) = _uiState.value.copy(notes = v, hasChanges = true).let { _uiState.value = it }
    fun onUrlChange(v: String) = _uiState.value.copy(sourceUrl = v, hasChanges = true).let { _uiState.value = it }
    fun onMinutesChange(v: String) = _uiState.value.copy(estimatedMinutes = v, hasChanges = true).let { _uiState.value = it }
    fun toggleEnabled() = _uiState.value.copy(enabled = !_uiState.value.enabled, hasChanges = true).let { _uiState.value = it }

    fun onTagsChange(tags: Set<String>) {
        _uiState.value = _uiState.value.copy(tags = LinkedHashSet(tags), hasChanges = true)
    }

    fun save() {
        val s = _uiState.value
        if (!FoodOptionForm.isValidName(s.name)) {
            _uiState.value = s.copy(
                nameError = if (s.name.isBlank()) "名称不能为空" else "名称最长 50 字"
            )
            return
        }
        viewModelScope.launch {
            val existing = s.optionId?.let { repository.getById(it) }
            val option = FoodOptionForm.buildOption(
                existing = existing,
                name = s.name,
                type = s.type,
                areaText = s.areaText,
                priceLevel = s.priceLevel,
                estimatedMinutes = s.estimatedMinutes.toIntOrNull(),
                notes = s.notes,
                sourceUrl = s.sourceUrl,
                enabled = s.enabled,
                // 待整理项保存后标记为整理完成
                markCompleted = s.importStatus == ImportStatus.NEEDS_REVIEW
            )
            repository.upsert(option)
            repository.setCollections(option.id, s.collections)
            repository.setTags(option.id, s.tags.filter { it.isNotBlank() }.toSet())
            _uiState.value = _uiState.value.copy(isSaving = true, saved = true, hasChanges = false)
        }
    }
}