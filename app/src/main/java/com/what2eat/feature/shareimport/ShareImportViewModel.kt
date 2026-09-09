package com.what2eat.feature.shareimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.foodpool.FoodOptionForm
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.model.SourcePlatform
import com.what2eat.domain.repository.SavedOptionRepository
import com.what2eat.domain.share.DuplicateCheckResult
import com.what2eat.domain.share.DuplicateDetector
import com.what2eat.domain.share.LinkTitleFetcher
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
    val unsupported: Boolean = false,
    /** 后台正在抓取链接标题补店名 */
    val isResolvingName: Boolean = false,
    /** v1.2.0：撞 yoda 验证墙后待人工通过的验证页 URL（非 null 时弹验证弹窗） */
    val verifyUrl: String? = null
) {
    /** 收件箱模式：解析出草稿即可保存，名称/类型允许留空（自动兜底） */
    val canSave: Boolean get() = draft != null
}

@HiltViewModel
class ShareImportViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repository: SavedOptionRepository,
    private val linkTitleFetcher: LinkTitleFetcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareImportFormState())
    val uiState: StateFlow<ShareImportFormState> = _uiState.asStateFlow()

    private var initialized = false

    /** 用户手动改过名称后，后台抓取的标题不再覆盖 */
    private var nameTouchedByUser = false

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
        val defaultType = ShareImportDefaults.defaultType(draft.detectedPlatform, draft.rawText)
        val defaultCols = ShareImportDefaults.defaultCollections(draft.detectedPlatform)
        _uiState.value = _uiState.value.copy(
            draft = draft,
            name = draft.detectedName ?: "",
            type = defaultType,
            collections = defaultCols,
            sourceUrl = draft.detectedUrl ?: "",
            // 地址/电话/营业时间预填备注（信息不丢）
            notes = draft.detectedNotes.orEmpty()
            // 收件箱模式：名称解析不出也不阻塞，保存时用兜底名称占位
        )
        // v1.1.0：初始化诊断日志文件（进程安全落盘）
        com.what2eat.data.share.FetchDebugLog.init(appContext)
        com.what2eat.data.share.FetchDebugLog.reset()
        // v1.2.0：清掉上一次分享的验证墙残留信号
        com.what2eat.data.share.VerifyWallSignal.clear()
        com.what2eat.data.share.FetchDebugLog.add(
            "解析: name=${draft.detectedName ?: "null"} url=${draft.detectedUrl ?: "null"}"
        )

        // v1.1.0：有链接就抓（占位名「待确认店铺」也尝试换真名——此前仅 detectedName==null
        // 才触发，美团分享常解析出占位名导致抓取链根本不启动、诊断无日志）。
        // 抓不到静默回退；抓到了且名称是占位名/空白才覆盖（不覆盖用户手动输入）。
        if (draft.detectedUrl != null) {
            val url = draft.detectedUrl
            val initialNameIsPlaceholder = draft.detectedName == null ||
                draft.detectedName.startsWith("待确认店铺")
            resolveName(url, initialNameIsPlaceholder)
        }
    }

    /**
     * 后台抓链接标题补店名。抓不到且撞过 yoda 验证墙 → 置 verifyUrl 触发人工验证弹窗。
     * v0.8.11 防御：抓取链路任何异常（网络/解析/WebView）都不得冒泡成未捕获协程
     * 异常——否则分享确认页进程被杀，用户表现为「确认页闪现后瞬间跳回美团」。
     */
    private fun resolveName(url: String, initialNameIsPlaceholder: Boolean) {
        _uiState.value = _uiState.value.copy(isResolvingName = true)
        viewModelScope.launch {
            val title = runCatching { linkTitleFetcher.fetchTitle(url) }.getOrNull()
            val s = _uiState.value
            // 覆盖条件：抓到真名 且（名称空白或仍是占位名）且用户未手动改过且未保存
            val currentIsPlaceholder = s.name.isBlank() || s.name.startsWith("待确认店铺")
            if (!title.isNullOrBlank() && !s.saved && currentIsPlaceholder &&
                initialNameIsPlaceholder && !nameTouchedByUser) {
                _uiState.value = s.copy(name = title, isResolvingName = false, verifyUrl = null)
                return@launch
            }
            // v1.2.0：失败且撞过验证墙 → 弹人工验证（用户仍可能改名称时才有意义）
            val wall = if (!s.saved && currentIsPlaceholder && !nameTouchedByUser) {
                com.what2eat.data.share.VerifyWallSignal.consume()
            } else {
                com.what2eat.data.share.VerifyWallSignal.clear()
                null
            }
            if (wall != null) {
                com.what2eat.data.share.FetchDebugLog.add("撞验证墙,弹人工验证")
            }
            _uiState.value = s.copy(isResolvingName = false, verifyUrl = wall)
        }
    }

    /**
     * v1.2.0：用户在验证页滑过 yoda → 关页重试（通过态 cookie 已种下）。
     * v1.2.4：验证页通过后落地店铺页可直接带回店名（shopName 非空）——免重试。
     */
    fun onVerifyPassed(shopName: String?) {
        com.what2eat.data.share.FetchDebugLog.add(
            "验证已通过${if (!shopName.isNullOrBlank()) ",店名:$shopName" else ",重试抓取"}"
        )
        val s = _uiState.value
        if (!shopName.isNullOrBlank() && !s.saved && !nameTouchedByUser &&
            (s.name.isBlank() || s.name.startsWith("待确认店铺"))
        ) {
            _uiState.value = s.copy(name = shopName, verifyUrl = null, isResolvingName = false)
            return
        }
        val url = s.draft?.detectedUrl ?: return
        _uiState.value = s.copy(verifyUrl = null)
        resolveName(url, initialNameIsPlaceholder = true)
    }

    /** v1.2.0：用户取消验证弹窗 → 清信号，保持手动填写 */
    fun onVerifyDismissed() {
        com.what2eat.data.share.VerifyWallSignal.clear()
        _uiState.value = _uiState.value.copy(verifyUrl = null)
    }

    fun onNameChange(v: String) {
        nameTouchedByUser = true
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

    /**
     * 收件箱保存：保存到「待整理」（NEEDS_REVIEW），之后在吃饭池整理。
     * 名称/类型留空时自动兜底，不阻塞分享收件；整理时再确认信息并转 COMPLETE。
     */
    fun save(stillSaveDuplicate: Boolean = false) {
        val s = _uiState.value
        val draft = s.draft ?: return
        // 兜底：名称解析不出 → 平台占位名；类型未选 → 按平台/文本推断
        val name = s.name.trim().ifEmpty { ShareImportDefaults.fallbackName(draft.detectedPlatform) }
        if (!FoodOptionForm.isValidName(name)) {
            _uiState.value = s.copy(nameError = "名称最长 50 字")
            return
        }
        val type = s.type ?: ShareImportDefaults.inboxType(draft.detectedPlatform, draft.rawText)
        viewModelScope.launch {
            // 重复检测（仅非"仍然保存"时）
            if (!stillSaveDuplicate) {
                val existing = repository.observeAll().first()
                val cols = repository.observeAllCollections().first()
                    .groupBy({ it.savedOptionId }, { it.collectionType })
                    .mapValues { it.value.toSet() }
                val result = DuplicateDetector.detect(
                    newUrl = s.sourceUrl.trim().takeIf { it.isNotBlank() },
                    newName = name,
                    newPlatform = draft.detectedPlatform,
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
                name = name,
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
                sourcePackage = draft.sourcePackage,
                // 收件箱：分享进来一律先进「待整理」，编辑保存后自动转 COMPLETE
                importStatus = ImportStatus.NEEDS_REVIEW
            )
            repository.upsert(option)
            repository.setCollections(option.id, s.collections)
            repository.setTags(option.id, s.tags.filter { it.isNotBlank() }.toSet())
            _uiState.value = s.copy(name = name, isSaving = true, saved = true, savedOptionId = option.id)
        }
    }
}