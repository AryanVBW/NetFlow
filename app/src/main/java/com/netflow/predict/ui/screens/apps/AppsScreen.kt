package com.netflow.predict.ui.screens.apps

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.netflow.predict.data.model.*
import com.netflow.predict.ui.components.*
import com.netflow.predict.ui.navigation.NetFlowBottomBar
import com.netflow.predict.ui.theme.*
import com.netflow.predict.ui.viewmodel.AppSortMode
import com.netflow.predict.ui.viewmodel.AppsViewModel
import com.netflow.predict.ui.screens.home.formatBytes

// ── Apps list screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onNavigateToAppDetail: (packageName: String) -> Unit,
    navController: NavController,
    viewModel: AppsViewModel = hiltViewModel()
) {
    val apps       by viewModel.filteredApps.collectAsState()
    val isLoading  by viewModel.isLoading.collectAsState()
    val sortMode   by viewModel.sortMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var searchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (searchActive) {
                SearchBar(
                    query         = searchQuery,
                    onQueryChange = { viewModel.setSearch(it) },
                    onSearch      = {},
                    active        = true,
                    onActiveChange = { searchActive = it },
                    placeholder   = { Text("Search apps…") },
                    leadingIcon   = {
                        IconButton(onClick = { searchActive = false; viewModel.setSearch("") }) {
                            Icon(Icons.Filled.ArrowBack, "Back")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {}
            } else {
                TopAppBar(
                    title   = { Text("Apps") },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, "Search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = { NetFlowBottomBar(navController = navController) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Sort chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick  = { viewModel.setSort(mode) },
                        label    = {
                            Text(when (mode) {
                                AppSortMode.MOST_DATA       -> "Most Data"
                                AppSortMode.MOST_REQUESTS   -> "Most Requests"
                                AppSortMode.HIGHEST_RISK    -> "Highest Risk"
                                AppSortMode.RECENTLY_ACTIVE -> "Recently Active"
                            })
                        }
                    )
                }
            }

            if (isLoading) {
                LazyColumn {
                    items(8) { ShimmerAppRow() }
                }
            } else if (apps.isEmpty()) {
                EmptyAppsState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppRow(
                            app     = app,
                            onClick = { onNavigateToAppDetail(app.packageName) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppNetworkInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(app.appName.take(2).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName,
                style    = MaterialTheme.typography.titleMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatBytes(app.dataSentToday + app.dataReceivedToday)} today · ${app.requestCountToday} requests",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        RiskBadge(riskLevel = app.riskLevel)
    }
}

@Composable
private fun EmptyAppsState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Apps, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("No app data yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Once NetFlow starts monitoring, apps will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}
