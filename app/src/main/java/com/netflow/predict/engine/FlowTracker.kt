package com.netflow.predict.engine

import android.util.Log
import com.netflow.predict.data.local.dao.ConnectionDao
import com.netflow.predict.data.local.dao.DnsQueryDao
import com.netflow.predict.data.local.dao.AppDao
import com.netflow.predict.data.local.dao.DomainDao
import com.netflow.predict.data.local.entity.ConnectionEntity
import com.netflow.predict.data.local.entity.DnsQueryEntity
import com.netflow.predict.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks network flows (connections) in memory, aggregates them, and
 * periodically flushes to the Room database.
 *
 * A "flow" is identified by a 5-tuple: (srcIp, srcPort, dstIp, dstPort, protocol).
 * The tracker maintains an in-memory flow table and batches DB writes for efficiency.
 */
class FlowTracker(
    private val connectionDao: ConnectionDao,
    private val dnsQueryDao: DnsQueryDao,
    private val appDao: AppDao,
    private val domainDao: DomainDao,
    private val appResolver: AppResolver
) {
    companion object {
        private const val TAG = "FlowTracker"
        private const val FLUSH_INTERVAL_MS = 5_000L // flush to DB every 5 seconds
        private const val FLOW_TIMEOUT_MS = 30_000L  // close idle flows after 30s
        private const val MAX_FLOWS = 2000            // max concurrent flows in memory
        private const val DNS_PORT = 53
    }

    /** In-memory flow table keyed by 5-tuple hash */
    private val flowTable = ConcurrentHashMap<String, ActiveFlow>()

    /** DNS cache: IP → domain name (for reverse lookups) */
    private val dnsCache = ConcurrentHashMap<String, String>()

    /** Recent live flows for UI consumption */
    private val _liveFlows = MutableStateFlow<List<TrafficFlow>>(emptyList())
    val liveFlows: StateFlow<List<TrafficFlow>> = _liveFlows

    private var flushJob: Job? = null
    private var scope: CoroutineScope? = null

    /** Represents an active (in-progress) network flow in memory */
    data class ActiveFlow(
        val key: String,
        val srcIp: String,
        val srcPort: Int,
        val dstIp: String,
        val dstPort: Int,
        val protocol: Int, // 6=TCP, 17=UDP
        val appUid: Int,
        var appId: Long = 0,         // Room FK, resolved lazily
        var domainId: Long? = null,  // Room FK, resolved via DNS cache
        var domain: String = "",     // resolved domain name
        val startTime: Long = System.currentTimeMillis(),
        var lastSeen: Long = System.currentTimeMillis(),
        var bytesSent: Long = 0,
        var bytesReceived: Long = 0,
        var packetCount: Int = 0,
        var direction: Direction = Direction.OUTBOUND,
        var wasBlocked: Boolean = false,
        var flushed: Boolean = false // has been written to DB at least once
    )

    fun start(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        flushJob = coroutineScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushToDB()
                evictStaleFlows()
                updateLiveFlows()
            }
        }
    }

    fun stop() {
        flushJob?.cancel()
        // Final flush
        scope?.launch {
            flushToDB()
            flowTable.clear()
        }
    }

    /**
     * Called by the VPN packet read loop for each parsed packet.
     * This is hot path — must be fast, no DB calls here.
     */
    fun onPacket(packet: PacketParser.ParsedPacket, appUid: Int, isOutbound: Boolean) {
        val key = flowKey(packet, isOutbound)

        val flow = flowTable.getOrPut(key) {
            if (flowTable.size >= MAX_FLOWS) {
                // Evict oldest flow
                val oldest = flowTable.entries.minByOrNull { it.value.lastSeen }
                oldest?.let { flowTable.remove(it.key) }
            }

            ActiveFlow(
                key = key,
                srcIp = if (isOutbound) packet.srcIp else packet.dstIp,
                srcPort = if (isOutbound) packet.srcPort else packet.dstPort,
                dstIp = if (isOutbound) packet.dstIp else packet.srcIp,
                dstPort = if (isOutbound) packet.dstPort else packet.srcPort,
                protocol = packet.protocol,
                appUid = appUid,
                domain = dnsCache[if (isOutbound) packet.dstIp else packet.srcIp] ?: "",
                direction = if (isOutbound) Direction.OUTBOUND else Direction.INBOUND
            )
        }

        flow.lastSeen = System.currentTimeMillis()
        flow.packetCount++
        if (isOutbound) {
            flow.bytesSent += packet.totalLength
        } else {
            flow.bytesReceived += packet.totalLength
            if (flow.direction == Direction.OUTBOUND) {
                flow.direction = Direction.BIDIRECTIONAL
            }
        }
    }

    /**
     * Called when a DNS query/response is detected.
     * Updates the in-memory DNS cache for IP→domain reverse lookup.
     */
    fun onDns(dns: PacketParser.DnsResult, appUid: Int) {
        // Cache IP→domain mappings
        for (ip in dns.resolvedIps) {
            dnsCache[ip] = dns.queryDomain
        }

        // Update any active flows that match resolved IPs
        for (ip in dns.resolvedIps) {
            flowTable.values
                .filter { it.dstIp == ip && it.domain.isEmpty() }
                .forEach { it.domain = dns.queryDomain }
        }

        // Store DNS query asynchronously
        scope?.launch {
            try {
                val appInfo = appResolver.resolveApp(appUid)
                val appId = appInfo?.let { appDao.upsert(it.packageName, it.appName, it.isSystem) }

                dnsQueryDao.insert(
                    DnsQueryEntity(
                        appId = appId,
                        queryDomain = dns.queryDomain,
                        resolvedIp = dns.resolvedIps.firstOrNull(),
                        queryType = dns.queryType,
                        timestamp = System.currentTimeMillis()
                    )
                )

                // Upsert domain
                val category = DomainClassifier.classify(dns.queryDomain)
                domainDao.upsert(dns.queryDomain, category.name)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store DNS query", e)
            }
        }
    }

    /**
     * Check if a domain is blocked. Called from the VPN forwarding logic.
     */
    suspend fun isDomainBlocked(domain: String): Boolean {
        val entity = domainDao.getByName(domain)
        return entity?.isBlocked == true
    }

    /**
     * Check if a domain/IP should be blocked by checking DNS cache + domain table.
     */
    suspend fun isIpBlocked(ip: String): Boolean {
        val domain = dnsCache[ip] ?: return false
        return isDomainBlocked(domain)
    }

    /** Flush active flows to database in a single transaction */
    private suspend fun flushToDB() {
        if (flowTable.isEmpty()) return

        try {
            val now = System.currentTimeMillis()
            val toFlush = flowTable.values.toList()

            for (flow in toFlush) {
                // Resolve app ID if not done yet
                if (flow.appId == 0L) {
                    val appInfo = appResolver.resolveApp(flow.appUid)
                    if (appInfo != null) {
                        flow.appId = appDao.upsert(appInfo.packageName, appInfo.appName, appInfo.isSystem)
                    }
                }

                // Resolve domain ID
                if (flow.domainId == null && flow.domain.isNotEmpty()) {
                    val category = DomainClassifier.classify(flow.domain)
                    flow.domainId = domainDao.upsert(flow.domain, category.name)
                }
            }

            // Batch insert connections
            val connections = toFlush
                .filter { it.appId > 0 }
                .map { flow ->
                    ConnectionEntity(
                        appId = flow.appId,
                        domainId = flow.domainId,
                        timestampStart = flow.startTime,
                        timestampEnd = if (now - flow.lastSeen > FLOW_TIMEOUT_MS) flow.lastSeen else null,
                        srcIp = flow.srcIp,
                        srcPort = flow.srcPort,
                        dstIp = flow.dstIp,
                        dstPort = flow.dstPort,
                        protocol = protocolString(flow.protocol),
                        bytesSent = flow.bytesSent,
                        bytesReceived = flow.bytesReceived,
                        direction = flow.direction.name,
                        wasBlocked = flow.wasBlocked
                    )
                }

            if (connections.isNotEmpty()) {
                connectionDao.insertAll(connections)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush flows to DB", e)
        }
    }

    /** Remove flows that have been idle for too long */
    private fun evictStaleFlows() {
        val now = System.currentTimeMillis()
        val stale = flowTable.entries.filter { now - it.value.lastSeen > FLOW_TIMEOUT_MS }
        stale.forEach { flowTable.remove(it.key) }
    }

    /** Update the live flows StateFlow for UI consumption */
    private fun updateLiveFlows() {
        val flows = flowTable.values
            .sortedByDescending { it.lastSeen }
            .take(200)
            .map { flow ->
                val appInfo = appResolver.resolveAppSync(flow.appUid)
                val category = if (flow.domain.isNotEmpty()) {
                    DomainClassifier.classify(flow.domain)
                } else DomainCategory.UNKNOWN

                val riskLevel = when (category) {
                    DomainCategory.SUSPICIOUS -> RiskLevel.HIGH
                    DomainCategory.TRACKING, DomainCategory.ADS -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }

                val elapsed = maxOf(1L, (flow.lastSeen - flow.startTime) / 1000)
                val bps = (flow.bytesSent + flow.bytesReceived) / elapsed

                TrafficFlow(
                    id = flow.key,
                    appPackage = appInfo?.packageName ?: "android",
                    appName = appInfo?.appName ?: "System",
                    domain = flow.domain.ifEmpty { flow.dstIp },
                    ipAddress = flow.dstIp,
                    port = flow.dstPort,
                    protocol = when (flow.protocol) {
                        6 -> Protocol.TCP
                        17 -> if (flow.dstPort == DNS_PORT) Protocol.DNS else Protocol.UDP
                        else -> Protocol.UNKNOWN
                    },
                    direction = flow.direction,
                    bytesSent = flow.bytesSent,
                    bytesReceived = flow.bytesReceived,
                    bytesPerSecond = bps,
                    firstSeen = flow.startTime,
                    lastSeen = flow.lastSeen,
                    riskLevel = riskLevel,
                    category = category,
                    sparklineData = emptyList() // populated by UI layer over time
                )
            }
        _liveFlows.value = flows
    }

    private fun flowKey(packet: PacketParser.ParsedPacket, isOutbound: Boolean): String {
        return if (isOutbound) {
            "${packet.srcIp}:${packet.srcPort}-${packet.dstIp}:${packet.dstPort}-${packet.protocol}"
        } else {
            "${packet.dstIp}:${packet.dstPort}-${packet.srcIp}:${packet.srcPort}-${packet.protocol}"
        }
    }

    private fun protocolString(proto: Int): String = when (proto) {
        6 -> "TCP"
        17 -> "UDP"
        else -> "UNKNOWN"
    }
}
