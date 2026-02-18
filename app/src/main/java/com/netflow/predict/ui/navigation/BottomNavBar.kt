package com.netflow.predict.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Home",     Icons.Filled.Home,     Screen.Home.route),
    BottomNavItem("Live",     Icons.Filled.Wifi,     Screen.Live.route),
    BottomNavItem("Apps",     Icons.Outlined.Apps,   Screen.Apps.route),
    BottomNavItem("Settings", Icons.Filled.Settings, Screen.Settings.route)
)

/** Bottom navigation bar shown on the four root destinations. */
@Composable
fun NetFlowBottomBar(
    navController: NavController,
    unreadAlerts: Int = 0
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick  = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                },
                icon  = {
                    if (item.route == Screen.Home.route && unreadAlerts > 0) {
                        BadgedBox(badge = { Badge { Text(unreadAlerts.toString()) } }) {
                            Icon(item.icon, contentDescription = item.label)
                        }
                    } else {
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = { Text(item.label) }
            )
        }
    }
}
