package com.what2eat.domain.share

import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.SourcePlatform

/**
 * 外部分享导入的草稿（Stage 5）。
 *
 * 由 ShareImportActivity 解析 Intent 后生成，进入确认页，
 * 用户确认/补充后才写入 SavedOption。不直接收到即写库。
 */
data class ShareImportDraft(
    val rawText: String,
    val subject: String?,
    val sourcePackage: String?,
    val detectedPlatform: SourcePlatform,
    val detectedUrl: String?,
    val detectedName: String?,
    /** 从分享文本提取的「地址：/电话：/营业时间：」行，预填到表单备注（信息不丢） */
    val detectedNotes: String? = null
) {
    /**
     * 是否进入 NEEDS_REVIEW：
     * - 名称无法识别
     * - 类型无法确定（纯文字）
     * - 只有 URL
     * - 解析明显不完整
     */
    val needsReview: Boolean
        get() = detectedName.isNullOrBlank()

    val importStatus: ImportStatus
        get() = if (needsReview) ImportStatus.NEEDS_REVIEW else ImportStatus.COMPLETE
}