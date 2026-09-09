package com.what2eat.data.repository

import com.what2eat.core.database.dao.PersonProfileDao
import com.what2eat.core.database.entity.PersonProfileEntity
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.repository.AppUsageModeRepository
import com.what2eat.domain.repository.PRIMARY_PROFILE_ID
import com.what2eat.domain.repository.SECONDARY_PROFILE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主用户识别与自检逻辑单元测试。
 *
 * 验证 PersonProfileRepository 的核心规则：
 * 1. 单人模式主用户必须来自 isPrimary=true，不依赖列表顺序；
 * 2. 修改名称不改变主用户身份；
 * 3. 单双人切换不改变 isPrimary；
 * 4. 应用重启（重新初始化）后主用户仍为原主用户；
 * 5. settlePrimaryProfile 各分支（空库种子化 / 情况A / 情况B单档案 / 情况B多档案选择）；
 * 6. observeEnabledProfilesForMode 单人只返回主用户、双人主用户排第一。
 */
class PrimaryProfileTest {

    private val klaus = PersonProfileEntity(
        id = PRIMARY_PROFILE_ID,
        name = "Klaus",
        isPrimary = true,
        sortOrder = 0,
        enabled = true
    )

    private val qing = PersonProfileEntity(
        id = SECONDARY_PROFILE_ID,
        name = "晴",
        isPrimary = false,
        sortOrder = 1,
        enabled = true
    )

    /** 内存 DAO，模拟 Room 行为 */
    private class FakePersonProfileDao(
        initial: List<PersonProfileEntity>
    ) : PersonProfileDao {
        private val state = MutableStateFlow(initial)

        private fun current(): List<PersonProfileEntity> = state.value

        override fun observeAll(): Flow<List<PersonProfileEntity>> = state

        override fun observePrimary(): Flow<PersonProfileEntity?> =
            MutableStateFlow(current().firstOrNull { it.isPrimary })

        override fun observeEnabled(): Flow<List<PersonProfileEntity>> = state

        override suspend fun getById(id: String): PersonProfileEntity? =
            current().firstOrNull { it.id == id }

        override suspend fun getPrimary(): PersonProfileEntity? =
            current().firstOrNull { it.isPrimary }

        override suspend fun getAllNow(): List<PersonProfileEntity> = current()

        override suspend fun countPrimary(): Int = current().count { it.isPrimary }

        override suspend fun count(): Int = current().size

        override suspend fun clearAllPrimary(updatedAt: Long) {
            state.value = current().map {
                if (it.isPrimary) it.copy(isPrimary = false, updatedAt = updatedAt) else it
            }
        }

        override suspend fun upsert(entity: PersonProfileEntity) {
            val without = current().filterNot { it.id == entity.id }
            state.value = (without + entity)
        }

        override suspend fun update(entity: PersonProfileEntity) = upsert(entity)

        override suspend fun delete(entity: PersonProfileEntity) {
            state.value = current().filterNot { it.id == entity.id }
        }

        override suspend fun migratePreferencePersonId(oldId: String, newId: String) {
            // 不需要断言偏好表，测试中无引用数据
        }

        override suspend fun migrateParticipantPersonId(oldId: String, newId: String) {
            // 不需要断言参与表，测试中无引用数据
        }

        // ── 备份/恢复（v0.9.3）──

        override suspend fun insertAll(entities: List<PersonProfileEntity>) {
            val without = current().filterNot { e -> entities.any { it.id == e.id } }
            state.value = without + entities
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }
    }

    /** 内存使用模式仓库，默认双人 */
    private class FakeAppUsageModeRepository(
        initial: AppUsageMode = AppUsageMode.COUPLE
    ) : AppUsageModeRepository {
        private val state = MutableStateFlow(initial)
        override fun observe(): Flow<AppUsageMode> = state
        override suspend fun get(): AppUsageMode = state.value
        override suspend fun set(mode: AppUsageMode) {
            state.value = mode
        }
    }

    private fun toDomain(e: PersonProfileEntity) = PersonProfile(
        id = e.id, name = e.name, isPrimary = e.isPrimary,
        sortOrder = e.sortOrder, enabled = e.enabled,
        createdAt = e.createdAt, updatedAt = e.updatedAt
    )

    private fun repo(dao: FakePersonProfileDao, mode: AppUsageMode = AppUsageMode.COUPLE) =
        PersonProfileRepositoryImpl(dao, FakeAppUsageModeRepository(mode))

    // 1. 人物顺序为晴、Klaus时，主用户仍返回Klaus
    @Test
    fun `primary is Klaus even when secondary listed first`() {
        // 数据库中晴排在 Klaus 前面（顺序颠倒）
        val dao = FakePersonProfileDao(listOf(qing, klaus))
        val r = repo(dao)

        val primary = runBlocking { r.getPrimaryProfile() }

        assertNotNull(primary)
        assertEquals("Klaus", primary!!.name)
        assertTrue(primary.isPrimary)
    }

    // 1b. 主用户查询不依赖 sortOrder 最小
    @Test
    fun `primary query does not rely on sortOrder`() {
        // Klaus sortOrder=5（比晴大），但 isPrimary=true，仍应返回 Klaus
        val klausHighSort = klaus.copy(sortOrder = 5)
        val dao = FakePersonProfileDao(listOf(klausHighSort, qing))
        val r = repo(dao)

        val primary = runBlocking { r.getPrimaryProfile() }

        assertEquals("Klaus", primary!!.name)
    }

    // 多个主用户时自检应保留 Klaus
    @Test
    fun `normalize keeps Klaus when multiple primaries`() {
        val anotherPrimary = PersonProfileEntity(
            id = "person_extra", name = "第三者", isPrimary = true, sortOrder = 2, enabled = true
        )
        val dao = FakePersonProfileDao(listOf(anotherPrimary, qing, klaus))
        val r = repo(dao)

        val result = runBlocking { r.normalizePrimaryFlag() }

        assertTrue("应执行修复", result.repaired)
        assertEquals("Klaus", result.primary!!.name)
        assertEquals(1, runBlocking { dao.countPrimary() })
    }

    // 2. 修改名称不改变主用户身份
    @Test
    fun `renaming primary does not change isPrimary`() {
        val dao = FakePersonProfileDao(listOf(klaus, qing))
        val r = repo(dao)

        runBlocking {
            val k = r.getPrimaryProfile()!!
            r.upsert(k.copy(name = "克劳斯", updatedAt = System.currentTimeMillis()))
        }

        val primary = runBlocking { r.getPrimaryProfile() }
        assertEquals("克劳斯", primary!!.name)
        assertTrue("改名后仍应为主用户", primary.isPrimary)
    }

    // 3. 单双人切换不改变 isPrimary（启用/停用第二人物不改变主用户）
    @Test
    fun `enabling disabling secondary does not change primary`() {
        val dao = FakePersonProfileDao(listOf(klaus, qing))
        val r = repo(dao)

        // 切到单人：停用第二人物
        runBlocking {
            val secondary = dao.getById(SECONDARY_PROFILE_ID)!!
            r.upsert(toDomain(secondary).copy(enabled = false))
        }
        assertTrue(runBlocking { r.getPrimaryProfile() }!!.isPrimary)

        // 切回双人：重新启用第二人物
        runBlocking {
            val secondary = dao.getById(SECONDARY_PROFILE_ID)!!
            r.upsert(toDomain(secondary).copy(enabled = true))
        }
        val primary = runBlocking { r.getPrimaryProfile() }
        assertTrue(primary!!.isPrimary)
        assertEquals("Klaus", primary.name)
    }

    // 4. 应用重启后主用户仍为Klaus（重新 self-check 不改变主用户）
    @Test
    fun `restart keeps Klaus as primary`() {
        val dao = FakePersonProfileDao(listOf(klaus, qing))
        val r1 = repo(dao)
        runBlocking { r1.normalizePrimaryFlag() }

        // 模拟重启：用同一数据重新创建 repository
        val r2 = repo(dao)
        val primary = runBlocking { r2.getPrimaryProfile() }

        assertEquals("Klaus", primary!!.name)
        assertTrue(primary.isPrimary)
        assertFalse("第二人物不应是主用户", primary.name == "晴")
    }

    // 无主用户但有档案：自检应择优提升，且优先 person_primary
    @Test
    fun `zero primary promotes Klaus by id preference`() {
        val noPrimaryKlaus = klaus.copy(isPrimary = false)
        val dao = FakePersonProfileDao(listOf(qing, noPrimaryKlaus))
        val r = repo(dao)

        assertEquals(0, runBlocking { dao.countPrimary() })
        val result = runBlocking { r.normalizePrimaryFlag() }

        assertTrue(result.repaired)
        assertEquals("Klaus", result.primary!!.name)
    }

    // ── settlePrimaryProfile 各分支 ──

    // 空库：种子化主用户 Klaus
    @Test
    fun `settle seeds Klaus when db empty`() {
        val dao = FakePersonProfileDao(emptyList())
        val r = repo(dao)

        val result = runBlocking { r.settlePrimaryProfile() }

        assertNotNull(result.primary)
        assertFalse(result.needsPrimarySelection)
        assertTrue(result.repaired)
        assertEquals("Klaus", result.primary!!.name)
        assertEquals(1, runBlocking { dao.countPrimary() })
    }

    // 情况 A + C：person_primary 存在时，保证其唯一主用户并启用
    @Test
    fun `settle keeps person_primary as sole primary and enables it`() {
        // 模拟损坏：Klaus isPrimary=false 且被停用，晴反而 isPrimary=true
        val brokenKlaus = klaus.copy(isPrimary = false, enabled = false)
        val promotedQing = qing.copy(isPrimary = true)
        val dao = FakePersonProfileDao(listOf(promotedQing, brokenKlaus))
        val r = repo(dao)

        val result = runBlocking { r.settlePrimaryProfile() }

        assertNotNull(result.primary)
        assertEquals("Klaus", result.primary!!.name)
        assertTrue(result.primary!!.isPrimary)
        assertTrue("主用户应被启用", result.primary!!.enabled)
        assertEquals(1, runBlocking { dao.countPrimary() })
    }

    // 情况 B 单档案：无 person_primary 且仅一个档案，提升并重命名为主用户
    @Test
    fun `settle promotes sole profile to primary`() {
        // 只有晴，且 id 不是 person_primary
        val lonelyQing = qing.copy(id = "person_something")
        val dao = FakePersonProfileDao(listOf(lonelyQing))
        val r = repo(dao)

        val result = runBlocking { r.settlePrimaryProfile() }

        assertFalse(result.needsPrimarySelection)
        assertNotNull(result.primary)
        assertEquals(PRIMARY_PROFILE_ID, result.primary!!.id)
        assertTrue(result.primary!!.isPrimary)
        assertEquals(1, runBlocking { dao.countPrimary() })
    }

    // 情况 B 多档案：无 person_primary 且多个档案，必须交用户选择
    @Test
    fun `settle requires user selection when multiple profiles and no primary`() {
        val a = qing.copy(id = "person_a", name = "A")
        val b = klaus.copy(id = "person_b", name = "B", isPrimary = false)
        val dao = FakePersonProfileDao(listOf(a, b))
        val r = repo(dao)

        val result = runBlocking { r.settlePrimaryProfile() }

        assertTrue(result.needsPrimarySelection)
        assertNull(result.primary)
        assertEquals(2, result.candidatesForSelection.size)
    }

    // promoteProfileToPrimary：将指定档案提升并重命名为 person_primary
    @Test
    fun `promote elevates chosen profile and demotes others`() {
        // 两个档案均非主用户，且 Klaus 记录 id 丢失了 person_primary 锚点
        val klausLostPrimary = klaus.copy(id = "person_klaus_old", isPrimary = false)
        val dao = FakePersonProfileDao(listOf(qing, klausLostPrimary))
        val r = repo(dao)

        val promoted = runBlocking { r.promoteProfileToPrimary("person_klaus_old") }

        assertNotNull(promoted)
        assertEquals(PRIMARY_PROFILE_ID, promoted!!.id)
        assertTrue(promoted.isPrimary)
        assertTrue(promoted.enabled)
        assertEquals(1, runBlocking { dao.countPrimary() })
        // 旧 id 残留记录应被删除
        assertNull(runBlocking { dao.getById("person_klaus_old") })
        // 晴仍存在但非主用户
        val qingNow = runBlocking { dao.getById(SECONDARY_PROFILE_ID) }
        assertNotNull(qingNow)
        assertFalse(qingNow!!.isPrimary)
    }

    // ── observeEnabledProfilesForMode ──

    // 单人模式：只返回主用户
    @Test
    fun `single mode returns only primary`() {
        val dao = FakePersonProfileDao(listOf(klaus, qing))
        val r = repo(dao, AppUsageMode.SINGLE)

        val profiles = runBlocking { r.observeEnabledProfilesForMode().first() }

        assertEquals(1, profiles.size)
        assertEquals("Klaus", profiles[0].name)
    }

    // 双人模式：返回主用户 + 第二人物，主用户排第一（即使顺序颠倒）
    @Test
    fun `couple mode returns secondary listed second regardless of order`() {
        val dao = FakePersonProfileDao(listOf(qing, klaus))
        val r = repo(dao, AppUsageMode.COUPLE)

        val profiles = runBlocking { r.observeEnabledProfilesForMode().first() }

        assertEquals(2, profiles.size)
        assertEquals("Klaus", profiles[0].name)
        assertEquals("晴", profiles[1].name)
    }
}