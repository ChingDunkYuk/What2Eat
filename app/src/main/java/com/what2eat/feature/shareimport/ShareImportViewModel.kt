package com.what2eat.feature.shareimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.foodpool.FoodOptionForm
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.model.SourcePlatform
import com.what2eat.domain.repository.SavedOptionRepository
import com.what2eat.domain.share.DuplicateCheckResult
import com.what2eat.domain.share.DuplicateDetector
import com.what2eat.domain.share.ShareImportDraft
import com.what2eat.domain.share.ShareImportDefaults
import com.what2eat.domain.share.ShareTextParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareImportFormState(
    val draft: ShareImportDraft? = null,
    val name: String = "",
    val type: SavedOptionType? = null,
    val collections: Set<CollectionType> = emptySet(),
    val tags: LinkedHashSet<String> = linkedSetOf(),
    val areaText: String = "",
    val notes: String = "",
    val sourceUrl: String = "",
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val duplicate: DuplicateCheckResult.Duplicate? = null,
    val savedOptionId: String? = null,
    val saved: Boolean = false,
    val unsupported: Boolean = false
) {
    val canSave: Boolean get() = name.isNotBlank() && type != null
}

@HiltViewModel
class ShareImportViewModel @Inject constructor(
    private val repository: SavedOptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareImportFormState())
    val uiState: StateFlow<ShareImportFormState> = _uiState.asStateFlow()

    private var initialized = false

    /**
     * 用 Intent 数据初始化表单。
     * @param rawText EXTRA_TEXT
     * @param subject EXTRA_SUBJECT
     * @param sourcePackage 来源包名
     */
    fun init(rawText: String?, subject: String?, sourcePackage: String?) {
        if (initialized) return
        initialized = true
        val draft = ShareTextParser.createDraft(rawText, subject, sourcePackage)
        val defaultType = ShareImportDefaults.defaultType(draft.detectedPlatform)
        val defaultCols = ShareImportDefaults.defaultCollections(draft.detectedPlatform)
        _uiState.value = _uiState.value.copy(
            draft = draft,
            name = draft.detectedName ?: "",
            type = defaultType,
            collections = defaultCols,
            sourceUrl = draft.detectedUrl ?: "",
            // 来源平台对应的默认类型一定可确定；若纯文字则 type=null 要求用户确认
            nameError = if (draft.needsReview && defaultType == null) "请补充名称" else null
        )
    }

    fun onNameChange(v: String) {
        _uiState.value = _uiState.value.copy(name = v, nameError = null)
    }

    fun onTypeChange(t: SavedOptionType) {
        _uiState.value = _uiState.value.copy(type = t)
    }

    fun toggleCollection(c: CollectionType) {
        val cur = _uiState.value.collections
        _uiState.value = _uiState.value.copy(collections = if (c in cur) cur - c else cur + c)
    }

    fun onTagsChange(tags: Set<String>) {
        _uiState.value = _uiState.value.copy(tags = LinkedHashSet(tags))
    }

    fun onAreaChange(v: String) {
        _uiState.value = _uiState.value.copy(areaText = v)
    }

    fun onNotesChange(v: String) {
        _uiState.value = _uiState.value.copy(notes = v)
    }

    fun onUrlChange(v: String) {
        _uiState.value = _uiState.value.copy(sourceUrl = v)
    }

    fun consumeDuplicate() {
        _uiState.value = _uiState.value.copy(duplicate = null)
    }

    /** 保存并复用 Stage 4 的 SavedOptionRepository。 */
    fun save(stillSaveDuplicate: Boolean = false) {
        val s = _uiState.value
        val draft = s.draft ?: return
        val type = s.type ?: run {
            _uiState.value = s.copy(nameError = "请选择类型")
            return
        }
        if (!FoodOptionForm.isValidName(s.name)) {
            _uiState.value = s.copy(
                nameError = if (s.name.isBlank()) "名称不能为空" else "名称最长 50 字"
            )
            return
        }
        viewModelScope.launch {
            // 重复检测（仅非"仍然保存"时）
            if (!stillSaveDuplicate) {
                val existing = repository.observeAll().first()
                val cols = repository.observeAllCollections().first()
                    .groupBy({ it.savedOptionId }, { it.collectionType })
                    .mapValues { it.value.toSet() }
                val result = DuplicateDetector.detect(
                    newUrl = s.sourceUrl.trim().takeIf { it.isNotBlank() },
                    newName = s.name,
                    existing = existing,
                    existingCollections = cols
                )
                if (result is DuplicateCheckResult.Duplicate) {
                    _uiState.value = s.copy(duplicate = result)
                    return@launch
                }
            }

            val option = FoodOptionForm.buildOption(
                existing = null,
                name = s.name,
                type = type,
                areaText = s.areaText,
                priceLevel = null,
                estimatedMinutes = null,
                notes = s.notes,
                sourceUrl = s.sourceUrl,
                enabled = true,
                markCompleted = false
            ).copy(
                // 覆盖来源字段
                sourcePlatform = draft.detectedPlatform,
                sourcePackage = draft.sourcePackage
            )
            repository.upsert(option)
            repository.setCollections(option.id, s.collections)
            repository.setTags(option.id, s.tags.filter { it.isNotBlank() }.toSet())
            _uiState.value = s.copy(isSaving = true, saved = true, savedOptionId = option.id)
        }
    }
}