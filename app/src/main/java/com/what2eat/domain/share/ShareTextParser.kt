package com.what2eat.domain.share

import com.what2eat.domain.model.SourcePlatform

/**
 * 分享文本解析（名称"尽力而为"提取）。
 *
 * 名称优先级：1. EXTRA_SUBJECT；2. URL 前各行；3. URL 后各行；4. 全文首行；5. 留空兜底。
 *
 * 逐行扫描 + 噪声行过滤（美团/点评分享模板里店名与元信息混排）：
 * - 「地址：/电话：/营业时间：/人均：…」等元信息行直接跳过，不当店名（含「门店地址：」等前缀变体）；
 * - 「」【】中的候选店名优先（平台标签【美团】除外）；
 * - 命中营销话术/评分/销量/距离/地址等关键词即截断；
 * - 丢弃描述性括号尾巴（这家店超好吃），保留分店名（望京店）；
 * - 多行候选按打分择优（含括号店名、短、无营销词优先）；
 * - 超长时先按标点断句，再硬截断到 30 字。
 *
 * v0.7.8 加固：
 * - NBSP/全角空格入口统一规范化（\s 与 trim 均不覆盖它们）；
 * - 元信息标签后缀匹配（「门店地址」endsWith「地址」）；
 * - [looksLikeMetadata] 最终护栏：subject/逐行/整体三道防线，地址电话类结果一律拒绝；
 * - [extractInfoNotes]：地址/电话/营业时间行提取为备注预填；
 * - [cleanWebTitle]：网页 <title> 净化（供链接标题抓取复用）。
 */
object ShareTextParser {

    /** 平台标签（【】中的平台名，不是店名） */
    private val platformTags = listOf(
        "美团外卖", "大众点评", "美团", "饿了么", "高德地图", "百度地图", "口碑"
    )

    /** NBSP(U+00A0)/全角空格(U+3000) → 半角空格（\s 与 String.trim 均不覆盖它们）。 */
    internal fun normalizeWhitespace(s: String): String =
        s.replace(Char(0x00A0), ' ').replace(Char(0x3000), ' ')

    /** 元信息行标签：「地址：番禺…」「电话：020…」这类行不含店名（店名：/名称： 是店名行，不在此列） */
    private val metadataLabels = listOf(
        "地址", "电话", "联系电话", "营业时间", "营业", "人均", "人均消费", "评分",
        "距离", "推荐菜", "招牌菜", "菜品", "招牌", "特色", "分类", "标签",
        "起送", "配送费", "配送", "优惠", "活动", "导航", "位置", "门店",
        "商家", "商户", "网址", "链接", "分享"
    )

    /** 行首元信息：「标签：」其中标签 ≤ 5 字（容忍 NBSP/全角空格前缀） */
    private val metadataLineRegex = Regex("""^[\s 　]*([^：:，,。！!~\s]{1,5})\s*[：:]""")

    /** 明确的泄漏前缀（startsWith 检查用；刻意收窄：不含「特色/招牌/菜品/链接/分享/标签」等可能是真实店名/文案的词） */
    private val leakPrefixes = listOf(
        "地址", "电话", "联系电话", "营业时间", "营业", "人均", "评分",
        "距离", "门店", "商家", "商户", "网址", "导航", "位置", "起送", "配送费", "配送"
    )

    /**
     * 「标签：」样式检查用的标签集（非锚定匹配误杀风险更高，比 leakPrefixes 更收窄：
     * 剔除「位置」，补充「人均消费」；绝不含「链接/分享/标签/特色/招牌/菜品」——
     * 「这不是一个链接：http://」这类正常文案会被宽名单误杀）。
     */
    private val leakColonLabels = listOf(
        "地址", "电话", "联系电话", "营业时间", "营业", "人均", "人均消费", "评分",
        "距离", "门店", "商家", "商户", "网址", "导航", "起送", "配送费", "配送"
    )

    /** 「标签：」样式（非锚定，仅配合收窄的 leakColonLabels 使用） */
    private val leakLabelColonRegex = Regex("""([^：:，,。！!~\s]{1,5})[：:]""")

    /** 备注行标签（仅联系类元信息；endsWith 覆盖「门店地址/商家电话」等变体） */
    private val notesLabels = listOf("地址", "电话", "联系电话", "营业时间", "营业")

    /** 营销/元信息关键词：命中位置即截断，其后内容不属于店名 */
    private val cutMarkers = listOf(
        "快来", "快看", "看看", "试试", "推荐", "安利", "分享给", "分享自",
        "复制", "打开", "点击", "选购", "立即", "下单", "领券", "优惠券", "红包",
        "月售", "人均", "好评", "评分", "榜单", "排名",
        "满减", "立减", "半价", "包邮", "折扣",
        "距离你", "距离您", "距你", "距您", "附近", "导航",
        // 美团/点评模板中的地址、电话等（行中命中即截断）
        "地址", "电话", "营业", "起送", "配送", "招牌"
    )

    /** 评分尾巴：4.8分 / 4.8 分 */
    private val ratingRegex = Regex("""\d+(?:\.\d+)?\s*分""")

    /** 「」『』【】中的候选店名 */
    private val bracketRegex = Regex("""[【「『]([^【」』]{2,32})[】」』]""")

    /** 行尾分店名「(汉溪长隆店)」：强店名信号 */
    private val branchNameRegex = Regex("""[（(][^（）()]{1,12}店[）)]\s*$""")

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
        // 入口统一空白规范化（NBSP/全角空格 → 半角）
        val text = normalizeWhitespace(rawText?.trim().orEmpty())
        val url = UrlNormalizer.extractFirstUrl(text)
        val platform = PlatformRecognizer.detect(sourcePackage, url, text)
        // 最后一道防线：任何路径产出的结果像元信息（地址/电话类）都不是店名
        val name = extractName(text, subject, url)?.takeUnless { looksLikeMetadata(it) }
        return ShareImportDraft(
            rawText = text,
            subject = subject,
            sourcePackage = sourcePackage,
            detectedPlatform = platform,
            detectedUrl = url,
            detectedName = name,
            detectedNotes = extractInfoNotes(text)
        )
    }

    /**
     * 名称提取（尽力而为）。
     * 1. subject 非空、含 ≥2 字、非 URL 且不像元信息 → subject（净化后）
     * 2. URL 前各行中择优
     * 3. URL 后各行中择优
     * 4. 全文各行中择优
     * 5. null（进入待整理，用兜底名占位）
     */
    fun extractName(rawText: String?, subject: String?, url: String?): String? {
        // 1. subject（分享标题也可能是整句模板，需净化；地址/电话类元信息标题拒绝）
        subject?.let { s ->
            val clean = cleanTitle(normalizeWhitespace(s))
            if (clean.length >= 2 && !clean.startsWith("http") && !looksLikeMetadata(clean)) return clean
        }
        val text = rawText?.let { normalizeWhitespace(it) }
        if (text != null) {
            if (url != null) {
                val idx = text.indexOf(url)
                if (idx >= 0) {
                    // 2. URL 前各行（店名通常在链接上方）
                    pickNameLine(text.substring(0, idx).lines())?.let { return it }
                    // 3. URL 后各行（链接在前、店名在后的格式）
                    pickNameLine(text.substring(idx + url.length).lines())?.let { return it }
                }
            }
            // 4. 全文兜底
            pickNameLine(text.lines())?.let { return it }
        }
        return null
    }

    /**
     * 逐行择店名：跳过空行/URL/元信息行，净化后打分取最优。
     * 打分：含「」【】店名 +4；行尾分店名(xx店) +3；短(≤24) +2；无营销词 +1；无元信息前缀 +1；
     * 行内出现营销话术 -3（整句安利文案不是店名）。
     * 同分取先出现的行（符合分享文本排版习惯）。
     */
    private fun pickNameLine(lines: List<String>): String? {
        var best: String? = null
        var bestScore = -1
        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("http")) continue
            if (isNoiseLine(t)) continue
            val cleaned = cleanTitle(t)
            if (cleaned.length < 2) continue
            // 截断残片（如「门店」）或地址电话类结果不是店名
            if (looksLikeMetadata(cleaned)) continue

            var score = 0
            if (hasBracketName(t)) score += 4
            if (branchNameRegex.containsMatchIn(t)) score += 3
            if (cleaned.length <= 24) score += 2
            if (cutMarkers.none { cleaned.contains(it) }) score += 1
            if (metadataLabels.none { cleaned.startsWith(it) }) score += 1
            if (cutMarkers.any { t.contains(it) }) score -= 3
            if (score > bestScore) {
                bestScore = score
                best = cleaned
            }
        }
        return best
    }

    /** 是否为噪声行：营销词开头，或「地址：/电话：」等元信息行（含「门店地址：」等前缀变体）。 */
    private fun isNoiseLine(line: String): Boolean {
        if (cutMarkers.any { line.startsWith(it) }) return true
        val label = metadataLineRegex.find(line)?.groupValues?.get(1)?.trim() ?: return false
        return isMetadataLabel(label)
    }

    /** 元信息标签判定：精确或后缀匹配（「门店地址」endsWith「地址」、「商家电话」endsWith「电话」）。 */
    private fun isMetadataLabel(label: String): Boolean =
        metadataLabels.any { label == it || label.endsWith(it) }

    /**
     * 最终护栏：结果像元信息 → 不是店名。
     * - 空串 / 以泄漏前缀开头（地址、电话、门店…）
     * - 含「标签：」样式且标签命中收窄清单（如「地址：」）
     * 非锚定的「含」检查只认 [leakColonLabels]，绝不含「链接/分享/标签/特色/招牌/菜品」，
     * 否则会误杀「这不是一个链接：http://」这类正常文案。
     */
    internal fun looksLikeMetadata(text: String): Boolean {
        val t = normalizeWhitespace(text).trim()
        if (t.isEmpty()) return true
        if (leakPrefixes.any { t.startsWith(it) }) return true
        return leakLabelColonRegex.findAll(t).any { m ->
            val label = m.groupValues[1].trim()
            leakColonLabels.any { label == it || label.endsWith(it) }
        }
    }

    /** 提取「地址：/电话：/营业时间：」行（保持原文顺序）拼为备注；无则 null。 */
    fun extractInfoNotes(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val lines = normalizeWhitespace(rawText).lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line ->
                val label = metadataLineRegex.find(line)?.groupValues?.get(1)?.trim()
                    ?: return@filter false
                notesLabels.any { label == it || label.endsWith(it) }
            }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /**
     * 网页 <title> 清洗（供链接标题抓取复用）：
     * 空/URL/元信息/纯平台名 → null；复用 [cleanTitle] 净化与 [looksLikeMetadata] 护栏。
     */
    fun cleanWebTitle(raw: String): String? {
        if (raw.isBlank()) return null
        val cleaned = cleanTitle(normalizeWhitespace(raw))
        if (cleaned.length < 2 || cleaned.startsWith("http")) return null
        if (looksLikeMetadata(cleaned)) return null
        if (cleaned in platformTags) return null
        return cleaned
    }

    /** 行内是否有有效的「」【】店名（排除平台标签）。 */
    private fun hasBracketName(line: String): Boolean =
        bracketRegex.findAll(line).any { m ->
            val inner = m.groupValues[1].trim()
            inner.length in 2..MAX_TITLE_LENGTH && inner !in platformTags
        }

    /** 店名净化：去平台标签 → 取括号店名 → 截营销尾巴 → 去描述性括号 → 限长。（internal：cleanWebTitle 复用） */
    internal fun cleanTitle(raw: String): String {
        var s = raw.replace(Regex("""\s+"""), " ").trim()

        // 1) 整体移除【平台名】标签
        platformTags.forEach { s = s.replace("【$it】", "") }
        s = s.trim()

        // 2) 「」【】中的候选店名优先（点评/美团分享常用「店名(分店)」格式）
        bracketRegex.findAll(s).forEach { m ->
            val inner = m.groupValues[1].trim()
            if (inner.length in 2..MAX_TITLE_LENGTH && inner !in platformTags) {
                return finalizeTitle(inner)
            }
        }

        // 3) 常见营销前缀与「店名：」标签前缀
        listOf(
            "店名：", "名称：",
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

        // 5) 平台来源后缀（含网页标题常用的「_平台名」「｜平台名」风格）
        s = s.substringBefore(" 来自").trim()
        s = Regex("""[（(]来自.*?[)）]\s*$""").replace(s, "").trim()
        for (p in platformTags) {
            s = s.removeSuffix(" - $p").removeSuffix(" | $p").removeSuffix(" -$p")
                .removeSuffix("_$p").removeSuffix("｜$p").trim()
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
