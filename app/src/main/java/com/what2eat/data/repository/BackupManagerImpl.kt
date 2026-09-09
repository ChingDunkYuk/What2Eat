package com.what2eat.data.repository

import androidx.room.withTransaction
import com.what2eat.core.database.What2EatDatabase
import com.what2eat.core.database.dao.DecisionRecommendationDao
import com.what2eat.core.database.dao.DecisionSessionDao
import com.what2eat.core.database.dao.FoodCategoryDao
import com.what2eat.core.database.dao.PersonCategoryPreferenceDao
import com.what2eat.core.database.dao.PersonOptionPreferenceDao
import com.what2eat.core.database.dao.PersonProfileDao
import com.what2eat.core.database.dao.SavedOptionCollectionDao
import com.what2eat.core.database.dao.SavedOptionDao
import com.what2eat.core.database.dao.SavedOptionTagDao
import com.what2eat.core.database.dao.SessionCategorySelectionDao
import com.what2eat.core.database.dao.SessionParticipantDao
import com.what2eat.core.database.entity.DecisionRecommendationEntity
import com.what2eat.core.database.entity.DecisionSessionEntity
import com.what2eat.core.database.entity.FoodCategoryEntity
import com.what2eat.core.database.entity.PersonCategoryPreferenceEntity
import com.what2eat.core.database.entity.PersonOptionPreferenceEntity
import com.what2eat.core.database.entity.PersonProfileEntity
import com.what2eat.core.database.entity.SavedOptionCollectionEntity
import com.what2eat.core.database.entity.SavedOptionEntity
import com.what2eat.core.database.entity.SavedOptionTagEntity
import com.what2eat.core.database.entity.SessionCategorySelectionEntity
import com.what2eat.core.database.entity.SessionParticipantEntity
import com.what2eat.core.datastore.AppUsageModeDataStore
import com.what2eat.domain.repository.BackupManager
import com.what2eat.domain.repository.BackupPayload
import com.what2eat.domain.repository.ValidatedBackup
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
import com.what2eat.domain.model.AppUsageMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份管理器实现（v0.9.3）。
 *
 * 导出：11 张表全量快照 + usageMode → JSON。
 * 导入：单 Room 事务内「先删子表后删父表，再父表→子表插入」全量替换，
 * 数据库要么整库还原、要么原样不动；成功后恢复 usageMode 与引导完成标志。
 */
@Singleton
class BackupManagerImpl @Inject constructor(
    private val database: What2EatDatabase,
    private val personProfileDao: PersonProfileDao,
    private val foodCategoryDao: FoodCategoryDao,
    private val personCategoryPreferenceDao: PersonCategoryPreferenceDao,
    private val decisionSessionDao: DecisionSessionDao,
    private val sessionParticipantDao: SessionParticipantDao,
    private val sessionCategorySelectionDao: SessionCategorySelectionDao,
    private val decisionRecommendationDao: DecisionRecommendationDao,
    private val savedOptionDao: SavedOptionDao,
    private val savedOptionCollectionDao: SavedOptionCollectionDao,
    private val savedOptionTagDao: SavedOptionTagDao,
    private val personOptionPreferenceDao: PersonOptionPreferenceDao,
    private val usageModeDataStore: AppUsageModeDataStore
) : BackupManager {

    override suspend fun exportAll(): String {
        val payload = BackupPayload(
            formatVersion = BackupSerializer.CURRENT_FORMAT_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = com.what2eat.BuildConfig.VERSION_NAME,
            usageMode = usageModeDataStore.usageModeFlow.first().name,
            personProfiles = personProfileDao.getAllNow().map { it.toBackup() },
            foodCategories = foodCategoryDao.getAll().map { it.toBackup() },
            personCategoryPreferences = personCategoryPreferenceDao.getAll().map { it.toBackup() },
            decisionSessions = decisionSessionDao.getAll().map { it.toBackup() },
            sessionParticipants = sessionParticipantDao.getAll().map { it.toBackup() },
            sessionCategorySelections = sessionCategorySelectionDao.getAll().map { it.toBackup() },
            decisionRecommendations = decisionRecommendationDao.getAll().map { it.toBackup() },
            savedOptions = savedOptionDao.getAll().map { it.toBackup() },
            savedOptionCollections = savedOptionCollectionDao.getAll().map { it.toBackup() },
            savedOptionTags = savedOptionTagDao.getAll().map { it.toBackup() },
            personOptionPreferences = personOptionPreferenceDao.getAll().map { it.toBackup() }
        )
        return BackupSerializer.encode(payload)
    }

    override suspend fun parseAndValidate(json: String): ValidatedBackup =
        BackupSerializer.decode(json)

    override suspend fun importAll(backup: ValidatedBackup) {
        val p = backup.payload
        database.withTransaction {
            // ── 删：子表 → 父表（FK 安全顺序）──
            personOptionPreferenceDao.deleteAll()
            savedOptionTagDao.deleteAll()
            savedOptionCollectionDao.deleteAll()
            decisionRecommendationDao.deleteAll()
            sessionCategorySelectionDao.deleteAll()
            sessionParticipantDao.deleteAll()
            savedOptionDao.deleteAll()
            decisionSessionDao.deleteAll()
            personCategoryPreferenceDao.deleteAll()
            foodCategoryDao.deleteAll()
            personProfileDao.deleteAll()

            // ── 插：父表 → 子表 ──
            personProfileDao.insertAll(p.personProfiles.map { it.toEntity() })
            foodCategoryDao.insertAll(p.foodCategories.map { it.toEntity() })
            personCategoryPreferenceDao.insertAll(p.personCategoryPreferences.map { it.toEntity() })
            decisionSessionDao.insertAll(p.decisionSessions.map { it.toEntity() })
            sessionParticipantDao.upsertAll(p.sessionParticipants.map { it.toEntity() })
            sessionCategorySelectionDao.insertAll(p.sessionCategorySelections.map { it.toEntity() })
            decisionRecommendationDao.insertAll(p.decisionRecommendations.map { it.toEntity() })
            savedOptionDao.insertAll(p.savedOptions.map { it.toEntity() })
            savedOptionCollectionDao.insertAll(p.savedOptionCollections.map { it.toEntity() })
            savedOptionTagDao.insertAll(p.savedOptionTags.map { it.toEntity() })
            personOptionPreferenceDao.insertAll(p.personOptionPreferences.map { it.toEntity() })
        }

        // ── DataStore：使用模式 + 引导标志（事务外，失败不影响库数据）──
        val mode = AppUsageMode.entries.firstOrNull { it.name == p.usageMode } ?: AppUsageMode.SINGLE
        usageModeDataStore.setUsageMode(mode)
        usageModeDataStore.setOnboardingCompleted()
    }

    // ── Entity → Backup 映射 ──

    private fun PersonProfileEntity.toBackup() = PersonProfileBackup(
        id = id, name = name, isPrimary = isPrimary, sortOrder = sortOrder,
        enabled = enabled, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun FoodCategoryEntity.toBackup() = FoodCategoryBackup(
        id = id, name = name, parentId = parentId, sortOrder = sortOrder,
        enabled = enabled, isSystemPreset = isSystemPreset, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun PersonCategoryPreferenceEntity.toBackup() = PersonCategoryPreferenceBackup(
        personId = personId, categoryId = categoryId, preferenceLevel = preferenceLevel,
        hardExcluded = hardExcluded, updatedAt = updatedAt
    )

    private fun DecisionSessionEntity.toBackup() = DecisionSessionBackup(
        id = id, decisionMode = decisionMode, status = status, startedAt = startedAt,
        completedAt = completedAt, mealModes = mealModes, moodTags = moodTags,
        budgetLevel = budgetLevel, distanceLevel = distanceLevel,
        selectedCategoryId = selectedCategoryId, selectedOptionId = selectedOptionId,
        rerollCount = rerollCount, finalWeight = finalWeight,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun SessionParticipantEntity.toBackup() = SessionParticipantBackup(
        sessionId = sessionId, personId = personId,
        selectionOrder = selectionOrder, completed = completed
    )

    private fun SessionCategorySelectionEntity.toBackup() = SessionCategorySelectionBackup(
        sessionId = sessionId, personId = personId, categoryId = categoryId,
        selectionType = selectionType, updatedAt = updatedAt
    )

    private fun DecisionRecommendationEntity.toBackup() = DecisionRecommendationBackup(
        id = id, sessionId = sessionId, categoryId = categoryId, rank = rank,
        weight = weight, selected = selected, rejected = rejected,
        reasonKeys = reasonKeys, createdAt = createdAt
    )

    private fun SavedOptionEntity.toBackup() = SavedOptionBackup(
        id = id, name = name, optionType = optionType, enabled = enabled,
        sourcePlatform = sourcePlatform, sourceUrl = sourceUrl, sourcePackage = sourcePackage,
        areaText = areaText, priceLevel = priceLevel, estimatedMinutes = estimatedMinutes,
        notes = notes, coverUri = coverUri, importStatus = importStatus,
        createdAt = createdAt, updatedAt = updatedAt, lastChosenAt = lastChosenAt
    )

    private fun SavedOptionCollectionEntity.toBackup() = SavedOptionCollectionBackup(
        savedOptionId = savedOptionId, collectionType = collectionType, createdAt = createdAt
    )

    private fun SavedOptionTagEntity.toBackup() = SavedOptionTagBackup(
        savedOptionId = savedOptionId, tagId = tagId
    )

    private fun PersonOptionPreferenceEntity.toBackup() = PersonOptionPreferenceBackup(
        personId = personId, savedOptionId = savedOptionId,
        preferenceLevel = preferenceLevel, hardExcluded = hardExcluded, updatedAt = updatedAt
    )

    // ── Backup → Entity 映射 ──

    private fun PersonProfileBackup.toEntity() = PersonProfileEntity(
        id = id, name = name, isPrimary = isPrimary, sortOrder = sortOrder,
        enabled = enabled, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun FoodCategoryBackup.toEntity() = FoodCategoryEntity(
        id = id, name = name, parentId = parentId, sortOrder = sortOrder,
        enabled = enabled, isSystemPreset = isSystemPreset, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun PersonCategoryPreferenceBackup.toEntity() = PersonCategoryPreferenceEntity(
        personId = personId, categoryId = categoryId, preferenceLevel = preferenceLevel,
        hardExcluded = hardExcluded, updatedAt = updatedAt
    )

    private fun DecisionSessionBackup.toEntity() = DecisionSessionEntity(
        id = id, decisionMode = decisionMode, status = status, startedAt = startedAt,
        completedAt = completedAt, mealModes = mealModes, moodTags = moodTags,
        budgetLevel = budgetLevel, distanceLevel = distanceLevel,
        selectedCategoryId = selectedCategoryId, selectedOptionId = selectedOptionId,
        rerollCount = rerollCount, finalWeight = finalWeight,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun SessionParticipantBackup.toEntity() = SessionParticipantEntity(
        sessionId = sessionId, personId = personId,
        selectionOrder = selectionOrder, completed = completed
    )

    private fun SessionCategorySelectionBackup.toEntity() = SessionCategorySelectionEntity(
        sessionId = sessionId, personId = personId, categoryId = categoryId,
        selectionType = selectionType, updatedAt = updatedAt
    )

    private fun DecisionRecommendationBackup.toEntity() = DecisionRecommendationEntity(
        id = id, sessionId = sessionId, categoryId = categoryId, rank = rank,
        weight = weight, selected = selected, rejected = rejected,
        reasonKeys = reasonKeys, createdAt = createdAt
    )

    private fun SavedOptionBackup.toEntity() = SavedOptionEntity(
        id = id, name = name, optionType = optionType, enabled = enabled,
        sourcePlatform = sourcePlatform, sourceUrl = sourceUrl, sourcePackage = sourcePackage,
        areaText = areaText, priceLevel = priceLevel, estimatedMinutes = estimatedMinutes,
        notes = notes, coverUri = coverUri, importStatus = importStatus,
        createdAt = createdAt, updatedAt = updatedAt, lastChosenAt = lastChosenAt
    )

    private fun SavedOptionCollectionBackup.toEntity() = SavedOptionCollectionEntity(
        savedOptionId = savedOptionId, collectionType = collectionType, createdAt = createdAt
    )

    private fun SavedOptionTagBackup.toEntity() = SavedOptionTagEntity(
        savedOptionId = savedOptionId, tagId = tagId
    )

    private fun PersonOptionPreferenceBackup.toEntity() = PersonOptionPreferenceEntity(
        personId = personId, savedOptionId = savedOptionId,
        preferenceLevel = preferenceLevel, hardExcluded = hardExcluded, updatedAt = updatedAt
    )
}
