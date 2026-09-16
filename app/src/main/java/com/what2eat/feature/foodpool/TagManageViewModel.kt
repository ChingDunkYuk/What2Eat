package com.what2eat.feature.foodpool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.TagUsage
import com.what2eat.domain.repository.SavedOptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 标签管理面板 UI 状态 */
data class TagManageUiState(
    val isLoading: Boolean = true,
    /** 全部标签（按使用数降序、名称升序） */
    val tags: List<TagUsage> = emptyList(),
    /** v1.6.0：标签颜色（标签名 → colorArgb；无行 = 默认色，map 中不出现） */
    val colorByTag: Map<String, Int> = emptyMap(),
    /** 正在重命名的标签（null = 重命名对话框关闭） */
    val renamingTag: TagUsage? = null,
    /** 重命名输入框内容 */
    val renameInput: String = "",
    /** 待删除确认的标签（null = 删除对话框关闭） */
    val deletingTag: TagUsage? = null,
    /** v1.6.0：正在选色的标签（null = 色板对话框关闭） */
    val coloringTag: TagUsage? = null,
    /** 操作结果提示（Snackbar 消费后置 null） */
    val message: String? = null
)

/**
 * 标签管理 ViewModel（v0.9.2）。
 *
 * 标签本体是字符串（saved_option_tag.tagId），本面板只做
 * 重命名（目标已存在时自动合并）与删除，清单来自 GROUP BY 使用统计。
 * v1.6.0：新增标签颜色——预设色板选色，元数据行随重命名/合并/删除联动。
 */
@HiltViewModel
class TagManageViewModel @Inject constructor(
    private val repository: SavedOptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagManageUiState())
    val uiState: StateFlow<TagManageUiState> = _uiState.asStateFlow()

    init {
        combine(
            repository.observeTagUsage(),
            repository.observeTagColors()
        ) { tags, colors -> tags to colors }
            .onEach { (tags, colors) ->
                _uiState.update { it.copy(isLoading = false, tags = tags, colorByTag = colors) }
            }
            .launchIn(viewModelScope)
    }

    // ── 重命名（含合并语义） ──

    fun startRename(tag: TagUsage) {
        _uiState.update { it.copy(renamingTag = tag, renameInput = tag.name) }
    }

    fun onRenameInput(input: String) {
        _uiState.update { it.copy(renameInput = input) }
    }

    fun dismissRename() {
        _uiState.update { it.copy(renamingTag = null, renameInput = "") }
    }

    /** 确认重命名：目标已存在时自动合并；结果 message 区分两种语义 */
    fun confirmRename() {
        val tag = _uiState.value.renamingTag ?: return
        val input = _uiState.value.renameInput.trim()
        if (input.isEmpty() || input == tag.name) {
            dismissRename()
            return
        }
        viewModelScope.launch {
            val merged = _uiState.value.tags.any { it.name == input }
            repository.renameTag(tag.name, input)
            _uiState.update {
                it.copy(
                    renamingTag = null,
                    renameInput = "",
                    message = if (merged) "已合并到「$input」" else "已重命名为「$input」"
                )
            }
        }
    }

    // ── 删除 ──

    fun requestDelete(tag: TagUsage) {
        _uiState.update { it.copy(deletingTag = tag) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deletingTag = null) }
    }

    fun confirmDelete() {
        val tag = _uiState.value.deletingTag ?: return
        viewModelScope.launch {
            repository.deleteTag(tag.name)
            _uiState.update {
                it.copy(deletingTag = null, message = "已删除标签「${tag.name}」")
            }
        }
    }

    // ── 其他 ──

    /** 面板关闭时重置对话框中间态（VM 实例复用，避免下次打开残留） */
    fun onSheetDismissed() {
        _uiState.update { it.copy(renamingTag = null, renameInput = "", deletingTag = null, coloringTag = null, message = null) }
    }

    /** 消费 Snackbar 提示 */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ── v1.6.0：标签颜色 ──

    fun startColoring(tag: TagUsage) {
        _uiState.update { it.copy(coloringTag = tag) }
    }

    fun dismissColoring() {
        _uiState.update { it.copy(coloringTag = null) }
    }

    /** 选色（null = 恢复默认色）；落库后由 observeTagColors 流自动刷新 */
    fun pickColor(colorArgb: Int?) {
        val tag = _uiState.value.coloringTag ?: return
        viewModelScope.launch {
            repository.setTagColor(tag.name, colorArgb)
            _uiState.update { it.copy(coloringTag = null) }
        }
    }
}
