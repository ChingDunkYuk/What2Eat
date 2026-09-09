package com.what2eat.data.repository

import com.what2eat.domain.repository.BackupFormatException
import com.what2eat.domain.repository.BackupPayload
import com.what2eat.domain.repository.DecisionRecommendationBackup
import com.what2eat.domain.repository.DecisionSessionBackup
import com.what2eat.domain.repository.FoodCategoryBackup
import com.what2eat.domain.repository.PersonCategoryPreferenceBackup
import com.what2eat.domain.repository.PersonOptionPreferenceBackup
import com.what2eat.domain.repository.PersonProfileBackup
import com.what2eat.domain.repository.SavedOptionBackup
import com.what2eat.domain.repository.SavedOptionCollectionBackup
import com.what2eat.domain.repository.SavedOptionTagBackup
import com.what2eat.domain.repository.SessionCategorySelectionBackup
import com.what2eat.domain.repository.SessionParticipantBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.3 备份序列化回归：
 * 1. 全表 JSON 往返（含 null 与非 null 可空字段两种形态）
 * 2. 版本头不认识 → 拒绝
 * 3. 畸形 JSON / 缺 formatVersion → 拒绝
 * 4. 缺可选字段 → 默认值容错填充
 * 5. summary 计数（标签去重）
 */
class BackupSerializerTest {

    /** 覆盖全部 11 表的完整 payload（可空字段同时含 null 与非 null 两种形态） */
    private fun fullPayload() = BackupPayload(
        formatVersion = 1,
        exportedAt = 1725417600000L,
        appVersion = "0.9.3",
        usageMode = "COUPLE",
        personProfiles = listOf(
            PersonProfileBackup(
                id = "person_primary", name = "Klaus", isPrimary = true,
                sortOrder = 0, enabled = true, createdAt = 1L, updatedAt = 2L
            ),
            PersonProfileBackup(
                id = "person_secondary", name = "晴", isPrimary = false,
                sortOrder = 1, enabled = false, createdAt = 3L, updatedAt = 4L
            )
        ),
        foodCategories = listOf(
            FoodCategoryBackup(
                id = "cat-hotpot", name = "火锅", parentId = "cat-root",
                sortOrder = 0, enabled = true, isSystemPreset = true, createdAt = 5L, updatedAt = 6L
            ),
            FoodCategoryBackup(
                id = "cat-root", name = "全部", parentId = null,
                sortOrder = 0, enabled = true, isSystemPreset = true, createdAt = 7L, updatedAt = 8L
            )
        ),
        personCategoryPreferences = listOf(
            PersonCategoryPreferenceBackup(
                personId = "person_primary", categoryId = "cat-hotpot",
                preferenceLevel = 2, hardExcluded = false, updatedAt = 9L
            )
        ),
        decisionSessions = listOf(
            DecisionSessionBackup(
                id = "session-1", decisionMode = 0, status = 3, startedAt = 10L,
                completedAt = 11L, mealModes = "0,1", moodTags = "2", budgetLevel = 5,
                distanceLevel = 4, selectedCategoryId = "cat-hotpot", selectedOptionId = null,
                rerollCount = 2, finalWeight = 150.5, createdAt = 12L, updatedAt = 13L
            ),
            DecisionSessionBackup(
                id = "session-2", decisionMode = 1, status = 3, startedAt = 14L,
                completedAt = null, mealModes = "", moodTags = "", budgetLevel = 5,
                distanceLevel = 4, selectedCategoryId = null, selectedOptionId = "opt-1",
                rerollCount = 0, finalWeight = 0.0, createdAt = 15L, updatedAt = 16L
            )
        ),
        sessionParticipants = listOf(
            SessionParticipantBackup(
                sessionId = "session-1", personId = "person_primary",
                selectionOrder = 0, completed = true
            )
        ),
        sessionCategorySelections = listOf(
            SessionCategorySelectionBackup(
                sessionId = "session-1", personId = "person_primary",
                categoryId = "cat-hotpot", selectionType = 1, updatedAt = 17L
            )
        ),
        decisionRecommendations = listOf(
            DecisionRecommendationBackup(
                id = 42L, sessionId = "session-1", categoryId = "cat-hotpot", rank = 1,
                weight = 150.5, selected = true, rejected = false, reasonKeys = "BOTH_WANT,NEVER_EATEN",
                createdAt = 18L
            )
        ),
        savedOptions = listOf(
            SavedOptionBackup(
                id = "opt-1", name = "文通冰室", optionType = 0, enabled = true,
                sourcePlatform = 1, sourceUrl = "https://example.com", sourcePackage = null,
                areaText = "番禺区", priceLevel = 2, estimatedMinutes = null,
                notes = "冻奶茶好喝", coverUri = null, importStatus = 0,
                createdAt = 19L, updatedAt = 20L, lastChosenAt = 21L
            )
        ),
        savedOptionCollections = listOf(
            SavedOptionCollectionBackup(
                savedOptionId = "opt-1", collectionType = 1, createdAt = 22L
            )
        ),
        savedOptionTags = listOf(
            SavedOptionTagBackup(savedOptionId = "opt-1", tagId = "茶餐厅"),
            SavedOptionTagBackup(savedOptionId = "opt-1", tagId = "火锅"), // 同名标签出现在别的店（不存在）
            SavedOptionTagBackup(savedOptionId = "opt-1", tagId = "茶餐厅") // 重复 tagId 模拟多店同名
        ),
        personOptionPreferences = listOf(
            PersonOptionPreferenceBackup(
                personId = "person_primary", savedOptionId = "opt-1",
                preferenceLevel = 2, hardExcluded = false, updatedAt = 23L
            )
        )
    )

    // ── 1. 全表往返 ──

    @Test
    fun `full payload round trip preserves all tables`() {
        val original = fullPayload()
        val json = BackupSerializer.encode(original)
        val decoded = BackupSerializer.decode(json)

        assertEquals(original, decoded.payload)
    }

    // ── 2. 版本头不认识 ──

    @Test
    fun `unknown format version is rejected`() {
        val json = BackupSerializer.encode(fullPayload().copy(formatVersion = 99))
        try {
            BackupSerializer.decode(json)
            throw AssertionError("应当抛出 BackupFormatException")
        } catch (e: BackupFormatException) {
            assertTrue(e.message!!.contains("99"))
        }
    }

    // ── 3. 畸形 JSON / 缺 formatVersion ──

    @Test
    fun `malformed json is rejected`() {
        try {
            BackupSerializer.decode("这不是JSON{{{")
            throw AssertionError("应当抛出 BackupFormatException")
        } catch (e: BackupFormatException) {
            // 预期
        }
    }

    @Test
    fun `missing format version is rejected`() {
        // 合法 JSON 但缺必填的 formatVersion 字段
        val json = """{"exportedAt": 123, "personProfiles": []}"""
        try {
            BackupSerializer.decode(json)
            throw AssertionError("应当抛出 BackupFormatException")
        } catch (e: BackupFormatException) {
            // 预期
        }
    }

    // ── 4. 缺可选字段 → 默认值容错 ──

    @Test
    fun `sparse json fills defaults`() {
        // 只给 formatVersion，其余全部走默认值
        val json = """{"formatVersion": 1}"""
        val decoded = BackupSerializer.decode(json)

        assertEquals(1, decoded.payload.formatVersion)
        assertEquals("SINGLE", decoded.payload.usageMode)
        assertTrue(decoded.payload.personProfiles.isEmpty())
        assertTrue(decoded.payload.savedOptions.isEmpty())
        assertEquals(0, decoded.summary.optionCount)
    }

    // ── 5. summary 计数（标签去重） ──

    @Test
    fun `summary counts distinct tags`() {
        val decoded = BackupSerializer.decode(BackupSerializer.encode(fullPayload()))

        assertEquals(2, decoded.summary.profileCount)
        assertEquals(1, decoded.summary.optionCount)
        // 3 行标签 = 2 个不同 tagId（茶餐厅 ×2 + 火锅 ×1）
        assertEquals(2, decoded.summary.tagCount)
        assertEquals(2, decoded.summary.sessionCount)
        assertEquals(1, decoded.summary.recommendationCount)
    }
}
