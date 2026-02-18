package com.netflow.predict.ui.navigation

sealed class Screen(val route: String) {
    object Splash       : Screen("splash")
    object Onboarding   : Screen("onboarding")
    object Permissions  : Screen("permissions")
    // Main tabs
    object Home         : Screen("home")
    object Live         : Screen("live")
    object Apps         : Screen("apps")
    object Settings     : Screen("settings")
    // Detail screens (with arguments)
    object Predictions  : Screen("predictions")
    object AppDetail    : Screen("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
    object ConnectionDetail : Screen("connection_detail/{flowId}/{domain}/{appPackage}") {
        fun createRoute(flowId: String, domain: String, appPackage: String) =
            "connection_detail/$flowId/$domain/$appPackage"
    }
    object BlockRules   : Screen("block_rules")
}
