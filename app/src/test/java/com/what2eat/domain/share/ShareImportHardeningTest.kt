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
}
