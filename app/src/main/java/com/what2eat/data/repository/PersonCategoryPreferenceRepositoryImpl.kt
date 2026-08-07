package com.what2eat.data.repository

import com.what2eat.core.database.dao.PersonCategoryPreferenceDao
import com.what2eat.core.database.entity.PersonCategoryPreferenceEntity
import com.what2eat.domain.model.PersonCategoryPreference
import com.what2eat.domain.repository.PersonCategoryPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PersonCategoryPreferenceRepository 的数据层实现。
 */
@Singleton
class PersonCategoryPreferenceRepositoryImpl @Inject constructor(
    private val dao: PersonCategoryPreferenceDao
) : PersonCategoryPreferenceRepository {

    override fun observeByPerson(personId: String): Flow<List<PersonCategoryPreference>> {
        return dao.observeByPerson(personId).map { list -> list.map { it.toDomain() } }
    }

    override fun observe(personId: String, categoryId: String): Flow<PersonCategoryPreference?> {
        return dao.observe(personId, categoryId).map { it?.toDomain() }
    }

    override suspend fun getByPerson(personId: String): List<PersonCategoryPreference> {
        return dao.getByPerson(personId).map { it.toDomain() }
    }

    override suspend fun setPreferenceLevel(personId: String, categoryId: String, level: Int) {
        val existing = dao.get(personId, categoryId)
        if (existing != null) {
            dao.setPreferenceLevel(personId, categoryId, level)
        } else {
            dao.upsert(
                PersonCategoryPreferenceEntity(
                    personId = personId,
                    categoryId = categoryId,
                    preferenceLevel = level,
                    hardExcluded = false
                )
            )
        }
    }

    override suspend fun setHardExcluded(personId: String, categoryId: String, excluded: Boolean) {
        val existing = dao.get(personId, categoryId)
        if (existing != null) {
            dao.setHardExcluded(personId, categoryId, excluded)
        } else {
            dao.upsert(
                PersonCategoryPreferenceEntity(
                    personId = personId,
                    categoryId = categoryId,
                    preferenceLevel = 0,
                    hardExcluded = excluded
                )
            )
        }
    }

    override suspend fun delete(personId: String, categoryId: String) {
        dao.delete(personId, categoryId)
    }

    // ── Mapper ──

    private fun PersonCategoryPreferenceEntity.toDomain(): PersonCategoryPreference {
        return PersonCategoryPreference(
            personId = personId,
            categoryId = categoryId,
            preferenceLevel = preferenceLevel,
            hardExcluded = hardExcluded,
            updatedAt = updatedAt
        )
    }
}
