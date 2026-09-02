package com.what2eat.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.7.8 解析加固单元测试。
 *
 * 背景：v0.7.7 的行级过滤对 NBSP/全角空格前缀的「地址：」行失明，
 * 且缺少最终护栏，导致部分美团分享仍把地址当店名入库。
 */
class ShareImportHardeningTest {

    // ── 空白规范化：NBSP / 全角空格 ──

    @Test
    fun `normalize whitespace converts NBSP and full-width space`() {
        assertEquals("a b", ShareTextParser.normalizeWhitespace("a\u00A0b"))
        assertEquals("a b", ShareTextParser.normalizeWhitespace("a\u3000b"))
    }

    // ── NBSP/全角空格前缀的元信息行不再泄漏为店名 ──

    @Test
    fun `address line with leading NBSP is filtered`() {
        val draft = ShareTextParser.createDraft(
            rawText = "【美团】台屿·台湾食堂\n\u00A0地址：番禺区兴南大道与万博二路交汇处\n电话：020-39170000\nhttps://tb.htuiot.com/Qr4WxPm",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals("台屿·台湾食堂", draft.detectedName)
    }

    @Test
    fun `address line with leading full-width space is filtered`() {
        val draft = ShareTextParser.createDraft(
            rawText = "　地址：番禺区奥园城市天地\n探鲜记顺德桑拿鸡鱼·蒸汽海鲜(汉溪长隆店)\nhttps://tb.htuiot.com/Qq1Zz",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals("探鲜记顺德桑拿鸡鱼·蒸汽海鲜(汉溪长隆店)", draft.detectedName)
    }

    // ── looksLikeMetadata 最终护栏 ──

    @Test
    fun `looks like metadata rejects address and phone prefixes`() {
        assertTrue(ShareTextParser.looksLikeMetadata("地址：番禺区兴南大道"))
        assertTrue(ShareTextParser.looksLikeMetadata("电话：020-39170000"))
        assertTrue(ShareTextParser.looksLikeMetadata("门店地址：番禺区"))
        assertTrue(ShareTextParser.looksLikeMetadata("商家电话：020-3917"))
        assertTrue(ShareTextParser.looksLikeMetadata(""))
    }

    @Test
    fun `looks like metadata keeps normal names and sentences`() {
        assertFalse(ShareTextParser.looksLikeMetadata("台屿·台湾食堂"))
        assertFalse(ShareTextParser.looksLikeMetadata("海底捞火锅（西单大悦城店）"))
        // 正常文案含「链接：」字样但标签不在收窄清单 → 不误杀
        assertFalse(ShareTextParser.looksLikeMetadata("这不是一个链接：http://"))
        assertFalse(ShareTextParser.looksLikeMetadata("招牌菜超好吃的宝藏小店"))
    }

    // ── 护栏兜底：subject 本身是元信息时拒绝 ──

    @Test
    fun `metadata-like subject is rejected`() {
        val draft = ShareTextParser.createDraft(
            rawText = "https://tb.htuiot.com/Ab2CdE\n台屿·台湾食堂",
            subject = "地址：番禺区兴南大道",
            sourcePackage = "com.sankuai.meituan"
        )
        // subject 是地址 → 不采用，落到正文行
        assertEquals("台屿·台湾食堂", draft.detectedName)
    }

    // ── extractInfoNotes：地址/电话/营业时间进备注 ──

    @Test
    fun `info notes extracted in original order`() {
        val notes = ShareTextParser.extractInfoNotes(
            "台屿·台湾食堂\n地址：番禺区兴南大道与万博二路交汇处锦麟万博L1-L2\n电话：020-39170000\n营业时间：10:00-22:00\nhttps://tb.htuiot.com/Qr4WxPm"
        )
        assertEquals(
            "地址：番禺区兴南大道与万博二路交汇处锦麟万博L1-L2\n电话：020-39170000\n营业时间：10:00-22:00",
            notes
        )
    }

    @Test
    fun `info notes null when no metadata lines`() {
        assertNull(ShareTextParser.extractInfoNotes("台屿·台湾食堂\nhttps://tb.htuiot.com/x"))
        assertNull(ShareTextParser.extractInfoNotes(null))
        assertNull(ShareTextParser.extractInfoNotes("  "))
    }

    @Test
    fun `create draft carries detected notes`() {
        val draft = ShareTextParser.createDraft(
            rawText = "https://tb.htuiot.com/Ab2CdE\n地址：番禺区奥园城市天地五区4栋1楼\n营业时间：10:00-22:00",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertNull(draft.detectedName)
        assertNotNull(draft.detectedNotes)
        assertTrue(draft.detectedNotes!!.contains("地址：番禺区奥园城市天地"))
        assertTrue(draft.detectedNotes!!.contains("营业时间：10:00-22:00"))
    }

    // ── cleanWebTitle：网页标题净化（链接标题抓取复用）──

    @Test
    fun `web title strips platform suffix`() {
        assertEquals(
            "小肥羊火锅(望京店)",
            ShareTextParser.cleanWebTitle("小肥羊火锅(望京店)_美团")
        )
        assertEquals(
            "潮汕牛肉火锅",
            ShareTextParser.cleanWebTitle("潮汕牛肉火锅 - 大众点评")
        )
    }

    @Test
    fun `web title rejects metadata url and bare platform`() {
        assertNull(ShareTextParser.cleanWebTitle("地址：番禺区兴南大道"))
        assertNull(ShareTextParser.cleanWebTitle("https://tb.htuiot.com/x"))
        assertNull(ShareTextParser.cleanWebTitle("美团"))
        assertNull(ShareTextParser.cleanWebTitle(""))
        assertNull(ShareTextParser.cleanWebTitle("  "))
    }

    // ── HtmlTitleExtractor：HTML 标题提取 ──

    @Test
    fun `html title extracted and cleaned`() {
        assertEquals(
            "台屿·台湾食堂",
            HtmlTitleExtractor.parseTitle(
                "<html><head><title>台屿·台湾食堂_美团</title></head><body>x</body></html>"
            )
        )
    }

    @Test
    fun `html title decodes entities`() {
        assertEquals(
            "A & B 火锅",
            HtmlTitleExtractor.parseTitle("<title>A &amp; B 火锅</title>")
        )
    }

    @Test
    fun `html title null for missing or invalid title`() {
        assertNull(HtmlTitleExtractor.parseTitle("<html><head></head><body>无标题</body></html>"))
        assertNull(HtmlTitleExtractor.parseTitle("<title>美团</title>"))
        assertNull(HtmlTitleExtractor.parseTitle("<title>地址：番禺区</title>"))
        assertNull(HtmlTitleExtractor.parseTitle(""))
    }

    @Test
    fun `html title is case-insensitive and multiline`() {
        assertEquals(
            "海底捞火锅",
            HtmlTitleExtractor.parseTitle("<TITLE>\n  海底捞火锅\n</TITLE>")
        )
    }

    // ── v0.8.2：URL query 店名提取（第 5 优先级兜底）──

    @Test
    fun `url query shop name extracted and decoded`() {
        assertEquals(
            "喜茶(望京店)",
            ShareTextParser.extractNameFromUrlQuery(
                "https://h5.waimai.meituan.com/shop/123?shopName=%E5%96%9C%E8%8C%B6(%E6%9C%9B%E4%BA%AC%E5%BA%97)&channel=share"
            )
        )
        assertEquals(
            "台屿·台湾食堂",
            ShareTextParser.extractNameFromUrlQuery("https://x.dianping.com/poi?poiName=台屿·台湾食堂")
        )
    }

    @Test
    fun `url query extraction returns null for invalid cases`() {
        // 无 query / 无店名参数
        assertNull(ShareTextParser.extractNameFromUrlQuery("https://h5.waimai.meituan.com/shop/123"))
        assertNull(ShareTextParser.extractNameFromUrlQuery("https://x.com/a?channel=share"))
        // 值是 URL / 元信息 → 拒绝
        assertNull(ShareTextParser.extractNameFromUrlQuery("https://x.com/a?title=https://evil.com"))
        assertNull(ShareTextParser.extractNameFromUrlQuery("https://x.com/a?shopName=地址：番禺区"))
    }

    @Test
    fun `create draft falls back to url query name when text has none`() {
        // 括号用 %28/%29 编码（裸括号会被 URL 提取正则截断）
        val draft = ShareTextParser.createDraft(
            rawText = "https://h5.waimai.meituan.com/shop/123?poiName=%E5%B0%8F%E9%BE%99%E5%9D%8E%E7%81%AB%E9%94%85%28%E6%9C%9B%E4%BA%AC%E5%BA%97%29",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals("小龙坎火锅(望京店)", draft.detectedName)
    }

    // ── v0.8.2：诊断通道——名称彻底解析失败时原文进备注 ──

    @Test
    fun `raw text goes to notes when name unresolved`() {
        val draft = ShareTextParser.createDraft(
            rawText = "复制打开美团App https://tb.htuiot.com/Ab2CdE",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertNull(draft.detectedName)
        // 原文进备注：诊断通道（用户排查时直接看条目即可拿到原始分享文字）
        assertEquals("复制打开美团App https://tb.htuiot.com/Ab2CdE", draft.detectedNotes)
    }

    @Test
    fun `notes stay as metadata lines when name resolved`() {
        val draft = ShareTextParser.createDraft(
            rawText = "台屿·台湾食堂\n地址：番禺区兴南大道\nhttps://tb.htuiot.com/Qr4WxPm",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals("台屿·台湾食堂", draft.detectedName)
        // 名称解析成功 → 维持地址/电话预填（非整段原文）
        assertEquals("地址：番禺区兴南大道", draft.detectedNotes)
    }

    // ── v0.8.2：extractFirstUrl 保留 query（此前半角 ? 被当边界，query 整段丢失）──

    @Test
    fun `url extraction keeps query string`() {
        assertEquals(
            "https://h5.waimai.meituan.com/shop/123?poiName=%E5%B0%8F%E9%BE%99%E5%9D%8E",
            UrlNormalizer.extractFirstUrl(
                "小龙坎火锅 https://h5.waimai.meituan.com/shop/123?poiName=%E5%B0%8F%E9%BE%99%E5%9D%8E 快来看看"
            )
        )
    }

    @Test
    fun `url extraction stops at full-width question mark`() {
        assertEquals(
            "https://h5.waimai.meituan.com/shop/123",
            UrlNormalizer.extractFirstUrl("https://h5.waimai.meituan.com/shop/123？快来看看吧")
        )
    }

    // ── v0.8.2：SchemeUrlExtractor —— App 唤起 scheme 落地页提取 ──

    @Test
    fun `scheme landing url extracted from query param`() {
        assertEquals(
            "https://h5.waimai.meituan.com/shop/123",
            SchemeUrlExtractor.extractLandingUrl(
                "imeituan://funding?url=https%3A%2F%2Fh5.waimai.meituan.com%2Fshop%2F123"
            )
        )
        assertEquals(
            "https://www.meituan.com/page/foodshop?poiId=1",
            SchemeUrlExtractor.extractLandingUrl(
                "imeituan://www.meituan.com/jump?landingUrl=https%3A%2F%2Fwww.meituan.com%2Fpage%2Ffoodshop%3FpoiId%3D1"
            )
        )
    }

    @Test
    fun `trusted scheme host converted to https`() {
        assertEquals(
            "https://www.meituan.com/page/foodshop?poiId=1",
            SchemeUrlExtractor.extractLandingUrl("imeituan://www.meituan.com/page/foodshop?poiId=1")
        )
    }

    @Test
    fun `untrusted scheme yields null`() {
        assertNull(SchemeUrlExtractor.extractLandingUrl("weixin://dl/business?ticket=xxx"))
        assertNull(SchemeUrlExtractor.extractLandingUrl("imeituan://unknown-host/xxx"))
        assertNull(SchemeUrlExtractor.extractLandingUrl(""))
    }
}
