package com.netflow.predict.data.local.entity

import androidx.room.*

// ── App Entity ────────────────────────────────────────────────────────────────

@Entity(
    tableName = "apps",
    indices = [
        Index(value = ["packageName"], unique = true)
    ]
)
data class AppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isMonitored: Boolean = true,
    val riskLevel: String = "UNKNOWN", // LOW, MEDIUM, HIGH, UNKNOWN
    val monitorStatus: String = "MONITORED", // MONITORED, IGNORED, BLOCKED
    val blockBackground: Boolean = false,
    val blockTrackers: Boolean = false,
    val alwaysAllowOnWifi: Boolean = true,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val notes: String = ""
)

// ── Domain Entity ─────────────────────────────────────────────────────────────

@Entity(
    tableName = "domains",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["category"]),
        Index(value = ["isBlocked"])
    ]
)
data class DomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "UNKNOWN", // CDN, ADS, TRACKING, UNKNOWN, SUSPICIOUS, TRUSTED
    val reputationScore: Float = 0.5f, // 0.0 = bad, 1.0 = good
    val isBlocked: Boolean = false,
    val isTrusted: Boolean = false,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)

// ── Connection Entity ─────────────────────────────────────────────────────────

@Entity(
    tableName = "connections",
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["id"],
            childColumns = ["appId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DomainEntity::class,
            parentColumns = ["id"],
            childColumns = ["domainId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["appId"]),
        Index(value = ["domainId"]),
        Index(value = ["timestampStart"]),
        Index(value = ["protocol"]),
        Index(value = ["dstIp"])
    ]
)
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appId: Long,
    val domainId: Long? = null,
    val timestampStart: Long,
    val timestampEnd: Long? = null,
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    val protocol: String, // TCP, UDP, DNS, UNKNOWN
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val direction: String = "OUTBOUND", // INBOUND, OUTBOUND, BIDIRECTIONAL
    val wasBlocked: Boolean = false
)

// ── DNS Query Entity ──────────────────────────────────────────────────────────

@Entity(
    tableName = "dns_queries",
    indices = [
        Index(value = ["queryDomain"]),
        Index(value = ["timestamp"]),
        Index(value = ["appId"])
    ]
)
data class DnsQueryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appId: Long? = null,
    val queryDomain: String,
    val resolvedIp: String? = null,
    val queryType: String = "A", // A, AAAA, CNAME, etc.
    val timestamp: Long = System.currentTimeMillis(),
    val wasBlocked: Boolean = false
)

// ── Alert Entity ──────────────────────────────────────────────────────────────

@Entity(
    tableName = "alerts",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["appId"]),
        Index(value = ["type"])
    ]
)
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // SUSPICIOUS_DOMAIN, DATA_SPIKE, NEW_APP, BLOCK_RULE_TRIGGERED, RISK_LEVEL_CHANGED, NEW_DOMAIN_BURST, TRACKER_DETECTED
    val title: String,
    val description: String,
    val appId: Long? = null,
    val domainId: Long? = null,
    val severity: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val isRead: Boolean = false,
    val isMuted: Boolean = false
)

// ── Prediction Snapshot Entity ────────────────────────────────────────────────

@Entity(
    tableName = "prediction_snapshots",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class PredictionSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceRiskScore: Float = 0f, // 0.0 - 1.0
    val deviceRiskLevel: String = "UNKNOWN",
    val summary: String = "",
    val appsAtRiskJson: String = "[]", // JSON array of {packageName, appName, riskLevel, reason}
    val domainsAtRiskJson: String = "[]" // JSON array of {domain, riskLevel, reason, appCount}
)

// ── Traffic Stats Snapshot (hourly aggregation) ───────────────────────────────

@Entity(
    tableName = "traffic_stats",
    indices = [
        Index(value = ["appId"]),
        Index(value = ["hourTimestamp"], unique = false),
        Index(value = ["dayTimestamp"])
    ]
)
data class TrafficStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appId: Long,
    val hourTimestamp: Long, // epoch millis, truncated to hour
    val dayTimestamp: Long,  // epoch millis, truncated to day
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val connectionCount: Int = 0,
    val newDomainCount: Int = 0,
    val trackerDomainCount: Int = 0
)

// ── Joined query results ──────────────────────────────────────────────────────

data class ConnectionWithDetails(
    @Embedded val connection: ConnectionEntity,
    @Relation(parentColumn = "appId", entityColumn = "id")
    val app: AppEntity?,
    @Relation(parentColumn = "domainId", entityColumn = "id")
    val domain: DomainEntity?
)

data class AppWithStats(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val monitorStatus: String,
    val riskLevel: String,
    val blockBackground: Boolean,
    val blockTrackers: Boolean,
    val alwaysAllowOnWifi: Boolean,
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val connectionCount: Int,
    val domainCount: Int
)

data class DomainWithStats(
    val domainId: Long,
    val name: String,
    val category: String,
    val reputationScore: Float,
    val isBlocked: Boolean,
    val isTrusted: Boolean,
    val firstSeen: Long,
    val lastSeen: Long,
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val requestCount: Int,
    val appCount: Int
)
