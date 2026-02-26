package com.netflow.predict.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.netflow.predict.data.local.dao.*
import com.netflow.predict.data.local.entity.*
import com.netflow.predict.data.model.*
import com.netflow.predict.service.NetFlowVpnService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── DataStore extension ───────────────────────────────────────────────────────

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "netflow_settings")

// ── Settings Repository ───────────────────────────────────────────────────────

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val DNS_ONLY_MODE = booleanPreferencesKey("dns_only_mode")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val AUTO_START_VPN = booleanPreferencesKey("auto_start_vpn")
        val FIRST_RUN = booleanPreferencesKey("first_run")
        val VPN_PERMISSION_GRANTED = booleanPreferencesKey("vpn_permission_granted")
        val SETTINGS_PIN_HASH = stringPreferencesKey("settings_pin_hash")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = try {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "DARK")
            } catch (_: Exception) {
                ThemeMode.DARK
            },
            language = prefs[Keys.LANGUAGE] ?: "system",
            retentionDays = prefs[Keys.RETENTION_DAYS] ?: 30,
            dnsOnlyMode = prefs[Keys.DNS_ONLY_MODE] ?: false,
            aiEnabled = prefs[Keys.AI_ENABLED] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            developerMode = prefs[Keys.DEVELOPER_MODE] ?: false
        )
    }

    val isFirstRun: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.FIRST_RUN] ?: true
    }

    val vpnPermissionGranted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.VPN_PERMISSION_GRANTED] ?: false
    }

    val autoStartVpn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_START_VPN] ?: false
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setRetentionDays(days: Int) {
        context.dataStore.edit { it[Keys.RETENTION_DAYS] = days }
    }

    suspend fun setDnsOnlyMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DNS_ONLY_MODE] = enabled }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AI_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEVELOPER_MODE] = enabled }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun setFirstRun(firstRun: Boolean) {
        context.dataStore.edit { it[Keys.FIRST_RUN] = firstRun }
    }

    suspend fun setVpnPermissionGranted(granted: Boolean) {
        context.dataStore.edit { it[Keys.VPN_PERMISSION_GRANTED] = granted }
    }

    suspend fun setAutoStartVpn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_START_VPN] = enabled }
    }
}

// ── VPN Repository ────────────────────────────────────────────────────────────

@Singleton
class VpnRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VpnRepository"
    }

    private val _vpnState = MutableStateFlow(
        VpnState(
            if (NetFlowVpnService.isRunning) VpnStatus.CONNECTED else VpnStatus.DISCONNECTED,
            0L
        )
    )
    val vpnState: StateFlow<VpnState> = _vpnState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var uptimeJob: Job? = null

    init {
        // Poll VPN service status periodically
        scope.launch {
            while (true) {
                val isRunning = NetFlowVpnService.isRunning
                val current = _vpnState.value
                if (isRunning && current.status != VpnStatus.CONNECTED) {
                    _vpnState.value = VpnState(VpnStatus.CONNECTED, 0L)
                    startUptimeCounter()
                } else if (!isRunning && current.status == VpnStatus.CONNECTED) {
                    _vpnState.value = VpnState(VpnStatus.DISCONNECTED, 0L)
                    uptimeJob?.cancel()
                }
                delay(1000)
            }
        }
    }

    fun startVpn() {
        _vpnState.value = VpnState(VpnStatus.RECONNECTING, 0L)
        try {
            val intent = Intent(context, NetFlowVpnService::class.java).apply {
                action = NetFlowVpnService.ACTION_START
            }
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN service", e)
            _vpnState.value = VpnState(VpnStatus.DISCONNECTED, 0L)
        }
    }

    fun stopVpn() {
        try {
            val intent = Intent(context, NetFlowVpnService::class.java).apply {
                action = NetFlowVpnService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop VPN service", e)
        }
        _vpnState.value = VpnState(VpnStatus.DISCONNECTED, 0L)
        uptimeJob?.cancel()
    }

    private fun startUptimeCounter() {
        uptimeJob?.cancel()
        uptimeJob = scope.launch {
            var seconds = 0L
            while (true) {
                _vpnState.value = VpnState(VpnStatus.CONNECTED, seconds)
                delay(1000)
                seconds++
            }
        }
    }
}

// ── Traffic Repository ────────────────────────────────────────────────────────

@Singleton
class TrafficRepository @Inject constructor(
    private val connectionDao: ConnectionDao,
    private val appDao: AppDao,
    private val domainDao: DomainDao,
    private val alertDao: AlertDao,
    private val predictionDao: PredictionSnapshotDao,
    private val dnsQueryDao: DnsQueryDao
) {
    companion object {
        private val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)
        private val SEVEN_DAYS_MS = TimeUnit.DAYS.toMillis(7)
    }

    private val gson = Gson()

    /**
     * Live traffic flows — streams from the in-memory FlowTracker
     * via the VPN service. Emits empty list when VPN is off.
     */
    fun liveTrafficFlow(): Flow<List<TrafficFlow>> = flow {
        while (true) {
            val tracker = NetFlowVpnService.activeFlowTracker
            if (tracker != null) {
                emit(tracker.liveFlows.value)
            } else {
                emit(emptyList())
            }
            delay(1500)
        }
    }

    /**
     * Traffic summary: total bytes sent/received today + hourly breakdown.
     */
    fun getTrafficSummary(): Flow<TrafficSummary> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            val dayStart = now - ONE_DAY_MS

            val totalSent = connectionDao.observeTotalSentSince(dayStart).first()
            val totalReceived = connectionDao.observeTotalReceivedSince(dayStart).first()

            val hourlyBuckets = connectionDao.getHourlyBreakdown(dayStart)
            val hourlyData = LongArray(24) { 0L }
            for (bucket in hourlyBuckets) {
                val slot = bucket.hourSlot.toInt().coerceIn(0, 23)
                hourlyData[slot] = bucket.totalBytes
            }

            emit(
                TrafficSummary(
                    totalSentBytes = totalSent,
                    totalReceivedBytes = totalReceived,
                    hourlyDataPoints = hourlyData.toList()
                )
            )
            delay(5000)
        }
    }

    /**
     * All monitored apps with today's traffic stats.
     */
    fun getApps(): Flow<List<AppNetworkInfo>> {
        val since = System.currentTimeMillis() - ONE_DAY_MS
        return connectionDao.observeAppsWithStats(since).map { appsList ->
            appsList.map { app ->
                val sevenDaysSince = System.currentTimeMillis() - SEVEN_DAYS_MS
                val appEntity = appDao.getByPackage(app.packageName)
                val dailyBuckets = if (appEntity != null) {
                    connectionDao.getDailyBytesForApp(appEntity.id, sevenDaysSince)
                } else emptyList()

                val weeklyData = LongArray(7) { 0L }
                val todaySlot = System.currentTimeMillis() / 86_400_000L
                for (bucket in dailyBuckets) {
                    val dayOffset = (todaySlot - bucket.daySlot).toInt()
                    if (dayOffset in 0..6) {
                        weeklyData[6 - dayOffset] = bucket.totalBytes
                    }
                }

                AppNetworkInfo(
                    packageName = app.packageName,
                    appName = app.appName,
                    dataSentToday = app.totalBytesSent,
                    dataReceivedToday = app.totalBytesReceived,
                    requestCountToday = app.connectionCount,
                    riskLevel = try {
                        RiskLevel.valueOf(app.riskLevel)
                    } catch (_: Exception) {
                        RiskLevel.UNKNOWN
                    },
                    monitorStatus = try {
                        AppMonitorStatus.valueOf(app.monitorStatus)
                    } catch (_: Exception) {
                        AppMonitorStatus.MONITORED
                    },
                    domainCount = app.domainCount,
                    weeklyDataBytes = weeklyData.toList(),
                    predictionText = "",
                    rules = AppRules(
                        blockBackground = app.blockBackground,
                        blockTrackers = app.blockTrackers,
                        alwaysAllowOnWifi = app.alwaysAllowOnWifi
                    )
                )
            }
        }
    }

    /**
     * Domains contacted by a specific app with aggregated stats.
     */
    fun getAppDomains(packageName: String): Flow<List<DomainInfo>> {
        return domainDao.observeDomainsForApp(packageName).map { domainsList ->
            domainsList.map { d ->
                DomainInfo(
                    domain = d.name,
                    ipAddress = "",
                    port = 443,
                    category = try {
                        DomainCategory.valueOf(d.category)
                    } catch (_: Exception) {
                        DomainCategory.UNKNOWN
                    },
                    riskLevel = categoryToRisk(d.category),
                    requestCount = d.requestCount,
                    totalBytesSent = d.totalBytesSent,
                    totalBytesReceived = d.totalBytesReceived,
                    firstSeen = d.firstSeen,
                    lastSeen = d.lastSeen,
                    geoCountry = null,
                    geoRegion = null,
                    asn = null,
                    asnOrg = null,
                    securityAssessment = buildSecurityAssessment(d.category),
                    isTrusted = d.isTrusted,
                    isBlocked = d.isBlocked
                )
            }
        }
    }

    /**
     * Single domain info with stats across all apps.
     */
    fun getDomainInfo(domainName: String): Flow<DomainInfo?> {
        return domainDao.observeDomainByName(domainName).map { d ->
            d?.let {
                DomainInfo(
                    domain = it.name,
                    ipAddress = "",
                    port = 443,
                    category = try {
                        DomainCategory.valueOf(it.category)
                    } catch (_: Exception) {
                        DomainCategory.UNKNOWN
                    },
                    riskLevel = categoryToRisk(it.category),
                    requestCount = it.requestCount,
                    totalBytesSent = it.totalBytesSent,
                    totalBytesReceived = it.totalBytesReceived,
                    firstSeen = it.firstSeen,
                    lastSeen = it.lastSeen,
                    securityAssessment = buildSecurityAssessment(it.category),
                    isTrusted = it.isTrusted,
                    isBlocked = it.isBlocked
                )
            }
        }
    }

    /**
     * Prediction results from the latest snapshot.
     */
    fun getPrediction(): Flow<PredictionResult> {
        return predictionDao.observeLatest().map { snapshot ->
            if (snapshot == null) {
                PredictionResult(
                    deviceRiskLevel = RiskLevel.UNKNOWN,
                    summary = "Start monitoring to generate traffic insights and predictions.",
                    appsToWatch = emptyList(),
                    domainsToWatch = emptyList(),
                    weeklyRisk = List(7) { RiskLevel.UNKNOWN },
                    lastUpdated = 0L
                )
            } else {
                val appsAtRisk = try {
                    val type = object : TypeToken<List<AppRiskJsonData>>() {}.type
                    val list: List<AppRiskJsonData> = gson.fromJson(snapshot.appsAtRiskJson, type)
                    list.map {
                        AppRiskEntry(
                            packageName = it.packageName,
                            appName = it.appName,
                            riskLevel = try { RiskLevel.valueOf(it.riskLevel) } catch (_: Exception) { RiskLevel.UNKNOWN },
                            reason = it.reason
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }

                val domainsAtRisk = try {
                    val type = object : TypeToken<List<DomainRiskJsonData>>() {}.type
                    val list: List<DomainRiskJsonData> = gson.fromJson(snapshot.domainsAtRiskJson, type)
                    list.map {
                        DomainRiskEntry(
                            domain = it.domain,
                            riskLevel = try { RiskLevel.valueOf(it.riskLevel) } catch (_: Exception) { RiskLevel.UNKNOWN },
                            reason = it.reason,
                            appCount = it.appCount
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }

                val weeklyRisk = List(7) {
                    try { RiskLevel.valueOf(snapshot.deviceRiskLevel) }
                    catch (_: Exception) { RiskLevel.UNKNOWN }
                }

                PredictionResult(
                    deviceRiskLevel = try { RiskLevel.valueOf(snapshot.deviceRiskLevel) }
                        catch (_: Exception) { RiskLevel.UNKNOWN },
                    summary = snapshot.summary,
                    appsToWatch = appsAtRisk,
                    domainsToWatch = domainsAtRisk,
                    weeklyRisk = weeklyRisk,
                    lastUpdated = snapshot.createdAt
                )
            }
        }
    }

    /** Alerts from the database. */
    fun getAlerts(): Flow<List<NetworkAlert>> {
        return alertDao.observeAll().map { entities ->
            entities.map { alert ->
                val appEntity = alert.appId?.let { appDao.getById(it) }
                NetworkAlert(
                    id = alert.id.toString(),
                    type = try { AlertType.valueOf(alert.type) }
                        catch (_: Exception) { AlertType.SUSPICIOUS_DOMAIN },
                    title = alert.title,
                    description = alert.description,
                    timestamp = alert.createdAt,
                    packageName = appEntity?.packageName,
                    domain = alert.domainId?.let { domainDao.getById(it)?.name },
                    isRead = alert.isRead,
                    isMuted = alert.isMuted
                )
            }
        }
    }

    /** Block or unblock a domain. */
    suspend fun setDomainBlocked(domainName: String, blocked: Boolean) {
        domainDao.setBlocked(domainName, blocked)
        if (blocked) {
            val domainEntity = domainDao.getByName(domainName)
            alertDao.insert(
                AlertEntity(
                    type = "BLOCK_RULE_TRIGGERED",
                    title = "Domain blocked",
                    description = "$domainName has been blocked",
                    domainId = domainEntity?.id,
                    severity = "LOW"
                )
            )
        }
    }

    /** Trust or untrust a domain. */
    suspend fun setDomainTrusted(domainName: String, trusted: Boolean) {
        domainDao.setTrusted(domainName, trusted)
    }

    /** Update app rules. */
    suspend fun updateAppRules(packageName: String, rules: AppRules) {
        appDao.updateRules(
            pkg = packageName,
            blockBg = rules.blockBackground,
            blockTrack = rules.blockTrackers,
            allowWifi = rules.alwaysAllowOnWifi
        )
    }

    /** Update app monitor status. */
    suspend fun updateAppMonitorStatus(packageName: String, status: AppMonitorStatus) {
        val app = appDao.getByPackage(packageName)
        if (app != null) {
            appDao.updateMonitorStatus(app.id, status.name)
        }
    }

    /** Dismiss (mark read) an alert. */
    suspend fun dismissAlert(alertId: String) {
        alertId.toLongOrNull()?.let { alertDao.markRead(it) }
    }

    /** Clear all logged data. */
    suspend fun clearAllData() {
        connectionDao.deleteAll()
        dnsQueryDao.deleteAll()
        alertDao.deleteAll()
        predictionDao.deleteAll()
    }

    private fun categoryToRisk(category: String): RiskLevel = when (category) {
        "SUSPICIOUS" -> RiskLevel.HIGH
        "TRACKING", "ADS" -> RiskLevel.MEDIUM
        "CDN", "TRUSTED" -> RiskLevel.LOW
        else -> RiskLevel.UNKNOWN
    }

    private fun buildSecurityAssessment(category: String): String = when (category) {
        "SUSPICIOUS" -> "This domain shows suspicious patterns. Treat with caution."
        "TRACKING" -> "This domain is used for tracking or analytics. Consider blocking."
        "ADS" -> "This domain serves advertisements. Blocking will not affect app functionality."
        "CDN" -> "Well-established content delivery network. Generally safe."
        "TRUSTED" -> "Well-known service provider. Generally safe."
        else -> "No classification available for this domain."
    }

    // JSON helper classes
    private data class AppRiskJsonData(
        val packageName: String = "",
        val appName: String = "",
        val riskLevel: String = "UNKNOWN",
        val reason: String = ""
    )

    private data class DomainRiskJsonData(
        val domain: String = "",
        val riskLevel: String = "UNKNOWN",
        val reason: String = "",
        val appCount: Int = 0
    )
}
