package com.what2eat.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库 Migration 集合。
 *
 * MIGRATION_1_2: v1 → v2
 * 1. 新增 food_category 表
 * 2. 新增 person_category_preference 表
 * 3. 重建 person_profile 表（id 从 Long 改为 String，新增 sortOrder/enabled，移除 avatar）
 * 4. 迁移现有用户数据，保留主用户名称
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── 1. 创建 food_category 表 ──
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `food_category` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `parentId` TEXT,
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `enabled` INTEGER NOT NULL DEFAULT 1,
                `isSystemPreset` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_category_parentId` ON `food_category` (`parentId`)")

        // ── 2. 创建 person_category_preference 表 ──
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `person_category_preference` (
                `personId` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `preferenceLevel` INTEGER NOT NULL DEFAULT 0,
                `hardExcluded` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`personId`, `categoryId`)
            )
            """.trimIndent()
        )

        // ── 3. 重建 person_profile 表（id 改为 String）──
        // 创建新表
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `person_profile_new` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `isPrimary` INTEGER NOT NULL DEFAULT 0,
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `enabled` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        // 迁移现有数据：将 Long id 转为 String id
        // isPrimary = 1 的记录使用 "person_primary"，其他使用 "person_secondary"
        db.execSQL(
            """
            INSERT INTO `person_profile_new` (`id`, `name`, `isPrimary`, `sortOrder`, `enabled`, `createdAt`, `updatedAt`)
            SELECT
                CASE WHEN isPrimary = 1 THEN 'person_primary' ELSE 'person_secondary' END,
                `name`,
                `isPrimary`,
                CASE WHEN isPrimary = 1 THEN 0 ELSE 1 END,
                1,
                `createdAt`,
                `updatedAt`
            FROM `person_profile`
            """.trimIndent()
        )

        // 删除旧表，重命名新表
        db.execSQL("DROP TABLE IF EXISTS `person_profile`")
        db.execSQL("ALTER TABLE `person_profile_new` RENAME TO `person_profile`")
    }
}
