package com.what2eat.data.repository

import com.what2eat.core.database.dao.FoodCategoryDao
import com.what2eat.core.database.entity.FoodCategoryEntity
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.repository.FoodCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FoodCategoryRepository 的数据层实现。
 */
@Singleton
class FoodCategoryRepositoryImpl @Inject constructor(
    private val dao: FoodCategoryDao
) : FoodCategoryRepository {

    override fun observeAll(): Flow<List<FoodCategory>> {
        return dao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    override fun observeEnabled(): Flow<List<FoodCategory>> {
        return dao.observeEnabled().map { list -> list.map { it.toDomain() } }
    }

    override fun observeByParent(parentId: String): Flow<List<FoodCategory>> {
        return dao.observeByParent(parentId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getById(id: String): FoodCategory? {
        return dao.getById(id)?.toDomain()
    }

    override suspend fun upsert(category: FoodCategory) {
        dao.upsert(category.toEntity())
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    override suspend fun isInitialized(): Boolean {
        return dao.count() > 0
    }

    override suspend fun insertAll(categories: List<FoodCategory>) {
        dao.insertAll(categories.map { it.toEntity() })
    }

    // ── Mapper ──

    private fun FoodCategoryEntity.toDomain(): FoodCategory {
        return FoodCategory(
            id = id,
            name = name,
            parentId = parentId,
            sortOrder = sortOrder,
            enabled = enabled,
            isSystemPreset = isSystemPreset,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun FoodCategory.toEntity(): FoodCategoryEntity {
        return FoodCategoryEntity(
            id = id,
            name = name,
            parentId = parentId,
            sortOrder = sortOrder,
            enabled = enabled,
            isSystemPreset = isSystemPreset,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
