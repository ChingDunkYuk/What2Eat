package com.what2eat.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.what2eat.feature.foodpool.FoodPoolScreen
import com.what2eat.feature.history.HistoryScreen
import com.what2eat.feature.home.HomeScreen
import com.what2eat.feature.placeholder.PlaceholderScreen
import com.what2eat.feature.preference.PreferenceScreen
import com.what2eat.feature.settings.SettingsScreen

/**
 * What2Eat 主导航入口。
 *
 * 单 Activity + Navigation Compose，底部四 Tab + 占位页 + 偏好页路由。
 */
@Composable
fun What2EatNavHost() {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // 判断当前是否在底部 Tab 页面上（控制底栏可见性）
    val showBottomBar = bottomNavItems.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelResId)
                                )
                            },
                            label = {
                                Text(text = stringResource(destination.labelResId))
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavDestination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 底部导航页面 ──

            composable(BottomNavDestination.Home.route) {
                HomeScreen(
                    onDecideFirstClick = {
                        navController.navigate(What2EatRoutes.PLACEHOLDER_DECIDE_FIRST)
                    },
                    onPoolDecideClick = {
                        navController.navigate(What2EatRoutes.PLACEHOLDER_POOL)
                    }
                )
            }

            composable(BottomNavDestination.FoodPool.route) {
                FoodPoolScreen()
            }

            composable(BottomNavDestination.History.route) {
                HistoryScreen()
            }

            composable(BottomNavDestination.Settings.route) {
                SettingsScreen(
                    onNavigateToPreference = { personId ->
                        navController.navigate(What2EatRoutes.preferenceRoute(personId))
                    }
                )
            }

            // ── 占位页面 ──

            composable(What2EatRoutes.PLACEHOLDER_DECIDE_FIRST) {
                PlaceholderScreen(
                    title = "先决定吃什么",
                    description = "此功能将在后续版本中开发"
                )
            }

            composable(What2EatRoutes.PLACEHOLDER_POOL) {
                PlaceholderScreen(
                    title = "从我的吃饭池决定",
                    description = "此功能将在后续版本中开发"
                )
            }

            // ── 偏好设置页面 ──

            composable(
                route = What2EatRoutes.PREFERENCE,
                arguments = listOf(
                    navArgument("personId") { type = NavType.StringType }
                )
            ) {
                PreferenceScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
