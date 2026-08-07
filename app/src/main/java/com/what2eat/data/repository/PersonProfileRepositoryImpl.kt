package com.what2eat.data.repository

import com.what2eat.core.database.dao.PersonProfileDao
import com.what2eat.core.database.entity.PersonProfileEntity
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.repository.PersonProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PersonProfileRepository 的数据层实现。
 * Stage 1.1: id 改为 String。
 */
@Singleton
class PersonProfileRepositoryImpl @Inject constructor(
    private val dao: PersonProfileDao
) : PersonProfileRepository {

    override fun observeAll(): Flow<List<PersonProfile>> {
        return dao.observeAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observePrimary(): Flow<PersonProfile?> {
        return dao.observePrimary().map { entity ->
            entity?.toDomain()
        }
    }

    override fun observeEnabled(): Flow<List<PersonProfile>> {
        return dao.observeEnabled().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getById(id: String): PersonProfile? {
        return dao.getById(id)?.toDomain()
    }

    override suspend fun upsert(profile: PersonProfile) {
        dao.upsert(profile.toEntity())
    }

    override suspend fun delete(profile: PersonProfile) {
        dao.delete(profile.toEntity())
    }

    // ── Mapper ──

    private fun PersonProfileEntity.toDomain(): PersonProfile {
        return PersonProfile(
            id = id,
            name = name,
            isPrimary = isPrimary,
            sortOrder = sortOrder,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun PersonProfile.toEntity(): PersonProfileEntity {
        return PersonProfileEntity(
            id = id,
            name = name,
            isPrimary = isPrimary,
            sortOrder = sortOrder,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
