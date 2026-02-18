package com.netflow.predict.data.model

import androidx.compose.ui.graphics.Color

// ── VPN ───────────────────────────────────────────────────────────────────────

enum class VpnStatus { CONNECTED, RECONNECTING, DISCONNECTED }

data class VpnState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val uptimeSeconds: Long = 0L
)

// ── Traffic ───────────────────────────────────────────────────────────────────

enum class Protocol { TCP, UDP, DNS, UNKNOWN }
enum class Direction { INBOUND, OUTBOUND, BIDIRECTIONAL }

data class TrafficFlow(
    val id: String,
    val appPackage: String,
    val appName: String,
    val domain: String,
    val ipAddress: String,
    val port: Int,
    val protocol: Protocol,
    val direction: Direction,
    val bytesSent: Long,
    val bytesReceived: Long,
    val bytesPerSecond: Long,
    val firstSeen: Long,   // epoch ms
    val lastSeen: Long,    // epoch ms
    val riskLevel: RiskLevel,
    val category: DomainCategory,
    val sparklineData: List<Long> = emptyList() // last 5 samples bytes/s
)

// ── Risk ──────────────────────────────────────────────────────────────────────

enum class RiskLevel { LOW, MEDIUM, HIGH, UNKNOWN }

// ── Domain ────────────────────────────────────────────────────────────────────

enum class DomainCategory { CDN, ADS, TRACKING, UNKNOWN, SUSPICIOUS, TRUSTED }

data class DomainInfo(
    val domain: String,
    val ipAddress: String,
    val port: Int,
    val category: DomainCategory,
    val riskLevel: RiskLevel,
    val requestCount: Int,
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val firstSeen: Long,
    val lastSeen: Long,
    val geoCountry: String? = null,
    val geoRegion: String? = null,
    val asn: String? = null,
    val asnOrg: String? = null,
    val securityAssessment: String = "",
    val isTrusted: Boolean = false,
    val isBlocked: Boolean = false
)

// ── App ───────────────────────────────────────────────────────────────────────

enum class AppMonitorStatus { MONITORED, IGNORED, BLOCKED }

data class AppNetworkInfo(
    val packageName: String,
    val appName: String,
    val dataSentToday: Long,      // bytes
    val dataReceivedToday: Long,  // bytes
    val requestCountToday: Int,
    val riskLevel: RiskLevel,
    val monitorStatus: AppMonitorStatus,
    val domainCount: Int,
    val weeklyDataBytes: List<Long> = List(7) { 0L }, // Mon..Sun
    val predictionText: String = "",
    val rules: AppRules = AppRules()
)

data class AppRules(
    val blockBackground: Boolean = false,
    val blockTrackers: Boolean = false,
    val alwaysAllowOnWifi: Boolean = true
)

// ── Predictions ───────────────────────────────────────────────────────────────

data class PredictionResult(
    val deviceRiskLevel: RiskLevel,
    val summary: String,
    val appsToWatch: List<AppRiskEntry>,
    val domainsToWatch: List<DomainRiskEntry>,
    val weeklyRisk: List<RiskLevel> = List(7) { RiskLevel.UNKNOWN }, // Mon..Sun
    val lastUpdated: Long = 0L
)

data class AppRiskEntry(
    val packageName: String,
    val appName: String,
    val riskLevel: RiskLevel,
    val reason: String
)

data class DomainRiskEntry(
    val domain: String,
    val riskLevel: RiskLevel,
    val reason: String,
    val appCount: Int
)

// ── Alerts ────────────────────────────────────────────────────────────────────

enum class AlertType {
    SUSPICIOUS_DOMAIN, DATA_SPIKE, NEW_APP, BLOCK_RULE_TRIGGERED, RISK_LEVEL_CHANGED
}

data class NetworkAlert(
    val id: String,
    val type: AlertType,
    val title: String,
    val description: String,
    val timestamp: Long,
    val packageName: String? = null,
    val domain: String? = null,
    val isRead: Boolean = false,
    val isMuted: Boolean = false
)

// ── Traffic summary ───────────────────────────────────────────────────────────

data class TrafficSummary(
    val totalSentBytes: Long,
    val totalReceivedBytes: Long,
    val hourlyDataPoints: List<Long> = List(24) { 0L } // bytes per hour for last 24h
)

// ── Settings ──────────────────────────────────────────────────────────────────

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class LogRetentionDays(val days: Int) { SEVEN(7), THIRTY(30), NINETY(90) }
enum class ExportFormat { CSV, PCAP }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: String = "system",
    val retentionDays: Int = 30,
    val dnsOnlyMode: Boolean = false,
    val aiEnabled: Boolean = true,
    val developerMode: Boolean = false,
    val notificationsEnabled: Boolean = true
)
