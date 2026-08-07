package com.what2eat.data.repository

import androidx.room.withTransaction
import com.what2eat.core.database.What2EatDatabase
import com.what2eat.core.database.dao.DecisionSessionDao
import com.what2eat.core.database.dao.PersonOptionPreferenceDao
import com.what2eat.core.database.dao.SavedOptionCollectionDao
import com.what2eat.core.database.dao.SavedOptionDao
import com.what2eat.core.database.dao.SavedOptionTagDao
import com.what2eat.core.database.entity.PersonOptionPreferenceEntity
import com.what2eat.core.database.entity.SavedOptionCollectionEntity
import com.what2eat.core.database.entity.SavedOptionEntity
import com.what2eat.core.database.entity.SavedOptionTagEntity
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.OptionPreferenceLevel
import com.what2eat.domain.model.PersonOptionPreference
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SavedOptionCollection
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.model.SourcePlatform
import com.what2eat.domain.repository.SavedOptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedOptionRepositoryImpl @Inject constructor(
    private val database: What2EatDatabase,
    private val optionDao: SavedOptionDao,
    private val collectionDao: SavedOptionCollectionDao,
    private val tagDao: SavedOptionTagDao,
    private val preferenceDao: PersonOptionPreferenceDao,
    private val decisionSessionDao: DecisionSessionDao
) : SavedOptionRepository {

    override fun observeAll(): Flow<List<SavedOption>> =
        optionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<SavedOption?> =
        optionDao.observeById(id).map { it?.toDomain() }

    override fun search(query: String): Flow<List<SavedOption>> =
        optionDao.search(query).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): SavedOption? =
        optionDao.getById(id)?.toDomain()

    override suspend fun isReferencedByHistory(optionId: String): Boolean {
        // 吃饭池选项(id) 与 decision_session.selectedCategoryId 的关联：
        // 若某历史决策引用了此选项 id，则不允许物理删除。
        return decisionSessionDao.getCompletedWithSelection()
            .any { it.selectedCategoryId == optionId }
    }

    override suspend fun upsert(option: SavedOption) {
        optionDao.upsert(option.toEntity())
    }

    override suspend fun setCollections(optionId: String, collections: Set<CollectionType>) {
        database.withTransaction {
            collectionDao.deleteByOption(optionId)
            if (collections.isNotEmpty()) {
                collectionDao.insertAll(
                    collections.map { type ->
                        SavedOptionCollectionEntity(
                            savedOptionId = optionId,
                            collectionType = type.ordinal,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                )
            }
        }
    }

    override fun observeCollections(optionId: String): Flow<List<SavedOptionCollection>> =
        collectionDao.observeByOption(optionId).map { list -> list.map { it.toDomain() } }

    override fun observeAllCollections(): Flow<List<SavedOptionCollection>> =
        collectionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeOptionIdsByCollection(collectionType: CollectionType): Flow<List<String>> =
        collectionDao.observeOptionIdsByCollection(collectionType.ordinal)

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        optionDao.setEnabled(id, enabled)
    }

    override suspend fun setLastChosenAt(id: String, chosenAt: Long) {
        optionDao.setLastChosenAt(id, chosenAt)
    }

    override suspend fun delete(id: String) {
        database.withTransaction {
            // CASCADE 会自动清理 collection / tag / preference 关联
            optionDao.delete(id)
        }
    }

    override fun observeAllTags(): Flow<Map<String, List<String>>> =
        tagDao.observeAll().map { list ->
            list.groupBy({ it.savedOptionId }, { it.tagId })
        }

    override fun observeTags(optionId: String): Flow<List<String>> =
        tagDao.observeByOption(optionId).map { list -> list.map { it.tagId } }

    override suspend fun setTags(optionId: String, tags: Set<String>) {
        database.withTransaction {
            tagDao.deleteByOption(optionId)
            if (tags.isNotEmpty()) {
                tagDao.insertAll(
                    tags.map { SavedOptionTagEntity(savedOptionId = optionId, tagId = it.trim()) }
                )
            }
        }
    }

    override fun observePreferences(optionId: String): Flow<List<PersonOptionPreference>> =
        preferenceDao.observeByOption(optionId).map { list -> list.map { it.toDomain() } }

    override suspend fun getPreferences(optionId: String): List<PersonOptionPreference> =
        preferenceDao.getByOption(optionId).map { it.toDomain() }

    override suspend fun setPreference(preference: PersonOptionPreference) {
        preferenceDao.upsert(preference.toEntity())
    }

    // ── Mappers ──

    private fun SavedOptionEntity.toDomain(): SavedOption = SavedOption(
        id = id,
        name = name,
        optionType = SavedOptionType.entries.getOrElse(optionType) { SavedOptionType.RESTAURANT },
        enabled = enabled,
        sourcePlatform = SourcePlatform.entries.getOrElse(sourcePlatform) { SourcePlatform.MANUAL },
        sourceUrl = sourceUrl,
        sourcePackage = sourcePackage,
        areaText = areaText,
        priceLevel = priceLevel,
        estimatedMinutes = estimatedMinutes,
        notes = notes,
        coverUri = coverUri,
        importStatus = ImportStatus.entries.getOrElse(importStatus) { ImportStatus.COMPLETE },
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastChosenAt = lastChosenAt
    )

    private fun SavedOption.toEntity(): SavedOptionEntity = SavedOptionEntity(
        id = id,
        name = name,
        optionType = optionType.ordinal,
        enabled = enabled,
        sourcePlatform = sourcePlatform.ordinal,
        sourceUrl = sourceUrl,
        sourcePackage = sourcePackage,
        areaText = areaText,
        priceLevel = priceLevel,
        estimatedMinutes = estimatedMinutes,
        notes = notes,
        coverUri = coverUri,
        importStatus = importStatus.ordinal,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        lastChosenAt = lastChosenAt
    )

    private fun SavedOptionCollectionEntity.toDomain(): SavedOptionCollection = SavedOptionCollection(
        savedOptionId = savedOptionId,
        collectionType = CollectionType.entries.getOrElse(collectionType) { CollectionType.WANT_TO_TRY },
        createdAt = createdAt
    )

    private fun PersonOptionPreferenceEntity.toDomain(): PersonOptionPreference = PersonOptionPreference(
        personId = personId,
        savedOptionId = savedOptionId,
        preferenceLevel = OptionPreferenceLevel.entries.getOrElse(preferenceLevel) { OptionPreferenceLevel.NEUTRAL },
        hardExcluded = hardExcluded,
        updatedAt = updatedAt
    )

    private fun PersonOptionPreference.toEntity(): PersonOptionPreferenceEntity = PersonOptionPreferenceEntity(
        personId = personId,
        savedOptionId = savedOptionId,
        preferenceLevel = preferenceLevel.ordinal,
        hardExcluded = hardExcluded,
        updatedAt = System.currentTimeMillis()
    )
}