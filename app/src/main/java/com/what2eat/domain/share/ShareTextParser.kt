package com.what2eat.domain.share

import com.what2eat.domain.model.SourcePlatform

/**
 * 分享文本解析（名称"尽力而为"提取）。
 *
 * 名称优先级：1. EXTRA_SUBJECT；2. URL 前标题；3. 常见平台格式；4. 留空。
 *
 * 店名净化（避免把整句分享文案当店名）：
 * - 优先取「」【】中的候选店名（点评/美团常用格式）；
 * - 命中营销话术/评分/销量/距离等关键词即截断；
 * - 丢弃描述性括号尾巴（这家店超好吃），保留分店名（望京店）；
 * - 超长时先按标点断句，再硬截断到 30 字。
 */
object ShareTextParser {

    /** 平台标签（【】中的平台名，不是店名） */
    private val platformTags = listOf(
        "美团外卖", "大众点评", "美团", "饿了么", "高德地图", "百度地图", "口碑"
    )

    /** 营销/元信息关键词：命中位置即截断，其后内容不属于店名 */
    private val cutMarkers = listOf(
        "快来", "快看", "看看", "试试", "推荐", "安利", "分享给", "分享自",
        "复制", "打开", "点击", "选购", "立即", "下单", "领券", "优惠券", "红包",
        "月售", "人均", "好评", "评分", "榜单", "排名",
        "满减", "立减", "半价", "包邮", "折扣",
        "距离你", "距离您", "距你", "距您", "附近", "导航"
    )

    /** 评分尾巴：4.8分 / 4.8 分 */
    private val ratingRegex = Regex("""\d+(?:\.\d+)?\s*分""")

    /** 「」『』【】中的候选店名 */
    private val bracketRegex = Regex("""[【「『]([^【」』]{2,32})[】」』]""")

    /** 描述性括号尾巴（含好吃/推荐等），如（这家店超好吃）→ 丢弃；分店名（望京店）不含关键词 → 保留 */
    private val descriptiveParenRegex =
        Regex("""[（(][^（）()]*?(?:好吃|推荐|喜欢|不错|必吃|强推|值得|超赞)[^（）()]*?[)）]\s*$""")

    /** 店名长度上限（先按标点断句，再硬截断） */
    private const val MAX_TITLE_LENGTH = 30

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
     * 1. subject 非空且不含 URL → subject（同样净化）
     * 2. URL 前的标题文本
     * 3. URL 后的第一行非 URL 文本
     * 4. 首行若为标题则取之
     * 5. null（进入待整理，用兜底名占位）
     */
    fun extractName(rawText: String?, subject: String?, url: String?): String? {
        // 1. subject（分享标题也可能是整句模板，需净化）
        subject?.let { s ->
            val clean = cleanTitle(s)
            if (clean.isNotEmpty() && !clean.startsWith("http")) return clean
        }
        if (rawText != null && url != null) {
            val idx = rawText.indexOf(url)
            // 2. URL 前的标题
            if (idx > 0) {
                val before = rawText.substring(0, idx).trim()
                val title = cleanTitle(before)
                if (title.isNotEmpty()) return title
            }
            // 3. URL 后的第一行非 URL 文本（常见格式：链接换行后店名）
            if (idx >= 0) {
                val after = rawText.substring(idx + url.length)
                val line = after.lineSequence().firstOrNull { l ->
                    l.isNotBlank() && !l.trimStart().startsWith("http")
                }?.trim()
                if (line != null) {
                    val title = cleanTitle(line)
                    if (title.isNotEmpty()) return title
                }
            }
        }
        // 4. 首行若为标题则取之
        val firstLine = rawText?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
        if (!firstLine.isNullOrEmpty() && !firstLine.startsWith("http")) {
            val title = cleanTitle(firstLine)
            if (title.isNotEmpty()) return title
        }
        return null
    }

    /** 店名净化：去平台标签 → 取括号店名 → 截营销尾巴 → 去描述性括号 → 限长。 */
    private fun cleanTitle(raw: String): String {
        var s = raw.replace(Regex("""\s+"""), " ").trim()

        // 1) 整体移除【平台名】标签
        platformTags.forEach { s = s.replace("【$it】", "") }
        s = s.trim()

        // 2) 「」【】中的候选店名优先（点评/美团分享常用「店名(分店)」格式）
        bracketRegex.find(s)?.let { m ->
            val inner = m.groupValues[1].trim()
            if (inner.length in 2..MAX_TITLE_LENGTH && inner !in platformTags) {
                return finalizeTitle(inner)
            }
        }

        // 3) 常见营销前缀
        listOf(
            "发现一个好去处：", "推荐：", "推荐一个好店：",
            "我在大众点评发现了一家不错的店", "发现了一家不错的店",
            "——", "--", "·"
        ).forEach { prefix ->
            s = s.removePrefix(prefix)
        }

        // 4) 营销关键词 / 评分尾巴：最早命中处截断
        val cutIndex = cutMarkers
            .mapNotNull { s.indexOf(it).takeIf { it > 0 } }
            .plus(listOfNotNull(ratingRegex.find(s)?.range?.first?.takeIf { it > 0 }))
            .minOrNull()
        if (cutIndex != null) s = s.substring(0, cutIndex)

        // 5) 平台来源后缀
        s = s.substringBefore(" 来自").trim()
        s = Regex("""[（(]来自.*?[)）]\s*$""").replace(s, "").trim()
        for (p in platformTags) {
            s = s.removeSuffix(" - $p").removeSuffix(" | $p").removeSuffix(" -$p").trim()
        }

        // 6) 描述性括号尾巴（保留分店名）
        s = descriptiveParenRegex.replace(s, "").trim()

        return finalizeTitle(s)
    }

    /** 收尾：去首尾装饰符、再次去描述性括号、限长断句。 */
    private fun finalizeTitle(input: String): String {
        var t = input.trim()
            .trim('·', '-', '—', '：', ':', '，', ',', '。', '！', '!', '~', ' ')
        t = descriptiveParenRegex.replace(t, "").trim()
        if (t.length > MAX_TITLE_LENGTH) {
            val punct = listOf('，', ',', '。', '！', '!', '~', '—', ' ')
            val cut = punct
                .mapNotNull { c -> t.indexOf(c).takeIf { i -> i in 1 until MAX_TITLE_LENGTH } }
                .minOrNull()
            t = if (cut != null) t.substring(0, cut) else t.take(MAX_TITLE_LENGTH)
        }
        return t.trim().trim('·', '-', '—', '：', ':', '，', ',', '。', ' ')
    }
}
