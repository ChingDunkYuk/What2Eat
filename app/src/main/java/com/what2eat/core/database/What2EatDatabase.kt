package com.what2eat.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.what2eat.core.database.dao.PersonProfileDao
import com.what2eat.core.database.entity.PersonProfileEntity

/**
 * What2Eat Room 数据库。
 *
 * 版本号从 1 开始，后续 Stage 通过 Migration 升级。
 */
@Database(
    entities = [
        PersonProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class What2EatDatabase : RoomDatabase() {

    abstract fun personProfileDao(): PersonProfileDao

    companion object {
        const val DATABASE_NAME = "what2eat.db"
    }
}
