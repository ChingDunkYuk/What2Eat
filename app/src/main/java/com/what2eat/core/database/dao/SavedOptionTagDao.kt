package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.SavedOptionTagEntity
import kotlinx.coroutines.flow.Flow

/** 标签使用统计投影（v0.9.2 标签管理） */
data class TagUsageProjection(
    val tagId: String,
    val usageCount: Int
)

@Dao
interface SavedOptionTagDao {

    @Query("SELECT * FROM saved_option_tag")
    fun observeAll(): Flow<List<SavedOptionTagEntity>>

    @Query("SELECT * FROM saved_option_tag WHERE savedOptionId = :optionId")
    fun observeByOption(optionId: String): Flow<List<SavedOptionTagEntity>>

    @Query("SELECT * FROM saved_option_tag WHERE savedOptionId = :optionId")
    suspend fun getByOption(optionId: String): List<SavedOptionTagEntity>

    @Query("SELECT tagId, COUNT(savedOptionId) AS usageCount FROM saved_option_tag GROUP BY tagId ORDER BY usageCount DESC, tagId ASC")
    fun observeTagUsage(): Flow<List<TagUsageProjection>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<SavedOptionTagEntity>)

    @Query("DELETE FROM saved_option_tag WHERE savedOptionId = :optionId")
    suspend fun deleteByOption(optionId: String)

    /** 合并/重命名前清理冲突行：同选项同时挂 from 和 to 时，先移除 to 侧（保 from 待改名） */
    @Query("DELETE FROM saved_option_tag WHERE tagId = :to AND savedOptionId IN (SELECT savedOptionId FROM saved_option_tag WHERE tagId = :from)")
    suspend fun deleteMergeConflicts(from: String, to: String)

    /** 重命名：from 的全部关联改指 to（目标已存在时与 deleteMergeConflicts 组合即为合并） */
    @Query("UPDATE saved_option_tag SET tagId = :to WHERE tagId = :from")
    suspend fun renameTagRefs(from: String, to: String)

    /** 删除标签：移除其全部关联 */
    @Query("DELETE FROM saved_option_tag WHERE tagId = :tagId")
    suspend fun deleteByTag(tagId: String)

    // ── 备份/恢复（v0.9.3） ──

    @Query("SELECT * FROM saved_option_tag")
    suspend fun getAll(): List<SavedOptionTagEntity>

    @Query("DELETE FROM saved_option_tag")
    suspend fun deleteAll()
}