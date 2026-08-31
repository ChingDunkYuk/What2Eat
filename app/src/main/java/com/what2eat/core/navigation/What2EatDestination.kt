package com.what2eat.core.navigation

import com.what2eat.core.designsystem.icon.What2EatIcons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航目标定义。
 */
sealed class BottomNavDestination(
    val route: String,
    val labelResId: Int,
    val icon: ImageVector
) {
    object Home : BottomNavDestination(
        route = "home",
        labelResId = com.what2eat.R.string.nav_home,
        icon = What2EatIcons.Home
    )

    object FoodPool : BottomNavDestination(
        route = "food_pool",
        labelResId = com.what2eat.R.string.nav_food_pool,
        icon = What2EatIcons.Restaurant
    )

    object History : BottomNavDestination(
        route = "history",
        labelResId = com.what2eat.R.string.nav_history,
        icon = What2EatIcons.History
    )

    object Settings : BottomNavDestination(
        route = "settings",
        labelResId = com.what2eat.R.string.nav_settings,
        icon = What2EatIcons.Settings
    )
}

/**
 * 非底部导航的额外路由。
 */
object What2EatRoutes {
    const val PLACEHOLDER_POOL = "placeholder/pool"
    const val PREFERENCE = "preference/{personId}"
    const val DECISION_FLOW = "decision_flow?startNew={startNew}"
    const val FOOD_OPTION_EDIT = "food_pool/edit?optionId={optionId}"
    const val FOOD_OPTION_DETAIL = "food_pool/detail/{optionId}"

    fun preferenceRoute(personId: String): String = "preference/$personId"
    fun decisionFlowRoute(startNew: Boolean): String = "decision_flow?startNew=$startNew"
    fun foodOptionEditRoute(optionId: String?): String =
        "food_pool/edit?optionId=${optionId ?: ""}"
    fun foodOptionDetailRoute(optionId: String): String = "food_pool/detail/$optionId"
}

/** 底部导航项列表 */
val bottomNavItems = listOf(
    BottomNavDestination.Home,
    BottomNavDestination.FoodPool,
    BottomNavDestination.History,
    BottomNavDestination.Settings
)
