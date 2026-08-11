package com.what2eat.domain.share

import com.what2eat.domain.model.SourcePlatform

/**
 * 分享文本解析（名称"尽力而为"提取）。
 *
 * 名称优先级：1. EXTRA_SUBJECT；2. URL 前标题；3. 常见平台格式；4. 留空。
 * 无法识别名称时留空 → draft.needsReview=true。
 */
object ShareTextParser {

    /**
     * 从 Intent 数据生成 ShareImportDraft。
     *
     * @param rawText 分享文本（EXTRA_TEXT）
     * @param subject 分享标题（EXTRA_SUBJECT）
     * @param sourcePackage 来源包名（可空）
     */
    fun createDraft(rawText: String?, subject: String?, sourcePackage: String?): ShareImportDraft {
        val text = rawText?.trim().orEmpty()
        val url = UrlNormalizer.extractFirstUrl(text)
        val platform = PlatformRecognizer.detect(sourcePackage, url, text)
        val name = extractName(text, subject, url)
        return ShareImportDraft(
            rawText = text,
            subject = subject,
            sourcePackage = sourcePackage,
            detectedPlatform = platform,
            detectedUrl = url,
            detectedName = name
        )
    }

    /**
     * 名称提取（尽力而为）。
     * 1. subject 非空且不含 URL → subject
     * 2. URL 前的标题文本
     * 3. 常见平台格式（标题换行后正文）
     * 4. null
     */
    fun extractName(rawText: String?, subject: String?, url: String?): String? {
        // 1. subject
        subject?.let { s ->
            val clean = s.trim()
            if (clean.isNotEmpty() && !clean.startsWith("http")) return clean
        }
        // 2. URL 前的标题
        if (rawText != null && url != null) {
            val idx = rawText.indexOf(url)
            if (idx > 0) {
                val before = rawText.substring(0, idx).trim()
                val title = cleanTitle(before)
                if (title.isNotEmpty()) return title
            }
        }
        // 3. 常见平台格式：第一行若为标题则取之
        val firstLine = rawText?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
        if (!firstLine.isNullOrEmpty() && !firstLine.startsWith("http")) {
            val title = cleanTitle(firstLine)
            if (title.isNotEmpty()) return title
        }
        return null
    }

    /** 清理标题：去掉常见分享前缀、换行、多余空白。 */
    private fun cleanTitle(raw: String): String {
        var s = raw
            .replace(Regex("""\s+"""), " ")
            .trim()
        // 平台常见前缀
        listOf(
            "【", "】", "来自", "分享", "— 分享 —", "——", "--", "·", "「", "」",
            "发现一个好去处：", "推荐：", "推荐一个好店："
        ).forEach { prefix ->
            s = s.removePrefix(prefix)
        }
        return s.trim()
    }
}