package com.what2eat.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.what2eat.core.database.dao.FoodCategoryDao
import com.what2eat.core.database.dao.PersonCategoryPreferenceDao
import com.what2eat.core.database.dao.PersonProfileDao
import com.what2eat.core.database.entity.FoodCategoryEntity
import com.what2eat.core.database.entity.PersonCategoryPreferenceEntity
import com.what2eat.core.database.entity.PersonProfileEntity

/**
 * What2Eat Room 数据库。
 *
 * Stage 1.1: 版本升级到 2，新增 FoodCategory 和 PersonCategoryPreference 表，
 * PersonProfile id 改为 String。
 */
@Database(
    entities = [
        PersonProfileEntity::class,
        FoodCategoryEntity::class,
        PersonCategoryPreferenceEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class What2EatDatabase : RoomDatabase() {

    abstract fun personProfileDao(): PersonProfileDao
    abstract fun foodCategoryDao(): FoodCategoryDao
    abstract fun personCategoryPreferenceDao(): PersonCategoryPreferenceDao

    companion object {
        const val DATABASE_NAME = "what2eat.db"
    }
}
