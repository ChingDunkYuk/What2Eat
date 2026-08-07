package com.what2eat.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
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
        icon = Icons.Outlined.Home
    )

    object FoodPool : BottomNavDestination(
        route = "food_pool",
        labelResId = com.what2eat.R.string.nav_food_pool,
        icon = Icons.Outlined.Restaurant
    )

    object History : BottomNavDestination(
        route = "history",
        labelResId = com.what2eat.R.string.nav_history,
        icon = Icons.Outlined.History
    )

    object Settings : BottomNavDestination(
        route = "settings",
        labelResId = com.what2eat.R.string.nav_settings,
        icon = Icons.Outlined.Settings
    )
}

/**
 * 非底部导航的额外路由。
 */
object What2EatRoutes {
    const val PLACEHOLDER_DECIDE_FIRST = "placeholder/decide_first"
    const val PLACEHOLDER_POOL = "placeholder/pool"
    const val PREFERENCE = "preference/{personId}"

    fun preferenceRoute(personId: String): String = "preference/$personId"
}

/** 底部导航项列表 */
val bottomNavItems = listOf(
    BottomNavDestination.Home,
    BottomNavDestination.FoodPool,
    BottomNavDestination.History,
    BottomNavDestination.Settings
)
