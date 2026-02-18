package com.netflow.predict

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.netflow.predict.ui.screens.splash.SplashScreen
import com.netflow.predict.ui.theme.NetFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic Compose UI tests for the SplashScreen.
 *
 * These are instrumented tests that require an Android device or emulator.
 * Run with: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SplashScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashScreen_displaysAppName() {
        composeTestRule.setContent {
            NetFlowTheme {
                SplashScreen(
                    onNavigateToOnboarding = {},
                    onNavigateToPermissions = {},
                    onNavigateToHome = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("NetFlow Predict")
            .assertIsDisplayed()
    }

    @Test
    fun splashScreen_displaysTagline() {
        composeTestRule.setContent {
            NetFlowTheme {
                SplashScreen(
                    onNavigateToOnboarding = {},
                    onNavigateToPermissions = {},
                    onNavigateToHome = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("See every connection. Predict every risk.")
            .assertIsDisplayed()
    }
}
