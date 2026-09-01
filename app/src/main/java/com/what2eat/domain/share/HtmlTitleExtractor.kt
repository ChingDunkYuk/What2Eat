package com.what2eat.domain.share

/**
 * HTML 标题提取（纯 Kotlin，可单测）。
 *
 * 只做最小可用解析：正则取 <title> + 最小 HTML 实体解码，
 * 净化复用 [ShareTextParser.cleanWebTitle]（平台后缀剥离 + 元信息护栏 + 限长）。
 * 不引入第三方 HTML 解析依赖。
 */
object HtmlTitleExtractor {

    private val titleRegex = Regex(
        """<title[^>]*>(.*?)</title>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    /**
     * 从 HTML 片段提取并净化 <title>；无有效标题返回 null。
     *
     * 实体解码顺序：&amp; 最后（避免「&amp;lt;」被二次解码成「<」）。
     */
    fun parseTitle(html: String): String? {
        val raw = titleRegex.find(html)?.groupValues?.get(1) ?: return null
        return ShareTextParser.cleanWebTitle(decodeEntities(raw))
    }

    /** 最小 HTML 实体解码集（够覆盖标题中常见的转义）。 */
    internal fun decodeEntities(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}
