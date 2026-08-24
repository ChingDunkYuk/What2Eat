package com.what2eat.domain.share

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.model.SourcePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 5：分享导入解析/识别/规范化/重复检测单元测试。
 * 覆盖场景 A-H。
 */
class ShareImportTest {

    // ── 场景 A：浏览器分享普通 URL → URL 识别 ──

    @Test
    fun `A_browser url draft recognizes url and needs name`() {
        val draft = ShareTextParser.createDraft(
            rawText = "https://www.example.com/restaurant/123",
            subject = null,
            sourcePackage = "com.android.browser"
        )
        assertEquals(SourcePlatform.BROWSER, draft.detectedPlatform)
        assertEquals("https://www.example.com/restaurant/123", draft.detectedUrl)
        assertTrue(draft.needsReview) // 无名称 → NEEDS_REVIEW
        assertEquals(com.what2eat.domain.model.ImportStatus.NEEDS_REVIEW, draft.importStatus)
    }

    // ── 场景 B：大众点评分享 → 识别来源，尽量解析 ──

    @Test
    fun `B_dianping share detects platform and title`() {
        val draft = ShareTextParser.createDraft(
            rawText = "潮汕牛肉火锅 https://www.dianping.com/shop/hx58",
            subject = "潮汕牛肉火锅",
            sourcePackage = "com.dianping.v1"
        )
        assertEquals(SourcePlatform.DIANPING, draft.detectedPlatform)
        assertEquals("潮汕牛肉火锅", draft.detectedName)
        assertEquals("https://www.dianping.com/shop/hx58", draft.detectedUrl)
        assertFalse(draft.needsReview)
    }

    // ── 场景 C：美团分享 ──

    @Test
    fun `C_meituan share detects platform`() {
        val draft = ShareTextParser.createDraft(
            rawText = "这家店不错 https://www.meituan.com/restaurant/abc",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals(SourcePlatform.MEITUAN, draft.detectedPlatform)
        assertEquals("https://www.meituan.com/restaurant/abc", draft.detectedUrl)
    }

    // ── 场景 D：高德地图分享 ──

    @Test
    fun `D_amap share detects platform`() {
        val draft = ShareTextParser.createDraft(
            rawText = "位置分享 https://uri.amap.com/marker",
            subject = "某餐厅",
            sourcePackage = null
        )
        assertEquals(SourcePlatform.AMAP, draft.detectedPlatform)
        assertEquals("某餐厅", draft.detectedName)
    }

    // ── 场景 E：纯文字分享 → 无 URL 也可导入 ──

    @Test
    fun `E_plain text imports without url`() {
        val draft = ShareTextParser.createDraft(
            rawText = "XX烧烤",
            subject = null,
            sourcePackage = null
        )
        assertNull(draft.detectedUrl)
        assertEquals(SourcePlatform.OTHER, draft.detectedPlatform)
        assertEquals("XX烧烤", draft.detectedName)
        assertFalse(draft.needsReview)
    }

    // ── 场景 F：只有 URL → NEEDS_REVIEW → 补资料后 COMPLETE ──

    @Test
    fun `F_only_url is needs review`() {
        val draft = ShareTextParser.createDraft(
            rawText = "https://www.example.com/shop/999",
            subject = null,
            sourcePackage = null
        )
        assertNotNull(draft.detectedUrl)
        assertNull(draft.detectedName)
        assertTrue(draft.needsReview)
        assertEquals(com.what2eat.domain.model.ImportStatus.NEEDS_REVIEW, draft.importStatus)
    }

    // ── 场景 G：重复 URL → 检测到重复 ──

    @Test
    fun `G_duplicate url detected`() {
        val existing = SavedOption(
            id = "e1", name = "潮汕牛肉火锅",
            optionType = SavedOptionType.RESTAURANT,
            sourceUrl = "https://www.dianping.com/shop/hx58"
        )
        val result = DuplicateDetector.detect(
            newUrl = "HTTPS://www.dianping.com/shop/hx58",
            newName = "潮汕牛肉火锅",
            newPlatform = SourcePlatform.DIANPING,
            existing = listOf(existing),
            existingCollections = mapOf("e1" to setOf(CollectionType.WANT_TO_TRY))
        )
        assertTrue(result is DuplicateCheckResult.Duplicate)
        val dup = result as DuplicateCheckResult.Duplicate
        assertEquals("e1", dup.existing.id)
        assertEquals(setOf(CollectionType.WANT_TO_TRY), dup.collections)
    }

    // ── 场景 H：无效 URL → 不闪退 ──

    @Test
    fun `H_invalid url does not crash`() {
        val draft = ShareTextParser.createDraft(
            rawText = "这不是一个链接：http://",
            subject = null,
            sourcePackage = null
        )
        assertNull(draft.detectedUrl)
        assertFalse(draft.needsReview) // 有名称，无 URL 也正常
    }

    // ── URL 规范化 ──

    @Test
    fun `url normalize lowercase scheme host and strip tracking`() {
        val norm = UrlNormalizer.normalize("HTTPS://WWW.DianPing.com/shop/1?utm_source=weixin&from=app#frag")
        assertEquals("https://www.dianping.com/shop/1", norm)
    }

    @Test
    fun `url keep meaningful query params`() {
        val norm = UrlNormalizer.normalize("https://www.meituan.com/r/123?shopid=88&utm_medium=share")
        assertEquals("https://www.meituan.com/r/123?shopid=88", norm)
    }

    // ── 默认值规则 ──

    @Test
    fun `defaults for map platforms restaurant want-to-try`() {
        assertEquals(SavedOptionType.RESTAURANT, ShareImportDefaults.defaultType(SourcePlatform.DIANPING))
        assertEquals(SavedOptionType.RESTAURANT, ShareImportDefaults.defaultType(SourcePlatform.AMAP))
        assertEquals(setOf(CollectionType.WANT_TO_TRY), ShareImportDefaults.defaultCollections(SourcePlatform.DIANPING))
    }

    @Test
    fun `defaults for plain text require user choice`() {
        assertNull(ShareImportDefaults.defaultType(SourcePlatform.OTHER))
        assertTrue(ShareImportDefaults.defaultCollections(SourcePlatform.OTHER).isEmpty())
    }

    // ── Stage 5 增强：SourceIdExtractor ──

    @Test
    fun `extract id from dianping shop path`() {
        assertEquals("hx58", SourceIdExtractor.extractId("https://www.dianping.com/shop/hx58"))
        assertEquals("123", SourceIdExtractor.extractId("https://m.dianping.com/i/shop/123"))
    }

    @Test
    fun `extract id from meituan path`() {
        assertEquals("456", SourceIdExtractor.extractId("https://www.meituan.com/restaurant/456"))
        assertEquals("789", SourceIdExtractor.extractId("https://www.meituan.com/r/789?utm_source=share"))
    }

    @Test
    fun `extract id from amap query`() {
        assertEquals("poi-abc", SourceIdExtractor.extractId("https://uri.amap.com/marker?position=116.3,39.9&id=poi-abc&name=XX"))
    }

    @Test
    fun `extract id from baidu uid`() {
        assertEquals("uid-99", SourceIdExtractor.extractId("https://map.baidu.com/?qt=con&uid=uid-99"))
        assertEquals("short1", SourceIdExtractor.extractId("https://j.map.baidu.com/short1"))
    }

    @Test
    fun `extract id from generic shopid query`() {
        assertEquals("88", SourceIdExtractor.extractId("https://www.example.com/r/1?shopid=88&from=app"))
        assertNull(SourceIdExtractor.extractId("https://www.example.com/page"))
    }

    // ── Stage 5 增强：平台+ID 重复检测 ──

    @Test
    fun `duplicate detected by platform and id with different urls`() {
        val existing = SavedOption(
            id = "e1", name = "潮汕牛肉火锅",
            optionType = SavedOptionType.RESTAURANT,
            sourcePlatform = SourcePlatform.DIANPING,
            sourceUrl = "https://www.dianping.com/shop/hx58"
        )
        // 不同子域/路径前缀，但业务 ID 相同
        val result = DuplicateDetector.detect(
            newUrl = "https://m.dianping.com/i/shop/hx58?from=weixin",
            newName = "潮汕牛肉火锅",
            newPlatform = SourcePlatform.DIANPING,
            existing = listOf(existing),
            existingCollections = mapOf("e1" to setOf(CollectionType.WANT_TO_TRY))
        )
        assertTrue(result is DuplicateCheckResult.Duplicate)
    }

    @Test
    fun `duplicate not triggered across platforms with same id`() {
        val existing = SavedOption(
            id = "e1", name = "美团潮汕牛肉火锅",
            optionType = SavedOptionType.RESTAURANT,
            sourcePlatform = SourcePlatform.MEITUAN,
            sourceUrl = "https://www.meituan.com/restaurant/456"
        )
        val result = DuplicateDetector.detect(
            newUrl = "https://www.dianping.com/shop/456",
            newName = "点评潮汕牛肉火锅",
            newPlatform = SourcePlatform.DIANPING,
            existing = listOf(existing),
            existingCollections = emptyMap()
        )
        assertTrue(result is DuplicateCheckResult.NoDuplicate)
    }

    // ── Stage 5 增强：名称规范化相似匹配 ──

    @Test
    fun `duplicate detected by normalized name ignoring whitespace and punctuation`() {
        val existing = SavedOption(
            id = "e1", name = "潮汕牛肉火锅",
            optionType = SavedOptionType.RESTAURANT
        )
        val result = DuplicateDetector.detect(
            newUrl = null,
            newName = "潮汕 牛肉火锅",
            newPlatform = null,
            existing = listOf(existing),
            existingCollections = mapOf("e1" to setOf(CollectionType.WANT_TO_TRY))
        )
        assertTrue(result is DuplicateCheckResult.Duplicate)
    }

    // ── Stage 5 增强：名称提取 ──

    @Test
    fun `name extracted from text after url`() {
        val draft = ShareTextParser.createDraft(
            rawText = "https://www.meituan.com/restaurant/123\n潮汕牛肉火锅（这家店超好吃）",
            subject = null,
            sourcePackage = null
        )
        assertEquals("潮汕牛肉火锅（这家店超好吃）", draft.detectedName)
        assertFalse(draft.needsReview)
    }

    @Test
    fun `name stripped of platform suffix`() {
        val draft = ShareTextParser.createDraft(
            rawText = "潮汕牛肉火锅 - 大众点评\nhttps://www.dianping.com/shop/hx58",
            subject = null,
            sourcePackage = "com.dianping.v1"
        )
        assertEquals("潮汕牛肉火锅", draft.detectedName)
    }

    // ── Stage 5 增强：外卖类型默认值 ──

    @Test
    fun `default type takeout when text contains delivery keyword`() {
        assertEquals(
            SavedOptionType.TAKEOUT_STORE,
            ShareImportDefaults.defaultType(SourcePlatform.MEITUAN, "这家店可以外卖 https://www.meituan.com/r/1")
        )
        assertEquals(
            SavedOptionType.TAKEOUT_STORE,
            ShareImportDefaults.defaultType(SourcePlatform.DIANPING, "支持配送")
        )
    }

    @Test
    fun `default type restaurant when no delivery keyword`() {
        assertEquals(
            SavedOptionType.RESTAURANT,
            ShareImportDefaults.defaultType(SourcePlatform.MEITUAN, "这家店不错 https://www.meituan.com/r/1")
        )
    }
}