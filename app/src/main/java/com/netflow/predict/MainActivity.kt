package com.netflow.predict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.netflow.predict.ui.navigation.AppNavigation
import com.netflow.predict.ui.theme.NetFlowTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity entry point.
 *
 * Edge-to-edge display is enabled so that the status bar and
 * navigation bar are drawn over the app's dark background.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetFlowApp()
        }
    }
}

@Composable
private fun NetFlowApp() {
    NetFlowTheme {
        val navController = rememberNavController()
        AppNavigation(navController = navController)
    }
}
