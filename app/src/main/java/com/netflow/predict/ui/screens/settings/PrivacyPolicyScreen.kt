package com.netflow.predict.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Privacy Policy for NetFlow Predict",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Last Updated: 2026-02-26",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            
            Spacer(8)
            
            SectionTitle("1. Data Collection and Usage")
            SectionBody("NetFlow Predict operates primarily on your device. All network traffic analysis, app identification, and risk assessment are performed locally. We do not upload your browsing history, DNS queries, or traffic logs to any cloud server.")
            
            SectionTitle("2. VPN Service Usage")
            SectionBody("The App uses the Android VpnService API to create a local Virtual Private Network (VPN) interface. This is required to capture network packets for traffic analysis and identify which apps are consuming data. This local VPN does not route your traffic to a remote server.")
            
            SectionTitle("3. Information We Collect")
            SectionBody("We do not collect personal information (PII). The App stores app usage stats, network logs, and risk assessments locally on your device in a secure database.")
            
            SectionTitle("4. Data Retention")
            SectionBody("By default, logs are kept for 30 days. You can adjust this period in the App settings. You can clear all stored data at any time via the 'Clear Data' option in Settings.")
            
            SectionTitle("5. Security")
            SectionBody("We implement industry-standard security measures to protect your local data. The App's database is sandboxed within the application's private storage.")
            
            SectionTitle("6. Contact Us")
            SectionBody("If you have any questions or suggestions about our Privacy Policy, do not hesitate to contact us at aryanvbw@gmail.com.")
            
            Spacer(24)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SectionBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Spacer(height: Int) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = height.dp))
}
