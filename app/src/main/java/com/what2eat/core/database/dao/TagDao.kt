package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * 标签元数据 DAO（v1.6.0 标签颜色）。
 *
 * 懒元数据策略配套：只提供行级读写，「无行 = 默认色」由上层解释。
 */
@Dao
interface TagDao {

    @Query("SELECT * FROM tag")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TagEntity)

    @Query("DELETE FROM tag WHERE name = :name")
    suspend fun deleteByName(name: String)

    /**
     * 重命名元数据行（仅目标无行时调用）：from 行改指 to，颜色跟随。
     * 合并场景（to 已有行）不调用本方法——上层先删 from 行即可（to 色胜出）。
     */
    @Query("UPDATE tag SET name = :to WHERE name = :from")
    suspend fun renameIfTargetAbsent(from: String, to: String)

    // ── 备份/恢复（v1.6.0） ──

    @Query("SELECT * FROM tag")
    suspend fun getAll(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TagEntity>)

    @Query("DELETE FROM tag")
    suspend fun deleteAll()
}
