package com.netflow.predict.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.netflow.predict.ui.screens.apps.AppDetailScreen
import com.netflow.predict.ui.screens.apps.AppsScreen
import com.netflow.predict.ui.screens.connection.ConnectionDetailScreen
import com.netflow.predict.ui.screens.home.HomeScreen
import com.netflow.predict.ui.screens.live.LiveTrafficScreen
import com.netflow.predict.ui.screens.onboarding.OnboardingScreen
import com.netflow.predict.ui.screens.permissions.PermissionsScreen
import com.netflow.predict.ui.screens.predictions.PredictionsScreen
import com.netflow.predict.ui.screens.settings.PrivacyPolicyScreen
import com.netflow.predict.ui.screens.settings.SettingsScreen
import com.netflow.predict.ui.screens.splash.SplashScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        enterTransition  = { fadeIn(tween(300)) },
        exitTransition   = { fadeOut(tween(200)) },
        popEnterTransition  = { fadeIn(tween(200)) },
        popExitTransition   = { fadeOut(tween(200)) }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToOnboarding  = { navController.navigate(Screen.Onboarding.route) { popUpTo(Screen.Splash.route) { inclusive = true } } },
                onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) { popUpTo(Screen.Splash.route) { inclusive = true } } },
                onNavigateToHome        = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Splash.route) { inclusive = true } } }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onSetupProtection = { navController.navigate(Screen.Permissions.route) },
                onBasicMode       = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } } }
            )
        }

        composable(Screen.Permissions.route) {
            PermissionsScreen(
                onProtectionStarted = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Permissions.route) { inclusive = true } } },
                onBack              = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLive        = { navController.navigate(Screen.Live.route) },
                onNavigateToApps        = { navController.navigate(Screen.Apps.route) },
                onNavigateToPredictions = { navController.navigate(Screen.Predictions.route) },
                onNavigateToSettings    = { navController.navigate(Screen.Settings.route) },
                onNavigateToBlockRules  = { navController.navigate(Screen.BlockRules.route) },
                navController          = navController
            )
        }

        composable(Screen.Live.route) {
            LiveTrafficScreen(
                onNavigateToDetail = { flowId, domain, pkg ->
                    navController.navigate(Screen.ConnectionDetail.createRoute(flowId, domain, pkg))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Apps.route) {
            AppsScreen(
                onNavigateToAppDetail = { pkg ->
                    navController.navigate(Screen.AppDetail.createRoute(pkg))
                },
                navController = navController
            )
        }

        composable(
            route     = Screen.AppDetail.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStack ->
            val pkg = backStack.arguments?.getString("packageName") ?: ""
            AppDetailScreen(
                packageName = pkg,
                onBack = { navController.popBackStack() },
                onNavigateToConnectionDetail = { flowId, domain ->
                    navController.navigate(Screen.ConnectionDetail.createRoute(flowId, domain, pkg))
                }
            )
        }

        composable(Screen.Predictions.route) {
            PredictionsScreen(
                onBack              = { navController.popBackStack() },
                onNavigateToApp     = { pkg -> navController.navigate(Screen.AppDetail.createRoute(pkg)) },
                onNavigateToLive    = { navController.navigate(Screen.Live.route) }
            )
        }

        composable(
            route     = Screen.ConnectionDetail.route,
            arguments = listOf(
                navArgument("flowId")     { type = NavType.StringType },
                navArgument("domain")     { type = NavType.StringType },
                navArgument("appPackage") { type = NavType.StringType }
            )
        ) { backStack ->
            ConnectionDetailScreen(
                flowId     = backStack.arguments?.getString("flowId") ?: "",
                domain     = backStack.arguments?.getString("domain") ?: "",
                appPackage = backStack.arguments?.getString("appPackage") ?: "",
                onBack     = { navController.popBackStack() },
                onNavigateToAppDetail = { pkg -> navController.navigate(Screen.AppDetail.createRoute(pkg)) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
                navController          = navController
            )
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BlockRules.route) {
            // Placeholder — full implementation in SettingsScreen nav graph
            SettingsScreen(
                onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
                navController          = navController
            )
        }
    }
}
