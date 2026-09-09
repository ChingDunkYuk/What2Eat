package com.what2eat.domain.repository

import com.what2eat.domain.model.PersonOptionPreference
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SavedOptionCollection
import com.what2eat.domain.model.TagUsage
import com.what2eat.domain.model.CollectionType
import kotlinx.coroutines.flow.Flow

/**
 * 吃饭池（SavedOption）Repository 接口。
 * Stage 4：手动管理吃饭选项、多列表、标签、人物具体偏好。
 */
interface SavedOptionRepository {

    /** 观察所有选项（含停用） */
    fun observeAll(): Flow<List<SavedOption>>

    /** 观察单个选项 */
    fun observeById(id: String): Flow<SavedOption?>

    /** 按关键词搜索（名称/区域/标签） */
    fun search(query: String): Flow<List<SavedOption>>

    /** 获取单个选项 */
    suspend fun getById(id: String): SavedOption?

    /** 个别选项是否已被历史引用（决定可否物理删除） */
    suspend fun isReferencedByHistory(optionId: String): Boolean

    /** 新增或更新选项（编辑保持 id 不变） */
    suspend fun upsert(option: SavedOption)

    /** 更新选项的所属列表（整组替换） */
    suspend fun setCollections(optionId: String, collections: Set<CollectionType>)

    /** 观察选项所属列表 */
    fun observeCollections(optionId: String): Flow<List<SavedOptionCollection>>

    /** 观察所有选项-列表关系（用于池页高效过滤） */
    fun observeAllCollections(): Flow<List<SavedOptionCollection>>

    /** 观察某列表下的所有选项 id */
    fun observeOptionIdsByCollection(collectionType: CollectionType): Flow<List<String>>

    /** 停用/启用选项 */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /** 记录最近选择时间 */
    suspend fun setLastChosenAt(id: String, chosenAt: Long)

    /** 物理删除（仅未被历史引用的选项） */
    suspend fun delete(id: String)

    // ── 标签 ──

    /** 观察所有选项标签（用于池页搜索/展示） */
    fun observeAllTags(): Flow<Map<String, List<String>>>

    /** 观察选项标签（tagId 即标签名） */
    fun observeTags(optionId: String): Flow<List<String>>

    /** 设置选项标签（整组替换） */
    suspend fun setTags(optionId: String, tags: Set<String>)

    // ── 标签管理（v0.9.2） ──

    /** 观察标签使用统计（按使用数降序、名称升序） */
    fun observeTagUsage(): Flow<List<TagUsage>>

    /**
     * 重命名标签；目标名已存在时自动合并（同选项重复关联去重）。
     * to 首尾去空后为空或与原名相同则不做任何事。
     */
    suspend fun renameTag(from: String, to: String)

    /** 删除标签（移除其全部关联） */
    suspend fun deleteTag(tagId: String)

    // ── 人物具体偏好 ──

    /** 观察选项的被各人物偏好 */
    fun observePreferences(optionId: String): Flow<List<PersonOptionPreference>>

    /** 获取选项被各人物偏好 */
    suspend fun getPreferences(optionId: String): List<PersonOptionPreference>

    /** 全量选项偏好（v0.9.1：池决策 N+1 优化——一次查询内存 groupBy 替代逐选项查询） */
    suspend fun getAllPreferences(): List<PersonOptionPreference>

    /** 设置某人对某选项的偏好 */
    suspend fun setPreference(preference: PersonOptionPreference)
}