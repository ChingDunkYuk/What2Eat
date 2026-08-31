package com.what2eat.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.what2eat.feature.decision.DecisionFlowScreen
import com.what2eat.feature.foodpool.FoodOptionDetailScreen
import com.what2eat.feature.foodpool.FoodOptionEditScreen
import com.what2eat.feature.foodpool.FoodPoolScreen
import com.what2eat.feature.history.HistoryScreen
import com.what2eat.feature.home.HomeScreen
import com.what2eat.feature.placeholder.PlaceholderScreen
import com.what2eat.feature.preference.PreferenceScreen
import com.what2eat.feature.settings.SettingsScreen

/**
 * What2Eat 主导航入口。
 *
 * 单 Activity + Navigation Compose，底部四 Tab + 偏好页 + 决策流程页。
 */
@Composable
fun What2EatNavHost(initialOptionId: String? = null) {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // 由外部分享"查看详情"跳入时，导航到对应详情页
    LaunchedEffect(initialOptionId) {
        if (!initialOptionId.isNullOrBlank()) {
            navController.navigate(What2EatRoutes.foodOptionDetailRoute(initialOptionId))
        }
    }

    // 判断当前是否在底部 Tab 页面上（控制底栏可见性）
    val showBottomBar = bottomNavItems.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
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
                        navController.navigate(What2EatRoutes.decisionFlowRoute(startNew = true))
                    },
                    onPoolDecideClick = {
                        navController.navigate(What2EatRoutes.PLACEHOLDER_POOL)
                    },
                    onContinueSessionClick = {
                        navController.navigate(What2EatRoutes.decisionFlowRoute(startNew = false))
                    }
                )
            }

            composable(BottomNavDestination.FoodPool.route) {
                FoodPoolScreen(
                    onAddClick = {
                        navController.navigate(What2EatRoutes.foodOptionEditRoute(optionId = null))
                    },
                    onOptionClick = { option ->
                        navController.navigate(What2EatRoutes.foodOptionDetailRoute(option.id))
                    }
                )
            }

            // ── 吃饭池：新增/编辑 ──
            composable(
                route = What2EatRoutes.FOOD_OPTION_EDIT,
                arguments = listOf(
                    navArgument("optionId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val optionId = entry.arguments?.getString("optionId")?.takeIf { it.isNotBlank() }
                FoodOptionEditScreen(
                    optionId = optionId,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── 吃饭池：详情 ──
            composable(
                route = What2EatRoutes.FOOD_OPTION_DETAIL,
                arguments = listOf(
                    navArgument("optionId") { type = NavType.StringType }
                )
            ) { entry ->
                val optionId = entry.arguments?.getString("optionId") ?: return@composable
                FoodOptionDetailScreen(
                    optionId = optionId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id ->
                        navController.navigate(What2EatRoutes.foodOptionEditRoute(id))
                    }
                )
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

            // ── 决策流程页面 ──

            composable(
                route = What2EatRoutes.DECISION_FLOW,
                arguments = listOf(
                    navArgument("startNew") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) {
                DecisionFlowScreen(
                    onExit = { navController.popBackStack() },
                    onCompleted = { navController.popBackStack() }
                )
            }
        }
    }
}
