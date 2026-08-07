package com.what2eat.data.repository

import android.util.Log
import com.what2eat.core.database.dao.PersonProfileDao
import com.what2eat.core.database.entity.PersonProfileEntity
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.repository.AppUsageModeRepository
import com.what2eat.domain.repository.PRIMARY_PROFILE_ID
import com.what2eat.domain.repository.PersonProfileRepository
import com.what2eat.domain.repository.PrimaryNormalizationResult
import com.what2eat.domain.repository.PrimarySettlementResult
import com.what2eat.domain.repository.SECONDARY_PROFILE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PersonProfileRepository 的数据层实现。
 * Stage 1.1: id 改为 String。
 * Stage 2.1: 主用户自检 + 稳定 id 身份锚点 + 模式感知查询。
 */
@Singleton
class PersonProfileRepositoryImpl @Inject constructor(
    private val dao: PersonProfileDao,
    private val usageModeRepository: AppUsageModeRepository
) : PersonProfileRepository {

    companion object {
        private const val TAG = "PersonProfileRepo"
    }

    override fun observeAll(): Flow<List<PersonProfile>> {
        return dao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    override fun observePrimary(): Flow<PersonProfile?> {
        return dao.observePrimary().map { entity -> entity?.toDomain() }
    }

    override fun observePrimaryProfile(): Flow<PersonProfile?> = observePrimary()

    override suspend fun getPrimaryProfile(): PersonProfile? {
        return dao.getPrimary()?.toDomain()
    }

    override fun observeEnabled(): Flow<List<PersonProfile>> {
        return dao.observeEnabled().map { list -> list.map { it.toDomain() } }
    }

    /**
     * 模式感知的人物档案查询。
     * 单人模式：仅主用户；双人模式：主用户排第一，第二人物随后。
     */
    override fun observeEnabledProfilesForMode(): Flow<List<PersonProfile>> {
        return combine(usageModeRepository.observe(), dao.observeEnabled()) { mode, entities ->
            val profiles = entities.map { it.toDomain() }
            val primaryFirst = profiles.sortedWith(
                compareByDescending<PersonProfile> { it.isPrimary }
                    .thenBy { it.sortOrder }
            )
            when (mode) {
                AppUsageMode.SINGLE -> primaryFirst.filter { it.isPrimary }
                AppUsageMode.COUPLE -> primaryFirst
            }
        }
    }

    override suspend fun getAllNow(): List<PersonProfile> {
        return dao.getAllNow().map { it.toDomain() }
    }

    override suspend fun getById(id: String): PersonProfile? {
        return dao.getById(id)?.toDomain()
    }

    override suspend fun upsert(profile: PersonProfile) {
        dao.upsert(profile.toEntity())
    }

    override suspend fun delete(profile: PersonProfile) {
        dao.delete(profile.toEntity())
    }

    /**
     * 主用户自检与修复（保留兼容）。
     */
    override suspend fun normalizePrimaryFlag(): PrimaryNormalizationResult {
        val all = dao.getAllNow()
        val primaryCount = all.count { it.isPrimary }
        val before = primaryCount

        if (primaryCount == 1) {
            val primary = all.first { it.isPrimary }
            return PrimaryNormalizationResult(primary.toDomain(), before, repaired = false)
        }

        if (primaryCount > 1) {
            val now = System.currentTimeMillis()
            dao.clearAllPrimary(now)
            val chosen = all.firstOrNull { it.id == PRIMARY_PROFILE_ID }
                ?: all.minByOrNull { it.sortOrder }!!
            dao.upsert(chosen.copy(isPrimary = true, sortOrder = 0, enabled = true, updatedAt = now))
            val repairedPrimary = dao.getPrimary()?.toDomain()
            return PrimaryNormalizationResult(repairedPrimary, before, repaired = true)
        }

        if (all.isEmpty()) {
            return PrimaryNormalizationResult(null, before, repaired = false)
        }
        val now = System.currentTimeMillis()
        val chosen = all.firstOrNull { it.id == PRIMARY_PROFILE_ID }
            ?: all.minByOrNull { it.sortOrder }!!
        dao.upsert(chosen.copy(isPrimary = true, sortOrder = 0, enabled = true, updatedAt = now))
        val repairedPrimary = dao.getPrimary()?.toDomain()
        return PrimaryNormalizationResult(repairedPrimary, before, repaired = true)
    }

    /**
     * 主用户身份结算（启动自检核心）。
     * 以稳定 id = "person_primary" 为身份锚点，保证恰好一个主用户。
     */
    override suspend fun settlePrimaryProfile(): PrimarySettlementResult {
        val all = dao.getAllNow()
        logProfiles("settle: before", all)

        // 无任何档案：种子化主用户 Klaus
        if (all.isEmpty()) {
            val seeded = PersonProfileEntity(
                id = PRIMARY_PROFILE_ID,
                name = "Klaus",
                isPrimary = true,
                sortOrder = 0,
                enabled = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            dao.upsert(seeded)
            logProfiles("settle: seeded Klaus", dao.getAllNow())
            return PrimarySettlementResult(seeded.toDomain(), false, emptyList(), repaired = true)
        }

        // 找到稳定主用户 person_primary
        val personPrimary = all.firstOrNull { it.id == PRIMARY_PROFILE_ID }

        if (personPrimary != null) {
            // 情况 A / C：确保 person_primary 是唯一主用户并启用
            val now = System.currentTimeMillis()
            dao.clearAllPrimary(now)
            dao.upsert(
                personPrimary.copy(
                    isPrimary = true,
                    enabled = true,
                    sortOrder = 0,
                    updatedAt = now
                )
            )
            val repairedPrimary = dao.getPrimary()?.toDomain()
            return PrimarySettlementResult(repairedPrimary, false, emptyList(), repaired = true)
        }

        // 无 person_primary：情况 B
        if (all.size == 1) {
            // 仅一个档案：无法确认名称，但只有一个角色，直接提升并重命名为主用户
            val chosen = all[0]
            val promoted = promoteToPrimaryInternal(chosen)
            return PrimarySettlementResult(
                primary = promoted,
                needsPrimarySelection = false,
                candidatesForSelection = emptyList(),
                repaired = true
            )
        }

        // 多个档案且无 person_primary：无法可靠识别，交由用户选择
        logProfiles("settle: needs user selection", all)
        return PrimarySettlementResult(
            primary = null,
            needsPrimarySelection = true,
            candidatesForSelection = all.map { it.toDomain() },
            repaired = false
        )
    }

    /**
     * 由用户明确指定主用户（情况 B 修复界面）。
     */
    override suspend fun promoteProfileToPrimary(profileId: String): PersonProfile? {
        val target = dao.getById(profileId) ?: return null
        return promoteToPrimaryInternal(target)
    }

    /**
     * 将指定档案提升为主用户并重命名为稳定 id，迁移其偏好引用。
     * 其他档案全部降级为非主用户。
     */
    private suspend fun promoteToPrimaryInternal(chosen: PersonProfileEntity): PersonProfile? {
        val now = System.currentTimeMillis()
        val oldId = chosen.id
        val newId = PRIMARY_PROFILE_ID

        // 迁移引用（偏好、会话参与者）
        if (oldId != newId) {
            dao.migratePreferencePersonId(oldId, newId)
            dao.migrateParticipantPersonId(oldId, newId)
        }

        // 降级其他所有档案
        dao.clearAllPrimary(now)

        // 写入主用户（保留原档案名称，只改身份，不重命名）
        dao.upsert(
            chosen.copy(
                id = newId,
                isPrimary = true,
                enabled = true,
                sortOrder = 0,
                updatedAt = now
            )
        )

        // 删除旧 id 的残留记录（若 oldId != newId）
        if (oldId != newId) {
            dao.getById(oldId)?.let { dao.delete(it) }
        }

        val result = dao.getPrimary()?.toDomain()
        logProfiles("settle: promoted to $newId", dao.getAllNow())
        return result
    }

    // ── 诊断日志 ──

    /**
     * 输出 person_profiles 表完整状态（id/name/isPrimary/enabled/sortOrder/createdAt/updatedAt）。
     * 用于验收排查主用户数据问题。
     */
    private fun logProfiles(action: String, entities: List<PersonProfileEntity>) {
        try {
            val desc = entities.joinToString(" | ") { e ->
                "id=${e.id}, name=${e.name}, isPrimary=${e.isPrimary}, enabled=${e.enabled}, " +
                    "sort=${e.sortOrder}, createdAt=${e.createdAt}, updatedAt=${e.updatedAt}"
            }
            Log.d(TAG, "$action -> [$desc]")
        } catch (e: Throwable) {
            // 本地单元测试环境无 android.util.Log，静默降级
        }
    }

    // ── Mapper ──

    private fun PersonProfileEntity.toDomain(): PersonProfile {
        return PersonProfile(
            id = id,
            name = name,
            isPrimary = isPrimary,
            sortOrder = sortOrder,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun PersonProfile.toEntity(): PersonProfileEntity {
        return PersonProfileEntity(
            id = id,
            name = name,
            isPrimary = isPrimary,
            sortOrder = sortOrder,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}