package com.what2eat.domain.search

/**
 * 平台搜索启动器。
 *
 * 统一封装大众点评 / 美团 / 地图 / 浏览器 4 类平台的承接逻辑。
 *
 * 设计约束：
 * - UI 不直接处理具体包名、URI、Intent 异常；
 * - 平台只作快捷入口，不成为核心流程依赖；
 * - 即使平台入口失效，地图、浏览器、复制关键词仍必须正常可用；
 * - 所有失败都必须返回 [PlatformLaunchResult.Failed] 或回退结果，绝不抛异常致闪退；
 * - 严禁写死未经验证的私有 URI Scheme 作为唯一方案。
 */
interface PlatformSearchLauncher {

    /**
     * 启动指定平台搜索。
     *
     * @param platform 目标平台
     * @param query 搜索关键词（应已由 SearchQueryBuilder 生成）
     * @return 平台启动结果（含面向用户的提示文案）
     */
    fun launch(
        platform: SearchPlatform,
        query: String
    ): PlatformLaunchResult
}