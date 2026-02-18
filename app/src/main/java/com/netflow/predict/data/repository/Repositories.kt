package com.netflow.predict.data.repository

import com.netflow.predict.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Stub VPN repository.
 * Replace internals with real VpnService IPC / Room queries.
 */
@Singleton
class VpnRepository @Inject constructor() {

    private val _vpnState = MutableStateFlow(VpnState(VpnStatus.DISCONNECTED, 0L))
    val vpnState: StateFlow<VpnState> = _vpnState

    fun startVpn() {
        _vpnState.value = VpnState(VpnStatus.CONNECTED, 0L)
    }

    fun stopVpn() {
        _vpnState.value = VpnState(VpnStatus.DISCONNECTED, 0L)
    }
}

/**
 * Stub traffic repository — emits realistic-looking fake data.
 * Replace with Room + VPN packet parser.
 */
@Singleton
class TrafficRepository @Inject constructor() {

    private val sampleApps = listOf(
        "WhatsApp" to "com.whatsapp",
        "Chrome" to "com.android.chrome",
        "TikTok" to "com.zhiliaoapp.musically",
        "Maps" to "com.google.android.apps.maps",
        "System" to "android",
        "Weather" to "com.weather.Weather",
        "Unknown App" to "com.unknown.app123"
    )
    private val sampleDomains = listOf(
        "graph.facebook.com" to DomainCategory.TRACKING,
        "api.whatsapp.net" to DomainCategory.CDN,
        "doubleclick.net" to DomainCategory.ADS,
        "data-harvest.io" to DomainCategory.SUSPICIOUS,
        "pixel.ads-tracker.net" to DomainCategory.TRACKING,
        "maps.googleapis.com" to DomainCategory.CDN,
        "unknown-domain-xyz.net" to DomainCategory.UNKNOWN,
        "tiktokv.com" to DomainCategory.CDN
    )

    fun liveTrafficFlow(): Flow<List<TrafficFlow>> = flow {
        var counter = 0
        while (true) {
            val batch = (1..Random.nextInt(1, 4)).map {
                val app = sampleApps.random()
                val dom = sampleDomains.random()
                TrafficFlow(
                    id             = "flow_${counter++}",
                    appPackage     = app.second,
                    appName        = app.first,
                    domain         = dom.first,
                    ipAddress      = "157.${Random.nextInt(0,255)}.${Random.nextInt(0,255)}.${Random.nextInt(0,255)}",
                    port           = listOf(443, 80, 853, 5228).random(),
                    protocol       = Protocol.values().random(),
                    direction      = Direction.values().random(),
                    bytesSent      = Random.nextLong(100, 50_000),
                    bytesReceived  = Random.nextLong(100, 200_000),
                    bytesPerSecond = Random.nextLong(0, 25_000),
                    firstSeen      = System.currentTimeMillis() - Random.nextLong(0, 3_600_000),
                    lastSeen       = System.currentTimeMillis(),
                    riskLevel      = if (dom.second == DomainCategory.SUSPICIOUS) RiskLevel.HIGH
                                     else if (dom.second == DomainCategory.TRACKING) RiskLevel.MEDIUM
                                     else RiskLevel.LOW,
                    category       = dom.second,
                    sparklineData  = List(5) { Random.nextLong(0, 20_000) }
                )
            }
            emit(batch)
            delay(1500)
        }
    }

    fun getTrafficSummary(): Flow<TrafficSummary> = flow {
        emit(TrafficSummary(
            totalSentBytes     = 25_500_000L,
            totalReceivedBytes = 196_800_000L,
            hourlyDataPoints   = List(24) { Random.nextLong(0, 10_000_000) }
        ))
    }

    fun getApps(): Flow<List<AppNetworkInfo>> = flow {
        emit(sampleApps.mapIndexed { index, app ->
            AppNetworkInfo(
                packageName        = app.second,
                appName            = app.first,
                dataSentToday      = Random.nextLong(1_000_000, 150_000_000),
                dataReceivedToday  = Random.nextLong(1_000_000, 500_000_000),
                requestCountToday  = Random.nextInt(50, 3000),
                riskLevel          = when (index % 3) { 0 -> RiskLevel.LOW; 1 -> RiskLevel.MEDIUM; else -> RiskLevel.HIGH },
                monitorStatus      = AppMonitorStatus.MONITORED,
                domainCount        = Random.nextInt(3, 60),
                weeklyDataBytes    = List(7) { Random.nextLong(5_000_000, 80_000_000) },
                predictionText     = when (index % 3) {
                    0 -> "No unusual activity detected."
                    1 -> "Contacted 3 unfamiliar domains in the past 48 hours."
                    else -> "Immediate outbound activity to unknown IP."
                }
            )
        })
    }

    fun getAppDomains(packageName: String): Flow<List<DomainInfo>> = flow {
        emit(sampleDomains.map { (domain, cat) ->
            DomainInfo(
                domain               = domain,
                ipAddress            = "157.${Random.nextInt(0,255)}.${Random.nextInt(0,255)}.1",
                port                 = 443,
                category             = cat,
                riskLevel            = if (cat == DomainCategory.SUSPICIOUS) RiskLevel.HIGH
                                       else if (cat == DomainCategory.TRACKING) RiskLevel.MEDIUM
                                       else RiskLevel.LOW,
                requestCount         = Random.nextInt(5, 500),
                totalBytesSent       = Random.nextLong(10_000, 5_000_000),
                totalBytesReceived   = Random.nextLong(10_000, 10_000_000),
                firstSeen            = System.currentTimeMillis() - 86_400_000L * 3,
                lastSeen             = System.currentTimeMillis() - 60_000L,
                geoCountry           = "United States",
                geoRegion            = "California",
                asn                  = "AS32934",
                asnOrg               = "Meta Platforms, Inc.",
                securityAssessment   = when (cat) {
                    DomainCategory.SUSPICIOUS -> "This domain has no known classification. Treat with caution."
                    DomainCategory.TRACKING  -> "This domain is used for advertising or tracking. Consider blocking it."
                    DomainCategory.ADS       -> "This domain is used for advertising or tracking. Consider blocking it."
                    DomainCategory.CDN       -> "This domain is well-established and commonly used for content delivery."
                    else                     -> "No classification available."
                }
            )
        })
    }

    fun getPrediction(): Flow<PredictionResult> = flow {
        emit(PredictionResult(
            deviceRiskLevel = RiskLevel.MEDIUM,
            summary         = "2 apps likely to contact suspicious domains today.",
            appsToWatch     = listOf(
                AppRiskEntry("com.zhiliaoapp.musically", "TikTok", RiskLevel.HIGH,
                    "Spike in requests to 4 unfamiliar domains since Monday."),
                AppRiskEntry("com.weather.Weather", "Weather App", RiskLevel.MEDIUM,
                    "Background traffic doubled compared to last week."),
                AppRiskEntry("com.unknown.app123", "Unknown App", RiskLevel.HIGH,
                    "New install with immediate outbound activity to unknown IP.")
            ),
            domainsToWatch  = listOf(
                DomainRiskEntry("data-harvest.io", RiskLevel.HIGH,
                    "Contacted by 3 apps. No known legitimate use.", 3),
                DomainRiskEntry("pixel.ads-tracker.net", RiskLevel.MEDIUM,
                    "Identified as advertising tracker by blocklist.", 1)
            ),
            weeklyRisk      = listOf(
                RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.LOW,
                RiskLevel.HIGH, RiskLevel.MEDIUM, RiskLevel.MEDIUM, RiskLevel.LOW
            ),
            lastUpdated     = System.currentTimeMillis()
        ))
    }

    fun getAlerts(): Flow<List<NetworkAlert>> = flow {
        val now = System.currentTimeMillis()
        emit(listOf(
            NetworkAlert("a1", AlertType.SUSPICIOUS_DOMAIN,
                "Suspicious domain contact",
                "com.tiktok.android → data-harvest.io",
                now - 600_000L, "com.zhiliaoapp.musically", "data-harvest.io"),
            NetworkAlert("a2", AlertType.DATA_SPIKE,
                "Data usage spike",
                "Weather App sent 3× more data than usual",
                now - 3_600_000L, "com.weather.Weather"),
            NetworkAlert("a3", AlertType.NEW_APP,
                "New app with immediate network activity",
                "Unknown App contacted 5 domains within seconds of install",
                now - 7_200_000L, "com.unknown.app123"),
            NetworkAlert("a4", AlertType.RISK_LEVEL_CHANGED,
                "Risk level increased",
                "TikTok risk changed from Low to High",
                now - 86_400_000L, "com.zhiliaoapp.musically")
        ))
    }
}
