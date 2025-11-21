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
                onUpgradeClick = { navController.navigate("purchase") },
                onProfileClick = { profile ->
                    val singleAction = profile.actions[TriggerType.SINGLE_PRESS]
                    val doubleAction = profile.actions[TriggerType.DOUBLE_PRESS]

                    val singleType = singleAction?.type?.name ?: ""
                    val singleX = singleAction?.tapX ?: 0
                    val singleY = singleAction?.tapY ?: 0

                    val doubleType = doubleAction?.type?.name ?: ""
                    val doubleX = doubleAction?.tapX ?: 0
                    val doubleY = doubleAction?.tapY ?: 0

                    navController.navigate("profile/${profile.packageName}/${profile.appName}?keyCode=${profile.keyCode}&blockInput=${profile.blockInput}&singleAction=$singleType&singleX=$singleX&singleY=$singleY&doubleAction=$doubleType&doubleX=$doubleX&doubleY=$doubleY")
                }
            )
        }
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
            "profile/{packageName}/{appName}?keyCode={keyCode}&blockInput={blockInput}&singleAction={singleAction}&singleX={singleX}&singleY={singleY}&doubleAction={doubleAction}&doubleX={doubleX}&doubleY={doubleY}&selectedTrigger={selectedTrigger}",
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType },
                navArgument("appName") { type = NavType.StringType },
                navArgument("keyCode") { type = NavType.StringType; nullable = true },
                navArgument("blockInput") { type = NavType.StringType; nullable = true },
                navArgument("singleAction") { type = NavType.StringType; nullable = true },
                navArgument("singleX") { type = NavType.StringType; nullable = true },
                navArgument("singleY") { type = NavType.StringType; nullable = true },
                navArgument("doubleAction") { type = NavType.StringType; nullable = true },
                navArgument("doubleX") { type = NavType.StringType; nullable = true },
                navArgument("doubleY") { type = NavType.StringType; nullable = true },
                navArgument("selectedTrigger") { type = NavType.StringType; nullable = true }
            ),
            deepLinks = listOf(navDeepLink {
                uriPattern = "tiktapremote://profile/{packageName}/{appName}?keyCode={keyCode}&blockInput={blockInput}&singleAction={singleAction}&singleX={singleX}&singleY={singleY}&doubleAction={doubleAction}&doubleX={doubleX}&doubleY={doubleY}&selectedTrigger={selectedTrigger}"
            })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            val appName = backStackEntry.arguments?.getString("appName") ?: ""
            val keyCode = backStackEntry.arguments?.getString("keyCode")
            val blockInput = backStackEntry.arguments?.getString("blockInput")?.toBoolean()

            val singleActionString = backStackEntry.arguments?.getString("singleAction")
            val singleX = backStackEntry.arguments?.getString("singleX")?.toIntOrNull() ?: 0
            val singleY = backStackEntry.arguments?.getString("singleY")?.toIntOrNull() ?: 0

            val doubleActionString = backStackEntry.arguments?.getString("doubleAction")
            val doubleX = backStackEntry.arguments?.getString("doubleX")?.toIntOrNull() ?: 0
            val doubleY = backStackEntry.arguments?.getString("doubleY")?.toIntOrNull() ?: 0

            val selectedTriggerString = backStackEntry.arguments?.getString("selectedTrigger")

            val initialActions = mutableMapOf<TriggerType, Action>()

            if (!singleActionString.isNullOrEmpty()) {
                try {
                    initialActions[TriggerType.SINGLE_PRESS] = Action(ActionType.valueOf(singleActionString), tapX = singleX, tapY = singleY)
                } catch (e: Exception) {}
            }
            if (!doubleActionString.isNullOrEmpty()) {
                try {
                    initialActions[TriggerType.DOUBLE_PRESS] = Action(ActionType.valueOf(doubleActionString), tapX = doubleX, tapY = doubleY)
                } catch (e: Exception) {}
            }

            val initialSelectedTrigger = if (!selectedTriggerString.isNullOrEmpty()) {
                try { TriggerType.valueOf(selectedTriggerString) } catch (e: Exception) { TriggerType.SINGLE_PRESS }
            } else {
                TriggerType.SINGLE_PRESS
            }

            val appInfo = AppInfo(name = appName, packageName = packageName, icon = null)

            ProfileScreen(
                appInfo = appInfo,
                initialKeyCode = keyCode,
                initialBlockInput = blockInput,
                initialActions = initialActions,
                onBackClick = { navController.popBackStack() },
                initialSelectedTrigger = initialSelectedTrigger
            )
        }
    }
}