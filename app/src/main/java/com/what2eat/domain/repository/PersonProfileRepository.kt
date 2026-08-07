package com.what2eat.domain.repository

import com.what2eat.domain.model.PersonProfile
import kotlinx.coroutines.flow.Flow

/**
 * 主用户自检结果。
 */
data class PrimaryNormalizationResult(
    /** 自检修复后最终的主用户 */
    val primary: PersonProfile?,
    /** 修复前检测到的主用户数量 */
    val primaryCountBefore: Int,
    /** 是否执行了修复 */
    val repaired: Boolean
)

/**
 * 主用户身份结算结果。
 *
 * 用于启动自检，保证数据库恰好存在一个主用户（稳定 id = "person_primary"）。
 */
data class PrimarySettlementResult(
    /** 结算后最终的主用户（可能为 null） */
    val primary: PersonProfile?,
    /** 是否需要在界面让用户指定主用户（无 person_primary 且无法可靠识别时） */
    val needsPrimarySelection: Boolean,
    /** 需要用户选择时提供的候选档案 */
    val candidatesForSelection: List<PersonProfile>,
    /** 是否执行了数据修复 */
    val repaired: Boolean
)

/**
 * 人物档案 Repository 接口（领域层定义，数据层实现）。
 */
interface PersonProfileRepository {

    /** 观察所有人物档案 */
    fun observeAll(): Flow<List<PersonProfile>>

    /** 观察主要用户档案（isPrimary = true） */
    fun observePrimary(): Flow<PersonProfile?>

    /** 观察主要用户档案（isPrimary = true），语义清晰别名 */
    fun observePrimaryProfile(): Flow<PersonProfile?>

    /** 获取主要用户档案（isPrimary = true），一次性 */
    suspend fun getPrimaryProfile(): PersonProfile?

    /** 观察已启用的人物档案 */
    fun observeEnabled(): Flow<List<PersonProfile>>

    /**
     * 观察当前使用模式下应展示的人物档案。
     * 单人模式：仅主用户；双人模式：主用户 + 第二人物，主用户排在第一位。
     */
    fun observeEnabledProfilesForMode(): Flow<List<PersonProfile>>

    /** 获取全部人物档案（一次性） */
    suspend fun getAllNow(): List<PersonProfile>

    /** 根据 id 获取人物档案 */
    suspend fun getById(id: String): PersonProfile?

    /** 插入或更新人物档案 */
    suspend fun upsert(profile: PersonProfile)

    /** 删除人物档案 */
    suspend fun delete(profile: PersonProfile)

    /**
     * 主用户自检与修复。
     * 保证数据库中恰好一个 isPrimary = true：
     * - 0 个主用户：若存在档案择优提升，否则返回 null 由调用方种子化；
     * - 多 个主用户：保留 id = "person_primary" 者，其余降级。
     */
    suspend fun normalizePrimaryFlag(): PrimaryNormalizationResult

    /**
     * 主用户身份结算（启动自检核心）。
     * 以稳定 id = "person_primary" 为身份锚点：
     * - 无任何档案：种子化主用户 Klaus；
     * - person_primary 存在：确保其为唯一主用户并启用，其余降级（情况 A/C）；
     * - 无 person_primary 且仅一个档案：提升并重命名该档案为主用户（情况 B 单档案）；
     * - 无 person_primary 且多个档案：无法可靠识别，needsPrimarySelection=true。
     */
    suspend fun settlePrimaryProfile(): PrimarySettlementResult

    /**
     * 由用户明确指定某档案为主用户（情况 B 修复界面）。
     * 将该档案提升为主用户并重命名为稳定 id = "person_primary"，迁移其偏好引用。
     */
    suspend fun promoteProfileToPrimary(profileId: String): PersonProfile?
}

/**
 * 主用户稳定 id 常量。
 * Klaus 的档案 id 永远是 "person_primary"，不依赖名称、列表顺序或 sortOrder。
 */
const val PRIMARY_PROFILE_ID = "person_primary"
const val SECONDARY_PROFILE_ID = "person_secondary"