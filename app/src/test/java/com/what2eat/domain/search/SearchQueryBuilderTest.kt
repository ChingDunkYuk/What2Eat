package com.what2eat.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SearchQueryBuilder 单元测试。
 * 覆盖 Stage 3.1 搜索关键词生成规则：
 * 1. categoryName 必须有效；
 * 2. 自动 trim；
 * 3. areaText 为空 → 仅 categoryName；
 * 4. areaText 有值 → "area categoryName"。
 */
class SearchQueryBuilderTest {

    @Test
    fun categoryName_only_returnsCategoryName() {
        val r = SearchQueryBuilder.build("潮汕牛肉火锅", null)
        assertTrue(r.valid)
        assertEquals("潮汕牛肉火锅", r.query)
    }

    @Test
    fun categoryName_withEmptyArea_returnsCategoryName() {
        val r = SearchQueryBuilder.build("潮汕牛肉火锅", "")
        assertTrue(r.valid)
        assertEquals("潮汕牛肉火锅", r.query)
    }

    @Test
    fun categoryName_withBlankArea_returnsCategoryName() {
        val r = SearchQueryBuilder.build("潮汕牛肉火锅", "   ")
        assertTrue(r.valid)
        assertEquals("潮汕牛肉火锅", r.query)
    }

    @Test
    fun categoryName_withArea_combinesAreaAndName() {
        val r = SearchQueryBuilder.build("潮汕牛肉火锅", "佛山南海")
        assertTrue(r.valid)
        assertEquals("佛山南海 潮汕牛肉火锅", r.query)
    }

    @Test
    fun categoryName_withTrimmedSurroundingSpaces() {
        val r = SearchQueryBuilder.build("  潮汕牛肉火锅  ", null)
        assertTrue(r.valid)
        assertEquals("潮汕牛肉火锅", r.query)
    }

    @Test
    fun blankCategoryName_isInvalid() {
        val r = SearchQueryBuilder.build("", null)
        assertFalse(r.valid)
        assertEquals("", r.query)
    }

    @Test
    fun whitespaceCategoryName_isInvalid() {
        val r = SearchQueryBuilder.build("   ", null)
        assertFalse(r.valid)
        assertEquals("", r.query)
    }

    @Test
    fun nullCategoryName_isInvalid() {
        val r = SearchQueryBuilder.build(null, "佛山南海")
        assertFalse(r.valid)
        assertEquals("", r.query)
    }

    @Test
    fun areaText_trim_combinedCorrectly() {
        val r = SearchQueryBuilder.build("汉堡", "  广州天河  ")
        assertTrue(r.valid)
        assertEquals("广州天河 汉堡", r.query)
    }
}