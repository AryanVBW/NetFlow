package com.netflow.predict.data.local.dao

import androidx.room.*
import com.netflow.predict.data.local.entity.*
import kotlinx.coroutines.flow.Flow

// ── App DAO ───────────────────────────────────────────────────────────────────

@Dao
interface AppDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: AppEntity): Long

    @Update
    suspend fun update(app: AppEntity)

    @Query("SELECT * FROM apps WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): AppEntity?

    @Query("SELECT * FROM apps WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AppEntity?

    @Query("SELECT * FROM apps ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<AppEntity>>

    @Query("UPDATE apps SET lastSeen = :ts WHERE id = :id")
    suspend fun touch(id: Long, ts: Long = System.currentTimeMillis())

    @Query("UPDATE apps SET riskLevel = :level WHERE id = :id")
    suspend fun updateRiskLevel(id: Long, level: String)

    @Query("UPDATE apps SET monitorStatus = :status WHERE id = :id")
    suspend fun updateMonitorStatus(id: Long, status: String)

    @Query("""
        UPDATE apps SET 
            blockBackground = :blockBg, 
            blockTrackers = :blockTrack, 
            alwaysAllowOnWifi = :allowWifi 
        WHERE packageName = :pkg
    """)
    suspend fun updateRules(pkg: String, blockBg: Boolean, blockTrack: Boolean, allowWifi: Boolean)

    @Query("DELETE FROM apps")
    suspend fun deleteAll()

    /** Upsert: insert or update lastSeen if already exists */
    @Transaction
    suspend fun upsert(pkg: String, name: String, isSystem: Boolean): Long {
        val existing = getByPackage(pkg)
        return if (existing != null) {
            touch(existing.id)
            existing.id
        } else {
            insert(AppEntity(packageName = pkg, appName = name, isSystemApp = isSystem))
        }
    }
}

// ── Domain DAO ────────────────────────────────────────────────────────────────

@Dao
interface DomainDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(domain: DomainEntity): Long

    @Update
    suspend fun update(domain: DomainEntity)

    @Query("SELECT * FROM domains WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): DomainEntity?

    @Query("SELECT * FROM domains WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DomainEntity?

    @Query("SELECT * FROM domains WHERE isBlocked = 1")
    suspend fun getBlockedDomains(): List<DomainEntity>

    @Query("SELECT * FROM domains WHERE isBlocked = 1")
    fun observeBlockedDomains(): Flow<List<DomainEntity>>

    @Query("UPDATE domains SET isBlocked = :blocked WHERE name = :name")
    suspend fun setBlocked(name: String, blocked: Boolean)

    @Query("UPDATE domains SET isTrusted = :trusted WHERE name = :name")
    suspend fun setTrusted(name: String, trusted: Boolean)

    @Query("UPDATE domains SET lastSeen = :ts WHERE id = :id")
    suspend fun touch(id: Long, ts: Long = System.currentTimeMillis())

    @Query("UPDATE domains SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("DELETE FROM domains")
    suspend fun deleteAll()

    /** Upsert: insert or update lastSeen */
    @Transaction
    suspend fun upsert(name: String, category: String = "UNKNOWN"): Long {
        val existing = getByName(name)
        return if (existing != null) {
            touch(existing.id)
            existing.id
        } else {
            insert(DomainEntity(name = name, category = category))
        }
    }

    /**
     * Get domains contacted by a specific app, with aggregated stats.
     */
    @Query("""
        SELECT 
            d.id AS domainId,
            d.name,
            d.category,
            d.reputationScore,
            d.isBlocked,
            d.isTrusted,
            d.firstSeen,
            d.lastSeen,
            COALESCE(SUM(c.bytesSent), 0) AS totalBytesSent,
            COALESCE(SUM(c.bytesReceived), 0) AS totalBytesReceived,
            COUNT(c.id) AS requestCount,
            COUNT(DISTINCT c.appId) AS appCount
        FROM domains d
        LEFT JOIN connections c ON c.domainId = d.id
        LEFT JOIN apps a ON c.appId = a.id
        WHERE a.packageName = :pkg
        GROUP BY d.id
        ORDER BY d.lastSeen DESC
    """)
    fun observeDomainsForApp(pkg: String): Flow<List<DomainWithStats>>

    /**
     * Get a single domain with aggregated stats across all apps.
     */
    @Query("""
        SELECT 
            d.id AS domainId,
            d.name,
            d.category,
            d.reputationScore,
            d.isBlocked,
            d.isTrusted,
            d.firstSeen,
            d.lastSeen,
            COALESCE(SUM(c.bytesSent), 0) AS totalBytesSent,
            COALESCE(SUM(c.bytesReceived), 0) AS totalBytesReceived,
            COUNT(c.id) AS requestCount,
            COUNT(DISTINCT c.appId) AS appCount
        FROM domains d
        LEFT JOIN connections c ON c.domainId = d.id
        WHERE d.name = :name
        GROUP BY d.id
    """)
    fun observeDomainByName(name: String): Flow<DomainWithStats?>
}

// ── Connection DAO ────────────────────────────────────────────────────────────

@Dao
interface ConnectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conn: ConnectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conns: List<ConnectionEntity>)

    @Update
    suspend fun update(conn: ConnectionEntity)

    @Query("""
        SELECT * FROM connections
        ORDER BY timestampStart DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int = 200): Flow<List<ConnectionEntity>>

    @Query("""
        SELECT * FROM connections
        WHERE appId = :appId
        ORDER BY timestampStart DESC
        LIMIT :limit
    """)
    fun observeForApp(appId: Long, limit: Int = 100): Flow<List<ConnectionEntity>>

    @Query("""
        SELECT COALESCE(SUM(bytesSent), 0) FROM connections
        WHERE timestampStart >= :since
    """)
    fun observeTotalSentSince(since: Long): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(bytesReceived), 0) FROM connections
        WHERE timestampStart >= :since
    """)
    fun observeTotalReceivedSince(since: Long): Flow<Long>

    /**
     * Hourly data points for the last 24 hours.
     * Returns pairs of (hourOffset, totalBytes).
     */
    @Query("""
        SELECT 
            ((timestampStart - :dayStart) / 3600000) AS hourSlot,
            COALESCE(SUM(bytesSent + bytesReceived), 0) AS totalBytes
        FROM connections
        WHERE timestampStart >= :dayStart
        GROUP BY hourSlot
        ORDER BY hourSlot ASC
    """)
    suspend fun getHourlyBreakdown(dayStart: Long): List<HourlyBucket>

    @Query("""
        SELECT 
            a.packageName,
            a.appName,
            a.isSystemApp,
            a.monitorStatus,
            a.riskLevel,
            a.blockBackground,
            a.blockTrackers,
            a.alwaysAllowOnWifi,
            COALESCE(SUM(c.bytesSent), 0) AS totalBytesSent,
            COALESCE(SUM(c.bytesReceived), 0) AS totalBytesReceived,
            COUNT(c.id) AS connectionCount,
            COUNT(DISTINCT c.domainId) AS domainCount
        FROM apps a
        LEFT JOIN connections c ON c.appId = a.id AND c.timestampStart >= :since
        GROUP BY a.id
        ORDER BY totalBytesSent + totalBytesReceived DESC
    """)
    fun observeAppsWithStats(since: Long): Flow<List<AppWithStats>>

    @Query("DELETE FROM connections WHERE timestampStart < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM connections")
    suspend fun deleteAll()

    /** Count connections for an app since a timestamp */
    @Query("""
        SELECT COUNT(*) FROM connections 
        WHERE appId = :appId AND timestampStart >= :since
    """)
    suspend fun countForAppSince(appId: Long, since: Long): Int

    /** Sum bytes sent by an app since a timestamp */
    @Query("""
        SELECT COALESCE(SUM(bytesSent), 0) FROM connections
        WHERE appId = :appId AND timestampStart >= :since
    """)
    suspend fun sumBytesSentForAppSince(appId: Long, since: Long): Long

    /** Count distinct new domains contacted by an app in a time window */
    @Query("""
        SELECT COUNT(DISTINCT c.domainId) FROM connections c
        WHERE c.appId = :appId 
          AND c.timestampStart >= :since
          AND c.domainId NOT IN (
              SELECT DISTINCT c2.domainId FROM connections c2 
              WHERE c2.appId = :appId AND c2.timestampStart < :since
              AND c2.domainId IS NOT NULL
          )
          AND c.domainId IS NOT NULL
    """)
    suspend fun countNewDomainsForAppSince(appId: Long, since: Long): Int

    /** Daily totals for an app over the last N days */
    @Query("""
        SELECT 
            (timestampStart / 86400000) AS daySlot,
            COALESCE(SUM(bytesSent + bytesReceived), 0) AS totalBytes
        FROM connections
        WHERE appId = :appId AND timestampStart >= :since
        GROUP BY daySlot
        ORDER BY daySlot ASC
    """)
    suspend fun getDailyBytesForApp(appId: Long, since: Long): List<DailyBucket>
}

data class HourlyBucket(val hourSlot: Long, val totalBytes: Long)
data class DailyBucket(val daySlot: Long, val totalBytes: Long)

// ── DNS Query DAO ─────────────────────────────────────────────────────────────

@Dao
interface DnsQueryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(query: DnsQueryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(queries: List<DnsQueryEntity>)

    @Query("SELECT * FROM dns_queries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<DnsQueryEntity>>

    @Query("SELECT resolvedIp FROM dns_queries WHERE queryDomain = :domain ORDER BY timestamp DESC LIMIT 1")
    suspend fun getIpForDomain(domain: String): String?

    @Query("SELECT queryDomain FROM dns_queries WHERE resolvedIp = :ip ORDER BY timestamp DESC LIMIT 1")
    suspend fun getDomainForIp(ip: String): String?

    @Query("DELETE FROM dns_queries WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM dns_queries")
    suspend fun deleteAll()
}

// ── Alert DAO ─────────────────────────────────────────────────────────────────

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: AlertEntity): Long

    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE isRead = 0 ORDER BY createdAt DESC")
    fun observeUnread(): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE alerts SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE alerts SET isMuted = 1 WHERE id = :id")
    suspend fun mute(id: Long)

    @Query("UPDATE alerts SET resolvedAt = :ts WHERE id = :id")
    suspend fun resolve(id: Long, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}

// ── Prediction Snapshot DAO ───────────────────────────────────────────────────

@Dao
interface PredictionSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: PredictionSnapshotEntity): Long

    @Query("SELECT * FROM prediction_snapshots ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<PredictionSnapshotEntity?>

    @Query("SELECT * FROM prediction_snapshots ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 7): Flow<List<PredictionSnapshotEntity>>

    @Query("DELETE FROM prediction_snapshots")
    suspend fun deleteAll()
}

// ── Traffic Stats DAO ─────────────────────────────────────────────────────────

@Dao
interface TrafficStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: TrafficStatsEntity): Long

    @Query("""
        SELECT COALESCE(SUM(bytesSent), 0) FROM traffic_stats
        WHERE appId = :appId AND dayTimestamp >= :since
    """)
    suspend fun sumBytesSentForApp(appId: Long, since: Long): Long

    /** Average daily bytes sent for an app over a time period */
    @Query("""
        SELECT COALESCE(AVG(dailyTotal), 0) FROM (
            SELECT SUM(bytesSent) as dailyTotal
            FROM traffic_stats
            WHERE appId = :appId AND dayTimestamp >= :since AND dayTimestamp < :until
            GROUP BY dayTimestamp
        )
    """)
    suspend fun avgDailyBytesSentForApp(appId: Long, since: Long, until: Long): Long

    @Query("DELETE FROM traffic_stats WHERE hourTimestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM traffic_stats")
    suspend fun deleteAll()
}
