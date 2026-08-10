package com.what2eat.domain.foodpool

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SavedOptionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 4：新增/编辑表单校验与默认建议单元测试。
 */
class FoodOptionFormTest {

    // ── 名称校验 ──

    @Test
    fun `name must be non empty after trim`() {
        assertFalse(FoodOptionForm.isValidName(""))
        assertFalse(FoodOptionForm.isValidName("   "))
    }

    @Test
    fun `name is trimmed before validation`() {
        assertTrue(FoodOptionForm.isValidName("  潮汕牛肉火锅  "))
    }

    @Test
    fun `name length up to 50 accepted`() {
        assertTrue(FoodOptionForm.isValidName("潮".repeat(50)))
    }

    @Test
    fun `name longer than 50 rejected`() {
        assertFalse(FoodOptionForm.isValidName("潮".repeat(51)))
    }

    // ── 默认列表建议 ──

    @Test
    fun `restaurant suggests want to try`() {
        assertEquals(setOf(CollectionType.WANT_TO_TRY),
            FoodOptionForm.suggestCollections(SavedOptionType.RESTAURANT))
    }

    @Test
    fun `takeout store suggests takeout`() {
        assertEquals(setOf(CollectionType.TAKEOUT),
            FoodOptionForm.suggestCollections(SavedOptionType.TAKEOUT_STORE))
    }

    @Test
    fun `home meal suggests home cook`() {
        assertEquals(setOf(CollectionType.HOME_COOK),
            FoodOptionForm.suggestCollections(SavedOptionType.HOME_MEAL))
    }

    // ── buildOption ──

    @Test
    fun `build creates new id when no existing`() {
        val opt = FoodOptionForm.buildOption(
            existing = null, name = " 潮汕牛肉火锅 ",
            type = SavedOptionType.RESTAURANT, areaText = null, priceLevel = null,
            estimatedMinutes = null, notes = null, sourceUrl = null, enabled = true
        )
        assertTrue(opt.id.startsWith("saved_"))
        assertEquals("潮汕牛肉火锅", opt.name) // trim
    }

    @Test
    fun `build keeps existing id on edit`() {
        val existing = SavedOption(
            id = "keep-me", name = "旧名", optionType = SavedOptionType.RESTAURANT
        )
        val updated = FoodOptionForm.buildOption(
            existing = existing, name = "新名",
            type = SavedOptionType.TAKEOUT_STORE, areaText = "佛山", priceLevel = 3,
            estimatedMinutes = 30, notes = "备注", sourceUrl = "https://x", enabled = false
        )
        assertEquals("keep-me", updated.id) // 编辑必须保持 id 不变
        assertEquals("新名", updated.name)
        assertEquals(SavedOptionType.TAKEOUT_STORE, updated.optionType)
        assertEquals("佛山", updated.areaText)
        assertEquals(false, updated.enabled)
    }

    @Test
    fun `blank optional fields become null`() {
        val opt = FoodOptionForm.buildOption(
            existing = null, name = "面",
            type = SavedOptionType.HOME_MEAL, areaText = "   ", priceLevel = null,
            estimatedMinutes = null, notes = "  ", sourceUrl = " ", enabled = true
        )
        assertEquals(null, opt.areaText)
        assertEquals(null, opt.notes)
        assertEquals(null, opt.sourceUrl)
    }

    @Test
    fun `edit never replaces id with a new one`() {
        val existing = SavedOption(id = "stable-id", name = "A", optionType = SavedOptionType.RESTAURANT)
        val updated = FoodOptionForm.buildOption(
            existing = existing, name = "B",
            type = SavedOptionType.RESTAURANT, areaText = null, priceLevel = null,
            estimatedMinutes = null, notes = null, sourceUrl = null, enabled = true
        )
        assertEquals("stable-id", updated.id)
        assertNotEquals("saved_", updated.id.take(6))
    }

    @Test
    fun `create and edit produce distinct ids`() {
        val created = FoodOptionForm.buildOption(
            existing = null, name = "C",
            type = SavedOptionType.RESTAURANT, areaText = null, priceLevel = null,
            estimatedMinutes = null, notes = null, sourceUrl = null, enabled = true
        )
        val edited = FoodOptionForm.buildOption(
            existing = created, name = "D",
            type = SavedOptionType.RESTAURANT, areaText = null, priceLevel = null,
            estimatedMinutes = null, notes = null, sourceUrl = null, enabled = true
        )
        assertEquals(created.id, edited.id)
    }

    // ── 待整理闭环：补齐资料保存后标记 COMPLETE ──

    @Test
    fun `build with markCompleted turns needs-review into complete`() {
        val review = SavedOption(
            id = "review-1", name = "待整理项",
            optionType = SavedOptionType.RESTAURANT,
            importStatus = ImportStatus.NEEDS_REVIEW
        )
        val saved = FoodOptionForm.buildOption(
            existing = review, name = "已补齐",
            type = SavedOptionType.RESTAURANT, areaText = "天河", priceLevel = null,
            estimatedMinutes = null, notes = null, sourceUrl = null, enabled = true,
            markCompleted = true
        )
        assertEquals(ImportStatus.COMPLETE, saved.importStatus)
        assertEquals("已补齐", saved.name)
        assertEquals("review-1", saved.id)
    }

    @Test
    fun `build without markCompleted preserves needs-review`() {
        val review = SavedOption(
            id = "review-2", name = "待整理项",
            optionType = SavedOptionType.RESTAURANT,
            importStatus = ImportStatus.NEEDS_REVIEW
        )
        val saved = FoodOptionForm.buildOption(
            existing = review, name = "还是待整理",
            type = SavedOptionType.RESTAURANT, areaText = null, priceLevel = null,
            estimatedMinutes = null, notes = null, sourceUrl = null, enabled = true
        )
        assertEquals(ImportStatus.NEEDS_REVIEW, saved.importStatus)
    }
}