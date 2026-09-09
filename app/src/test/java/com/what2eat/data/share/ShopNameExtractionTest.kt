package com.what2eat.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.1.0：ShopNameExtractor（美团数据接口 JSON 店名提取）单元测试。
 *
 * 覆盖 JS 数据劫持链路里 Kotlin 侧的字段提取逻辑（JS 侧正则与之一致）：
 * poiName / shopName / name 三种字段，及各类脏数据/边界情况。
 */
class ShopNameExtractionTest {

    // 典型美团 POI 接口响应：poiName 字段
    @Test
    fun `extract poiName from typical response`() {
        val json = """{"code":0,"data":{"poiId":1475979044,"poiName":"海底捞火锅(望京店)","avgPrice":120}}"""
        assertEquals("海底捞火锅(望京店)", ShopNameExtractor.extract(json))
    }

    // shopName 字段
    @Test
    fun `extract shopName field`() {
        val json = """{"data":{"shopName":"西贝莜面村","rating":4.5}}"""
        assertEquals("西贝莜面村", ShopNameExtractor.extract(json))
    }

    // name 字段（兜底）
    @Test
    fun `extract name field as fallback`() {
        val json = """{"poi":{"name":"麦当劳(国贸店)"}}"""
        assertEquals("麦当劳(国贸店)", ShopNameExtractor.extract(json))
    }

    // 多字段时取第一个有效值
    @Test
    fun `extract first valid field when multiple present`() {
        val json = """{"a":{"poiName":"甲店"},"b":{"shopName":"乙店"}}"""
        assertEquals("甲店", ShopNameExtractor.extract(json))
    }

    // 字段值含空格（前后空白被 trim）
    @Test
    fun `extract trims surrounding whitespace`() {
        val json = """{"poiName":"  外婆家  "}"""
        assertEquals("外婆家", ShopNameExtractor.extract(json))
    }

    // 无店名字段 → null
    @Test
    fun `extract returns null when no shop field`() {
        val json = """{"code":0,"data":{"avgPrice":120,"rating":4.5}}"""
        assertNull(ShopNameExtractor.extract(json))
    }

    // 店名过短（<2 字符）→ null（字段值长度限制 2..40）
    @Test
    fun `extract returns null for too short name`() {
        val json = """{"poiName":"面"}"""
        assertNull(ShopNameExtractor.extract(json))
    }

    // 店名超过 40 字符 → null（限长防爆）
    @Test
    fun `extract returns null for too long name`() {
        // 41 字符（不含字段名）
        val longName = "a".repeat(41)
        val json = """{"poiName":"$longName"}"""
        assertNull(ShopNameExtractor.extract(json))
    }

    // 店名 40 字符边界（≤40 应正常返回）
    @Test
    fun `extract accepts name at 40 char boundary`() {
        val name40 = "店".repeat(40)
        val json = """{"poiName":"$name40"}"""
        assertEquals(name40, ShopNameExtractor.extract(json))
    }

    // 非法 JSON / 截断文本 → null（静默失败不崩溃）
    @Test
    fun `extract returns null for malformed json`() {
        assertNull(ShopNameExtractor.extract("not a json at all"))
        assertNull(ShopNameExtractor.extract(""))
        assertNull(ShopNameExtractor.extract("   "))
        assertNull(ShopNameExtractor.extract("""{"poiName":"""))
    }

    // 嵌套深层结构仍可命中（正则全文扫描）
    @Test
    fun `extract finds field in deeply nested structure`() {
        val json = """{"code":0,"data":{"modules":[{"type":"header","data":{"poiName":"太二酸菜鱼"}}]}}"""
        assertEquals("太二酸菜鱼", ShopNameExtractor.extract(json))
    }
}
