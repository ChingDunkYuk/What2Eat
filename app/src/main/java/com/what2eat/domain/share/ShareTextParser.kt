package com.what2eat.domain.share

import com.what2eat.domain.model.SourcePlatform
import java.net.URLDecoder

/**
 * 分享文本解析（名称"尽力而为"提取）。
 *
 * 名称优先级：1. EXTRA_SUBJECT；2. URL 前各行；3. URL 后各行；4. 全文首行；
 * 5. URL query 店名参数（shopName/poiName 等）；6. 留空兜底。
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
 *
 * v0.8.2 增强：
 * - [extractNameFromUrlQuery]：URL query 中的店名参数（shopName/poiName/title 等）解码提取（第 5 优先级）；
 * - 名称彻底解析失败时，原文进备注（诊断通道：信息不丢，且后续排查可直接看条目）。
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

    /** 名称彻底解析失败时，原文进备注的长度上限（诊断通道，防超长文本撑爆备注栏） */
    private const val RAW_TEXT_NOTES_LIMIT = 500

    /** URL query 中常见的店名参数名（美团/点评等分享链接携带） */
    private val urlNameKeys = listOf(
        "shopName", "shop_name", "shopTitle", "shop_title",
        "poiName", "poi_name", "storeName", "store_name",
        "brandName", "brand_name", "merchantName", "merchant_name", "title"
    )

    /**
     * 从 Intent 数据生成 ShareImportDraft。
     *
     * @param rawText 分享文本（EXTRA_TEXT 或 ClipData）
     * @param subject 分享标题（EXTRA_SUBJECT）
     * @param sourcePackage 来源包名（可空）
     */
    fun createDraft(rawText: String?, subject: String?, sourcePackage: String?): ShareImportDraft {
        // 入口统一空白规范化（NBSP/全角空格 → 半角）
        val text = normalizeWhitespace(rawText?.trim().orEmpty())
        val url = UrlNormalizer.extractFirstUrl(text)
        val platform = PlatformRecognizer.detect(sourcePackage, url, text)
        // 最后一道防线：任何路径产出的结果像元信息（地址/电话类）或平台模板文案都不是店名
        val name = extractName(text, subject, url)
            ?.takeUnless { looksLikeMetadata(it) }
            ?.takeUnless { isPlatformTemplate(it) }
            // v0.8.2：文本解析不出时，从 URL query 店名参数兜底
            ?: url?.let { extractNameFromUrlQuery(it) }
        return ShareImportDraft(
            rawText = text,
            subject = subject,
            sourcePackage = sourcePackage,
            detectedPlatform = platform,
            detectedUrl = url,
            detectedName = name,
            // v0.8.2 诊断通道：名称彻底解析失败 → 原文进备注（信息不丢，排查时直接看条目）；
            // 解析成功 → 维持 v0.7.8 的地址/电话行预填
            detectedNotes = if (name == null && text.isNotBlank()) {
                text.take(RAW_TEXT_NOTES_LIMIT)
            } else {
                extractInfoNotes(text)
            }
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
        // 1. subject（分享标题也可能是整句模板，需净化；地址/电话类元信息/平台模板标题
        //    拒绝后**继续走正文**——v0.8.16 修复：此前模板 subject 提前 return，
        //    堵死了正文店名解析（真实案例：subject=「来自美团的分享」导致正文里的
        //    「【文通冰室（时代长隆店）】」永远不被读取，名称栏空转后落到兜底名）
        subject?.let { s ->
            val clean = cleanTitle(normalizeWhitespace(s))
            if (clean.length >= 2 && !clean.startsWith("http") && !looksLikeMetadata(clean) &&
                !isPlatformTemplate(clean)
            ) return clean
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
            // 平台模板文案（「来自美团的分享」等）不是店名（v0.8.13）
            if (isPlatformTemplate(cleaned)) continue

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

    /**
     * 平台分享模板文案（v0.8.13）：不是店名，命中即拒绝。
     *
     * 真实回归：美团分享的 EXTRA_SUBJECT 是「来自美团的分享」，正文也常含该行——
     * 旧护栏全部穿透（cleanTitle 的「 来自」后缀剥离只处理「店名 来自xx」格式，
     * 不处理以「来自」开头的整句），导致名称栏被填成模板文案，
     * 且 detectedName 非空让链接标题抓取（真正的店名来源）根本不触发。
     * 拒掉模板 → detectedName 为 null → 触发抓取补真名。
     * 正则以「的分享/分享了」收尾，真店名（如「来自大自然的馈赠」）不受影响。
     */
    private val platformTemplateRegex = Regex(
        """来自(?:美团|大众点评|点评|饿了么|口碑|高德(?:地图)?|百度地图)?的分享""" +
            """|(?:美团|大众点评|点评)分享了(?:这家店|一家)"""
    )

    /** 是否为平台分享模板文案（供 extractName/createDraft/cleanWebTitle 三处护栏复用）。 */
    internal fun isPlatformTemplate(text: String): Boolean =
        platformTemplateRegex.containsMatchIn(normalizeWhitespace(text).trim())

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
     * v0.8.2：从 URL query 提取店名参数（第 5 优先级兜底）。
     *
     * 美团/点评等分享链接常携带 shopName=/poiName=/title= 等结构化店名参数，
     * 值可能 URL 编码。解码 → [cleanTitle] 净化 → [looksLikeMetadata] 护栏。
     * 无命中参数或值无效返回 null。
     */
    internal fun extractNameFromUrlQuery(url: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val key = pair.substringBefore('=').trim()
            if (key !in urlNameKeys) continue
            val encoded = pair.substringAfter('=', "")
            if (encoded.isEmpty()) continue
            val value = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull() ?: continue
            val cleaned = cleanTitle(value)
            if (cleaned.length >= 2 && !cleaned.startsWith("http") && !looksLikeMetadata(cleaned)) {
                return cleaned
            }
        }
        return null
    }

    /** 反爬验证页标题特征（v0.8.5：命中即拒绝，避免「身份核实」等验证页标题被当店名） */
    private val captchaTitleRegex = Regex("""身份核实|安全验证|人机验证|滑动验证|验证中心|登录环境异常|检测到当前登录环境异常""")

    /**
     * 登录页标题特征（v1.1.2：命中即拒绝）。
     * SPA 数据接口 401 会把页面跳去 passport 登录页——「美团网账号登录-手机美团官网」
     * 曾穿透全部护栏被当店名返回（还因返回非空短路了后续候选页）。
     * v1.1.3 泛化为单字「登录」：i.meituan 的 mttouch 登录页标题只写「登录」也能漏网；
     * 真实店名不可能含「登录」（本正则仅用于网页标题，不影响分享文本解析）。
     */
    private val loginTitleRegex = Regex("""登录""")

    /**
     * 站点/壳页标题（v0.8.9，**整串相等**才拒绝——不能用包含匹配，
     * 否则误杀「店名_大众点评网」这类真实标题）：
     * - 「大众点评网/美团网」——点评桌面店铺页 301 到登录页的 <title>
     *   （v0.8.8 该标题穿透护栏被误当店名，用户名称栏被填「大众点评网」）；
     * - 「商家详情/店铺详情」——meishi.meituan.com POI H5 的 SPA 壳固定标题；
     * - 「页面不存在/加载失败」——无效 poiId 命中 404/错误页。
     */
    private val siteShellTitles = setOf(
        "大众点评网", "美团网", "美团外卖网", "商家详情", "店铺详情", "页面不存在", "加载失败", "加载中"
    )

    /**
     * 死路整串标题（v1.1.3）：这些页面连 DOM/接口都不可能出店名，命中即拒 + 快速失败。
     * 「温馨提示」——static.meituan.net/bs/mbs-pages upgrader 升级提示页标题
     * （meishi SPA 嫌 WebView「浏览器太旧」跳过去，曾穿透护栏被当店名返回）。
     * 与 siteShellTitles 的区别：壳页（商家详情）只是标题无用、DOM 还可能出店名，不算死路。
     */
    private val deadEndTitles = setOf("温馨提示")

    /**
     * 通用落地页/营销页标题特征（包含匹配即拒绝，都不会出现在真实店名里）：
     * v0.8.8：唤起页营销语、活动结束页、App 下载引导页；
     * v0.8.9：美团 H5 账号安全检查页文案（无登录态时 POI 页渲染成该页）。
     */
    private val genericTitleRegex = Regex(
        """问美团|都安排|和美团合作|活动已结束|打开App|下载App|打开美团|打开大众点评|下载美团|下载大众点评|是我的账号|不是我的账号"""
    )

    /**
     * 网页 <title> 清洗（供链接标题抓取复用）：
     * 空/URL/元信息/纯平台名/站点壳页/反爬验证页/营销落地页 → null；
     * 复用 [cleanTitle] 净化与 [looksLikeMetadata] 护栏。
     */
    /** URL 特征（无 http 前缀的裸域名/路径也算——v1.1.0 修复 dpurl.cn/xxx 穿透护栏被当店名） */
    private val looksLikeUrlRegex = Regex(
        """^(?:https?://)?[a-z0-9-]+(?:\.[a-z0-9-]+)+(?:/\S*)?$""",
        RegexOption.IGNORE_CASE
    )

    fun cleanWebTitle(raw: String): String? {
        if (raw.isBlank()) return null
        val cleaned = cleanTitle(normalizeWhitespace(raw))
        if (cleaned.length < 2 || cleaned.startsWith("http")) return null
        if (looksLikeUrlRegex.matches(cleaned.trim())) return null
        if (looksLikeMetadata(cleaned)) return null
        if (isPlatformTemplate(cleaned)) return null
        if (cleaned in platformTags) return null
        if (cleaned in siteShellTitles) return null
        if (cleaned in deadEndTitles) return null
        if (captchaTitleRegex.containsMatchIn(cleaned)) return null
        if (loginTitleRegex.containsMatchIn(cleaned)) return null
        if (genericTitleRegex.containsMatchIn(cleaned)) return null
        return cleaned
    }

    /**
     * 死路标题（v1.1.2）：反爬验证页/登录页——这些页面不可能产出店名，
     * WebView 抓取命中即快速失败（不再空等到超时，单候选两次重试省约 20 秒）。
     * 注意：SPA 壳页标题（商家详情等）不在此列——壳页的 DOM/数据接口仍可能出店名。
     */
    fun isDeadEndWebTitle(raw: String): Boolean {
        if (raw.isBlank()) return false
        val cleaned = cleanTitle(normalizeWhitespace(raw))
        return cleaned in deadEndTitles ||
            captchaTitleRegex.containsMatchIn(cleaned) || loginTitleRegex.containsMatchIn(cleaned)
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
