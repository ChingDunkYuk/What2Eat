package com.what2eat.domain.repository

/**
 * v0.9.3 备份/恢复：备份文件 payload DTO。
 *
 * 设计约定：
 * - 字段镜像 schema v7 实体（同名同型），备份格式与 DB schema 解耦，
 *   未来 schema 演进时备份格式迁移是显式动作（formatVersion 递增）。
 * - 除 formatVersion 外全部带默认值：导入时缺失字段容错填充，
 *   旧格式文件缺新字段也能导入（配合 ignoreUnknownKeys 双向兼容） */
data class BackupSummary(
    val profileCount: Int,
    val optionCount: Int,
    val tagCount: Int,
    val sessionCount: Int,
    val recommendationCount: Int
)

/** 已解析校验通过的备份（payload + 预览计数），供导入确认对话框与执行导入复用 */
data class ValidatedBackup(
    val payload: BackupPayload,
    val summary: BackupSummary
)

/** 备份文件格式/版本不合法 */
class BackupFormatException(message: String) : Exception(message)

/**
 * 备份管理器。
 *
 * 导出：全量 11 表快照 + usageMode → JSON。
 * 导入：parseAndValidate 先解析校验（UI 弹确认），确认后 importAll 单事务全量替换。
 */
interface BackupManager {

    /** 导出全量备份 JSON */
    suspend fun exportAll(): String

    /** 解析并校验备份 JSON；格式错误抛 [BackupFormatException] */
    suspend fun parseAndValidate(json: String): ValidatedBackup

    /** 全量替换导入（单事务），并恢复 usageMode 与引导完成标志 */
    suspend fun importAll(backup: ValidatedBackup)
}

// ── 头字段 + 11 表 payload ──

data class BackupPayload(
    /** 备份格式版本（绑定 schema v7），必填——缺失即拒绝 */
    val formatVersion: Int,
    val exportedAt: Long = 0L,
    val appVersion: String = "",
    /** SINGLE / COUPLE */
    val usageMode: String = "SINGLE",
    val personProfiles: List<PersonProfileBackup> = emptyList(),
    val foodCategories: List<FoodCategoryBackup> = emptyList(),
    val personCategoryPreferences: List<PersonCategoryPreferenceBackup> = emptyList(),
    val decisionSessions: List<DecisionSessionBackup> = emptyList(),
    val sessionParticipants: List<SessionParticipantBackup> = emptyList(),
    val sessionCategorySelections: List<SessionCategorySelectionBackup> = emptyList(),
    val decisionRecommendations: List<DecisionRecommendationBackup> = emptyList(),
    val savedOptions: List<SavedOptionBackup> = emptyList(),
    val savedOptionCollections: List<SavedOptionCollectionBackup> = emptyList(),
    val savedOptionTags: List<SavedOptionTagBackup> = emptyList(),
    val personOptionPreferences: List<PersonOptionPreferenceBackup> = emptyList()
)

data class PersonProfileBackup(
    val id: String = "",
    val name: String = "",
    val isPrimary: Boolean = false,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class FoodCategoryBackup(
    val id: String = "",
    val name: String = "",
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val isSystemPreset: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class PersonCategoryPreferenceBackup(
    val personId: String = "",
    val categoryId: String = "",
    val preferenceLevel: Int = 0,
    val hardExcluded: Boolean = false,
    val updatedAt: Long = 0L
)

data class DecisionSessionBackup(
    val id: String = "",
    val decisionMode: Int = 0,
    val status: Int = 0,
    val startedAt: Long = 0L,
    val completedAt: Long? = null,
    val mealModes: String = "",
    val moodTags: String = "",
    val budgetLevel: Int = 5,
    val distanceLevel: Int = 4,
    val selectedCategoryId: String? = null,
    val selectedOptionId: String? = null,
    val rerollCount: Int = 0,
    val finalWeight: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class SessionParticipantBackup(
    val sessionId: String = "",
    val personId: String = "",
    val selectionOrder: Int = 0,
    val completed: Boolean = false
)

data class SessionCategorySelectionBackup(
    val sessionId: String = "",
    val personId: String = "",
    val categoryId: String = "",
    val selectionType: Int = 1,
    val updatedAt: Long = 0L
)

data class DecisionRecommendationBackup(
    val id: Long = 0L,
    val sessionId: String = "",
    val categoryId: String = "",
    val rank: Int = 0,
    val weight: Double = 0.0,
    val selected: Boolean = false,
    val rejected: Boolean = false,
    val reasonKeys: String = "",
    val createdAt: Long = 0L
)

data class SavedOptionBackup(
    val id: String = "",
    val name: String = "",
    val optionType: Int = 0,
    val enabled: Boolean = true,
    val sourcePlatform: Int = 0,
    val sourceUrl: String? = null,
    val sourcePackage: String? = null,
    val areaText: String? = null,
    val priceLevel: Int? = null,
    val estimatedMinutes: Int? = null,
    val notes: String? = null,
    val coverUri: String? = null,
    val importStatus: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastChosenAt: Long? = null
)

data class SavedOptionCollectionBackup(
    val savedOptionId: String = "",
    val collectionType: Int = 0,
    val createdAt: Long = 0L
)

data class SavedOptionTagBackup(
    val savedOptionId: String = "",
    val tagId: String = ""
)

data class PersonOptionPreferenceBackup(
    val personId: String = "",
    val savedOptionId: String = "",
    val preferenceLevel: Int = 0,
    val hardExcluded: Boolean = false,
    val updatedAt: Long = 0L
)
