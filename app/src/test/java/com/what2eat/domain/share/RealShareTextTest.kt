package com.what2eat.domain.share

import com.what2eat.domain.model.SourcePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * v0.8.16 诊断：用户提供的真实美团分享原文（含店名）为何识别不出。
 */
class RealShareTextTest {

    private val realText = "【文通冰室（时代长隆店）】快来试试这家餐厅吧！ " +
        "【地址：番禺区汉溪大道东时代芳华里首层112-113号】【电话：020-84887364】" +
        "@美团 `http://dpurl.cn/o022Vckz`"

    @Test
    fun `real meituan text with shop name in brackets`() {
        val url = UrlNormalizer.extractFirstUrl(realText)
        println("URL = $url")
        assertNotNull(url)

        val subject = "来自美团的分享"
        val name = ShareTextParser.extractName(realText, subject, url)
        println("extractName = $name")
        println("looksLikeMetadata = ${name?.let { ShareTextParser.looksLikeMetadata(it) }}")
        println("isPlatformTemplate = ${name?.let { ShareTextParser.isPlatformTemplate(it) }}")

        val draft = ShareTextParser.createDraft(
            rawText = realText,
            subject = subject,
            sourcePackage = "com.sankuai.meituan"
        )
        println("draft.detectedName = ${draft.detectedName}")
        println("draft.detectedUrl = ${draft.detectedUrl}")
        println("draft.detectedPlatform = ${draft.detectedPlatform}")

        assertEquals(SourcePlatform.MEITUAN, draft.detectedPlatform)
        assertEquals("文通冰室（时代长隆店）", draft.detectedName)
    }

    @Test
    fun `same text without subject`() {
        val draft = ShareTextParser.createDraft(
            rawText = realText,
            subject = null,
            sourcePackage = "com.sankuai.meituan"
        )
        println("no-subject detectedName = ${draft.detectedName}")
        assertEquals("文通冰室（时代长隆店）", draft.detectedName)
    }

    @Test
    fun `subject itself is the shop name`() {
        // 另一种可能：EXTRA_SUBJECT 直接是店名
        val draft = ShareTextParser.createDraft(
            rawText = realText,
            subject = "文通冰室（时代长隆店）",
            sourcePackage = "com.sankuai.meituan"
        )
        println("shop-subject detectedName = ${draft.detectedName}")
        assertEquals("文通冰室（时代长隆店）", draft.detectedName)
    }
}
