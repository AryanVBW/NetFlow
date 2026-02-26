package com.netflow.predict

import android.app.AlertDialog
import android.content.Context
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
        
        // Check for first run consent
        checkConsent()

        setContent {
            NetFlowAppContent()
        }
    }

    private fun checkConsent() {
        val prefs = getSharedPreferences("netflow_prefs", Context.MODE_PRIVATE)
        val hasConsented = prefs.getBoolean("has_consented_v1", false)

        if (!hasConsented) {
            AlertDialog.Builder(this)
                .setTitle("Privacy & Data Usage")
                .setMessage("NetFlow Predict uses a local VPN to monitor your network traffic. All data is processed locally on your device and is never uploaded to our servers.\n\nBy continuing, you agree to our Privacy Policy and Terms of Service.")
                .setPositiveButton("I Agree") { _, _ ->
                    prefs.edit().putBoolean("has_consented_v1", true).apply()
                }
                .setNegativeButton("Exit") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }
}

@Composable
private fun NetFlowAppContent() {
    NetFlowTheme {
        val navController = rememberNavController()
        AppNavigation(navController = navController)
    }
}
