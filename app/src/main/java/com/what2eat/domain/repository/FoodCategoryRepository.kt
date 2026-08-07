package com.what2eat.domain.repository

import com.what2eat.domain.model.FoodCategory
import kotlinx.coroutines.flow.Flow

/**
 * 餐饮分类 Repository 接口。
 */
interface FoodCategoryRepository {

    /** 观察所有分类 */
    fun observeAll(): Flow<List<FoodCategory>>

    /** 观察已启用的分类 */
    fun observeEnabled(): Flow<List<FoodCategory>>

    /** 根据 id 获取分类 */
    suspend fun getById(id: String): FoodCategory?

    /** 观察某一级分类下的子分类 */
    fun observeByParent(parentId: String): Flow<List<FoodCategory>>

    /** 插入或更新分类 */
    suspend fun upsert(category: FoodCategory)

    /** 设置分类启用状态 */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /** 检查是否已初始化默认分类 */
    suspend fun isInitialized(): Boolean

    /** 批量插入默认分类 */
    suspend fun insertAll(categories: List<FoodCategory>)
}
