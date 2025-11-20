package com.xalies.tiktapremote

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onAddProfileClick = { navController.navigate("appList") },
                onUpgradeClick = { navController.navigate("purchase") }, // Added callback
                onProfileClick = { profile ->
                    val singleAction = profile.actions[TriggerType.SINGLE_PRESS]?.type?.name ?: ""
                    val doubleAction = profile.actions[TriggerType.DOUBLE_PRESS]?.type?.name ?: ""
                    navController.navigate("profile/${profile.packageName}/${profile.appName}?x=${profile.tapX}&y=${profile.tapY}&keyCode=${profile.keyCode}&blockInput=${profile.blockInput}&singleAction=$singleAction&doubleAction=$doubleAction")
                }
            )
        }
        // New Route
        composable("purchase") {
            PurchaseScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("appList") {
            AppListScreen(
                onAppClick = { appInfo ->
                    navController.navigate("profile/${appInfo.packageName}/${appInfo.name}")
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            "profile/{packageName}/{appName}?x={x}&y={y}&keyCode={keyCode}&blockInput={blockInput}&singleAction={singleAction}&doubleAction={doubleAction}&selectedTrigger={selectedTrigger}",
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType },
                navArgument("appName") { type = NavType.StringType },
                navArgument("x") { type = NavType.StringType; nullable = true },
                navArgument("y") { type = NavType.StringType; nullable = true },
                navArgument("keyCode") { type = NavType.StringType; nullable = true },
                navArgument("blockInput") { type = NavType.StringType; nullable = true },
                navArgument("singleAction") { type = NavType.StringType; nullable = true },
                navArgument("doubleAction") { type = NavType.StringType; nullable = true },
                navArgument("selectedTrigger") { type = NavType.StringType; nullable = true }
            ),
            deepLinks = listOf(navDeepLink {
                uriPattern = "tiktapremote://profile/{packageName}/{appName}?x={x}&y={y}&keyCode={keyCode}&blockInput={blockInput}&singleAction={singleAction}&doubleAction={doubleAction}&selectedTrigger={selectedTrigger}"
            })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            val appName = backStackEntry.arguments?.getString("appName") ?: ""
            val x = backStackEntry.arguments?.getString("x")
            val y = backStackEntry.arguments?.getString("y")
            val keyCode = backStackEntry.arguments?.getString("keyCode")
            val blockInput = backStackEntry.arguments?.getString("blockInput")?.toBoolean()

            val singleActionString = backStackEntry.arguments?.getString("singleAction")
            val doubleActionString = backStackEntry.arguments?.getString("doubleAction")
            val selectedTriggerString = backStackEntry.arguments?.getString("selectedTrigger")

            val initialActions = mutableMapOf<TriggerType, Action>()
            if (!singleActionString.isNullOrEmpty()) {
                initialActions[TriggerType.SINGLE_PRESS] = Action(ActionType.valueOf(singleActionString))
            }
            if (!doubleActionString.isNullOrEmpty()) {
                initialActions[TriggerType.DOUBLE_PRESS] = Action(ActionType.valueOf(doubleActionString))
            }

            val initialSelectedTrigger = if (!selectedTriggerString.isNullOrEmpty()) {
                try { TriggerType.valueOf(selectedTriggerString) } catch (e: Exception) { TriggerType.SINGLE_PRESS }
            } else {
                TriggerType.SINGLE_PRESS
            }

            val appInfo = AppInfo(name = appName, packageName = packageName, icon = null)

            ProfileScreen(
                appInfo = appInfo,
                initialX = x,
                initialY = y,
                initialKeyCode = keyCode,
                initialBlockInput = blockInput,
                initialShowVisualIndicator = false,
                initialActions = initialActions,
                onBackClick = { navController.popBackStack() },
                initialSelectedTrigger = initialSelectedTrigger
            )
        }
    }
}

data class ProfileNavInfo(
    val packageName: String,
    val appName: String,
    val keyCode: Int,
    val tapX: Int,
    val tapY: Int,
    val blockInput: Boolean,
    val showVisualIndicator: Boolean,
    val actions: Map<TriggerType, Action>,
    val isEnabled: Boolean
)