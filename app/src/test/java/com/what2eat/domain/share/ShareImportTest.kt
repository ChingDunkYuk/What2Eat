package com.what2eat.domain.share

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.model.SourcePlatform
import com.what2eat.domain.foodpool.FoodOptionForm
import com.what2eat.domain.foodpool.FoodPoolFilter
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
        // 描述性括号（这家店超好吃）被丢弃，只留主店名
        assertEquals("潮汕牛肉火锅", draft.detectedName)
        assertFalse(draft.needsReview)
    }

    // ── 店名净化：真实分享模板提取主店名 ──

    @Test
    fun `dianping template name from bracket`() {
        val draft = ShareTextParser.createDraft(
            rawText = "我在大众点评发现了一家不错的店【小肥羊(望京店)】，快来看看吧 https://m.dianping.com/shop/x1",
            subject = null,
            sourcePackage = "com.dianping.v1"
        )
        assertEquals("小肥羊(望京店)", draft.detectedName)
    }

    @Test
    fun `meituan template name cut at rating and sales`() {
        val draft = ShareTextParser.createDraft(
            rawText = "【美团】小龙坎火锅(合生汇店) 4.8分 月售2000+ 距离你880m，快去买单吧 https://tb.htuiot.com/x",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals("小龙坎火锅(合生汇店)", draft.detectedName)
    }

    @Test
    fun `subject is cleaned too`() {
        val draft = ShareTextParser.createDraft(
            rawText = "https://m.dianping.com/shop/x2",
            subject = "我在大众点评发现了一家不错的店【海底捞(西单店)】，快来看看吧",
            sourcePackage = "com.dianping.v1"
        )
        assertEquals("海底捞(西单店)", draft.detectedName)
    }

    @Test
    fun `branch name in parentheses is kept`() {
        val draft = ShareTextParser.createDraft(
            rawText = "海底捞火锅（西单大悦城店） https://m.dianping.com/shop/x3",
            subject = null,
            sourcePackage = "com.dianping.v1"
        )
        assertEquals("海底捞火锅（西单大悦城店）", draft.detectedName)
    }

    @Test
    fun `long marketing sentence is truncated to 30 chars`() {
        val draft = ShareTextParser.createDraft(
            rawText = "这家藏在胡同深处的宝藏小店真的太好吃了我每次去都要排队两个小时才吃得上 https://m.dianping.com/shop/x4",
            subject = null,
            sourcePackage = "com.dianping.v1"
        )
        val name = draft.detectedName!!
        assertTrue("长度应不超过30: ${name.length}", name.length <= 30)
        // 应在标点/营销词处断句，而非整句照抄
        assertFalse(name.contains("才吃得上"))
    }

    @Test
    fun `extraction result never exceeds 30 chars`() {
        val draft = ShareTextParser.createDraft(
            rawText = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十 https://m.dianping.com/shop/x5",
            subject = null,
            sourcePackage = null
        )
        assertTrue(draft.detectedName!!.length <= 30)
    }

    // ── 美团分享：店名与「地址：」等元信息行混排 ──

    @Test
    fun `meituan multiline share skips address line and picks shop name`() {
        val draft = ShareTextParser.createDraft(
            rawText = "【美团】台屿·台湾食堂\n地址：番禺区兴南大道与万博二路交汇处锦麟万博L1-L2\n电话：020-39170000\nhttps://tb.htuiot.com/Qr4WxPm",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals("台屿·台湾食堂", draft.detectedName)
    }

    @Test
    fun `meituan address only share yields null name for inbox fallback`() {
        val draft = ShareTextParser.createDraft(
            rawText = "https://tb.htuiot.com/Ab2CdE\n地址：番禺区奥园城市天地五区4栋1楼\n营业时间：10:00-22:00",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        // 全是元信息行，提不出店名 → 收件箱兜底名占位
        assertNull(draft.detectedName)
        assertTrue(draft.needsReview)
    }

    @Test
    fun `shop name and address in same line cut at address`() {
        val draft = ShareTextParser.createDraft(
            rawText = "探鲜记顺德桑拿鸡鱼·蒸汽海鲜(汉溪长隆店) 地址：番禺区奥园城市天地五区4栋1楼 https://tb.htuiot.com/Qq1Zz",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals("探鲜记顺德桑拿鸡鱼·蒸汽海鲜(汉溪长隆店)", draft.detectedName)
    }

    @Test
    fun `name line with label prefix is extracted`() {
        val draft = ShareTextParser.createDraft(
            rawText = "店名：小肥羊(望京店)\n地址：朝阳区望京西路 https://tb.htuiot.com/Zz9",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals("小肥羊(望京店)", draft.detectedName)
    }

    @Test
    fun `marketing header line loses to shop name line`() {
        val draft = ShareTextParser.createDraft(
            rawText = "我在美团发现一家宝藏店，快来\n探鲜记顺德桑拿鸡鱼·蒸汽海鲜(汉溪长隆店)\n地址：番禺区 https://tb.htuiot.com/Mm3",
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        assertEquals("探鲜记顺德桑拿鸡鱼·蒸汽海鲜(汉溪长隆店)", draft.detectedName)
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

    // ── 收件箱模式：兜底名称/类型 ──

    @Test
    fun `fallback name uses platform label for meituan and dianping`() {
        assertEquals("来自美团的分享", ShareImportDefaults.fallbackName(SourcePlatform.MEITUAN))
        assertEquals("来自大众点评的分享", ShareImportDefaults.fallbackName(SourcePlatform.DIANPING))
        assertEquals("来自高德地图的分享", ShareImportDefaults.fallbackName(SourcePlatform.AMAP))
    }

    @Test
    fun `fallback name for unknown platform is generic`() {
        assertEquals("待整理的分享", ShareImportDefaults.fallbackName(SourcePlatform.OTHER))
        assertEquals("待整理的分享", ShareImportDefaults.fallbackName(SourcePlatform.NONE))
    }

    @Test
    fun `inbox type never null even for plain text`() {
        assertEquals(
            SavedOptionType.RESTAURANT,
            ShareImportDefaults.inboxType(SourcePlatform.OTHER, null)
        )
        assertEquals(
            SavedOptionType.TAKEOUT_STORE,
            ShareImportDefaults.inboxType(SourcePlatform.MEITUAN, "这家可以外卖配送")
        )
    }

    // ── 收件箱模式：分享进来一律先进待整理，整理保存后转正式 ──

    @Test
    fun `inbox item saved as needs review then completed after organize`() {
        // 1) 收件：分享保存 → NEEDS_REVIEW（ShareImportViewModel 语义，此处验证表单构建链路）
        val draft = ShareTextParser.createDraft(
            rawText = "https://www.dianping.com/shop/hx58",
            subject = null,
            sourcePackage = "com.dianping.v1"
        )
        assertEquals(ImportStatus.NEEDS_REVIEW, draft.importStatus)

        val inbox = FoodOptionForm.buildOption(
            existing = null,
            name = draft.detectedName ?: ShareImportDefaults.fallbackName(draft.detectedPlatform),
            type = ShareImportDefaults.inboxType(draft.detectedPlatform, draft.rawText),
            areaText = null,
            priceLevel = null,
            estimatedMinutes = null,
            notes = null,
            sourceUrl = draft.detectedUrl,
            enabled = true,
            markCompleted = false
        ).copy(
            sourcePlatform = draft.detectedPlatform,
            sourcePackage = draft.sourcePackage,
            importStatus = ImportStatus.NEEDS_REVIEW
        )
        assertEquals(ImportStatus.NEEDS_REVIEW, inbox.importStatus)
        assertEquals("来自大众点评的分享", inbox.name)

        // 2) 整理：编辑保存（markCompleted=true）→ COMPLETE，移出待整理
        val organized = FoodOptionForm.buildOption(
            existing = inbox,
            name = "潮汕牛肉火锅",
            type = SavedOptionType.RESTAURANT,
            areaText = null,
            priceLevel = null,
            estimatedMinutes = null,
            notes = null,
            sourceUrl = inbox.sourceUrl,
            enabled = true,
            markCompleted = true
        )
        assertEquals(ImportStatus.COMPLETE, organized.importStatus)
        assertEquals("潮汕牛肉火锅", organized.name)
        // 待整理 Tab 过滤：整理前命中，整理后不再命中
        assertTrue(FoodPoolFilter.matchesTab(inbox, emptySet(), "NEEDS_REVIEW"))
        assertFalse(FoodPoolFilter.matchesTab(organized, emptySet(), "NEEDS_REVIEW"))
    }
}