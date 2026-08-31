package com.what2eat.domain.share

import com.what2eat.domain.model.SourcePlatform

/**
 * 分享文本解析（名称"尽力而为"提取）。
 *
 * 名称优先级：1. EXTRA_SUBJECT；2. URL 前各行；3. URL 后各行；4. 全文首行；5. 留空兜底。
 *
 * 逐行扫描 + 噪声行过滤（美团/点评分享模板里店名与元信息混排）：
 * - 「地址：/电话：/营业时间：/人均：…」等元信息行直接跳过，不当店名；
 * - 「」【】中的候选店名优先（平台标签【美团】除外）；
 * - 命中营销话术/评分/销量/距离/地址等关键词即截断；
 * - 丢弃描述性括号尾巴（这家店超好吃），保留分店名（望京店）；
 * - 多行候选按打分择优（含括号店名、短、无营销词优先）；
 * - 超长时先按标点断句，再硬截断到 30 字。
 */
object ShareTextParser {

    /** 平台标签（【】中的平台名，不是店名） */
    private val platformTags = listOf(
        "美团外卖", "大众点评", "美团", "饿了么", "高德地图", "百度地图", "口碑"
    )

    /** 元信息行标签：「地址：番禺…」「电话：020…」这类行不含店名（店名：/名称： 是店名行，不在此列） */
    private val metadataLabels = listOf(
        "地址", "电话", "联系电话", "营业时间", "营业", "人均", "人均消费", "评分",
        "距离", "推荐菜", "招牌菜", "菜品", "招牌", "特色", "分类", "标签",
        "起送", "配送费", "配送", "优惠", "活动", "导航", "位置", "门店",
        "商家", "商户", "网址", "链接", "分享"
    )

    /** 行首元信息：「标签：」其中标签 ≤ 5 字 */
    private val metadataLineRegex = Regex("""^\s*([^：:，,。！!~\s]{1,5})\s*[：:]""")

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
     * 2. URL 前各行中择优
     * 3. URL 后各行中择优
     * 4. 全文各行中择优
     * 5. null（进入待整理，用兜底名占位）
     */
    fun extractName(rawText: String?, subject: String?, url: String?): String? {
        // 1. subject（分享标题也可能是整句模板，需净化）
        subject?.let { s ->
            val clean = cleanTitle(s)
            if (clean.isNotEmpty() && !clean.startsWith("http")) return clean
        }
        if (rawText != null) {
            if (url != null) {
                val idx = rawText.indexOf(url)
                if (idx >= 0) {
                    // 2. URL 前各行（店名通常在链接上方）
                    pickNameLine(rawText.substring(0, idx).lines())?.let { return it }
                    // 3. URL 后各行（链接在前、店名在后的格式）
                    pickNameLine(rawText.substring(idx + url.length).lines())?.let { return it }
                }
            }
            // 4. 全文兜底
            pickNameLine(rawText.lines())?.let { return it }
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

    /** 是否为噪声行：营销词开头，或「地址：/电话：」等元信息行。 */
    private fun isNoiseLine(line: String): Boolean {
        if (cutMarkers.any { line.startsWith(it) }) return true
        val label = metadataLineRegex.find(line)?.groupValues?.get(1)?.trim() ?: return false
        return label in metadataLabels
    }

    /** 行内是否有有效的「」【】店名（排除平台标签）。 */
    private fun hasBracketName(line: String): Boolean =
        bracketRegex.findAll(line).any { m ->
            val inner = m.groupValues[1].trim()
            inner.length in 2..MAX_TITLE_LENGTH && inner !in platformTags
        }

    /** 店名净化：去平台标签 → 取括号店名 → 截营销尾巴 → 去描述性括号 → 限长。 */
    private fun cleanTitle(raw: String): String {
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
