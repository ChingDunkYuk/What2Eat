package com.what2eat.domain.repository

import com.what2eat.domain.model.PersonCategoryPreference
import kotlinx.coroutines.flow.Flow

/**
 * 人物餐饮偏好 Repository 接口。
 */
interface PersonCategoryPreferenceRepository {

    /** 观察某人物的所有偏好 */
    fun observeByPerson(personId: String): Flow<List<PersonCategoryPreference>>

    /** 观察某人物对某分类的偏好 */
    fun observe(personId: String, categoryId: String): Flow<PersonCategoryPreference?>

    /** 获取某人物的所有偏好（一次性） */
    suspend fun getByPerson(personId: String): List<PersonCategoryPreference>

    /** 设置偏好等级 */
    suspend fun setPreferenceLevel(personId: String, categoryId: String, level: Int)

    /** 设置硬排除 */
    suspend fun setHardExcluded(personId: String, categoryId: String, excluded: Boolean)

    /** 删除偏好 */
    suspend fun delete(personId: String, categoryId: String)
}
