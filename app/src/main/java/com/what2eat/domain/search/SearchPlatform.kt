package com.what2eat.domain.search

/**
 * 外部搜索平台。
 *
 * Stage 3.2：在"去找餐厅"面板中新增 DIANPING / MEITUAN 快捷入口。
 * MAP / BROWSER 为既有能力，统一纳入同一平台抽象。
 */
enum class SearchPlatform {

    /** 大众点评（快捷入口，非核心依赖） */
    DIANPING,

    /** 美团（快捷入口，非核心依赖） */
    MEITUAN,

    /** 地图搜索（不代表特定地图应用，让系统选择） */
    MAP,

    /** 浏览器通用搜索 */
    BROWSER
}