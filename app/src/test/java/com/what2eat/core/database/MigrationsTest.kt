package com.what2eat.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MIGRATION_5_6 迁移判定逻辑单元测试（SQL 部分由 Room schema 验证 + 真机迁移覆盖）。
 */
class MigrationsTest {

    @Test
    fun `legacy leaked names are detected by prefix`() {
        assertTrue(isLegacyLeakedName("地址：番禺区兴南大道与万博二路交汇处"))
        assertTrue(isLegacyLeakedName("电话：020-39170000"))
        assertTrue(isLegacyLeakedName("门店地址：番禺区"))
        assertTrue(isLegacyLeakedName("商家电话：020-3917"))
        assertTrue(isLegacyLeakedName("商户：某公司"))
    }

    @Test
    fun `normal names are not treated as leaked`() {
        assertFalse(isLegacyLeakedName("台屿·台湾食堂"))
        assertFalse(isLegacyLeakedName("海底捞火锅（西单大悦城店）"))
        assertFalse(isLegacyLeakedName("来自美团的分享"))
        assertFalse(isLegacyLeakedName("待整理的分享"))
        assertFalse(isLegacyLeakedName(""))
    }

    @Test
    fun `migration target versions are contiguous`() {
        assertEquals(5, MIGRATION_5_6.startVersion)
        assertEquals(6, MIGRATION_5_6.endVersion)
    }
}
