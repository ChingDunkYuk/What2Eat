package com.what2eat.domain.repository

import com.what2eat.domain.model.PersonProfile
import kotlinx.coroutines.flow.Flow

/**
 * 人物档案 Repository 接口（领域层定义，数据层实现）。
 */
interface PersonProfileRepository {

    /** 观察所有人物档案 */
    fun observeAll(): Flow<List<PersonProfile>>

    /** 观察主要用户档案 */
    fun observePrimary(): Flow<PersonProfile?>

    /** 根据 id 获取人物档案 */
    suspend fun getById(id: Long): PersonProfile?

    /** 插入或更新人物档案，返回 id */
    suspend fun upsert(profile: PersonProfile): Long

    /** 删除人物档案 */
    suspend fun delete(profile: PersonProfile)
}
