package com.netflow.predict.engine

import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Central manager for all proxy sessions (TCP and UDP).
 *
 * Responsibilities:
 *   - Creates and caches TcpProxySession / UdpProxySession instances keyed by 5-tuple.
 *   - Enforces session limits (MAX_TCP / MAX_UDP) with LRU eviction.
 *   - Runs a periodic cleanup coroutine to evict idle/closed sessions.
 *   - Provides a poll loop that reads from all active TCP channels and writes
 *     response data back to the TUN.
 *   - Exposes aggregate stats (active sessions, bytes relayed).
 */
class SessionManager(
    private val vpnService: VpnService,
    private val tunOutput: FileOutputStream,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SessionManager"
        private const val MAX_TCP_SESSIONS = 512
        private const val MAX_UDP_SESSIONS = 256
        private const val TCP_IDLE_TIMEOUT_MS = 30_000L    // 30 seconds
        private const val UDP_IDLE_TIMEOUT_MS = 60_000L    // 60 seconds
        private const val CLEANUP_INTERVAL_MS = 5_000L     // 5 seconds
        private const val TCP_POLL_INTERVAL_MS = 2L        // ~500 polls/s per session
    }

    private val tcpSessions = ConcurrentHashMap<String, TcpProxySession>(MAX_TCP_SESSIONS)
    private val udpSessions = ConcurrentHashMap<String, UdpProxySession>(MAX_UDP_SESSIONS)

    private var cleanupJob: Job? = null
    private var tcpPollJob: Job? = null

    @Volatile
    private var running = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Start the session manager: cleanup and TCP polling coroutines.
     */
    fun start() {
        running = true

        cleanupJob = scope.launch {
            while (running && isActive) {
                delay(CLEANUP_INTERVAL_MS)
                evictStaleSessions()
            }
        }

        tcpPollJob = scope.launch(Dispatchers.IO) {
            while (running && isActive) {
                pollTcpSessions()
                delay(TCP_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop the session manager, close all sessions.
     */
    fun stop() {
        running = false
        cleanupJob?.cancel()
        tcpPollJob?.cancel()

        for (session in tcpSessions.values) {
            try { session.close() } catch (_: Exception) {}
        }
        tcpSessions.clear()

        for (session in udpSessions.values) {
            try { session.close() } catch (_: Exception) {}
        }
        udpSessions.clear()
    }

    // ── TCP session management ────────────────────────────────────────────────

    /**
     * Get or create a TCP proxy session for the given 5-tuple.
     * Returns null if eviction fails and we are at max capacity.
     */
    fun getOrCreateTcpSession(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int
    ): TcpProxySession {
        val key = TcpProxySession.sessionKey(srcIp, srcPort, dstIp, dstPort)

        return tcpSessions.getOrPut(key) {
            // Evict if at capacity
            if (tcpSessions.size >= MAX_TCP_SESSIONS) {
                evictOldestTcp()
            }

            TcpProxySession(
                key = key,
                srcIp = srcIp, srcPort = srcPort,
                dstIp = dstIp, dstPort = dstPort,
                vpnService = vpnService,
                tunOutput = tunOutput
            )
        }
    }

    /**
     * Retrieve an existing TCP session, if any.
     */
    fun getTcpSession(srcIp: String, srcPort: Int, dstIp: String, dstPort: Int): TcpProxySession? {
        val key = TcpProxySession.sessionKey(srcIp, srcPort, dstIp, dstPort)
        return tcpSessions[key]
    }

    /**
     * Remove and close a TCP session.
     */
    fun removeTcpSession(key: String) {
        tcpSessions.remove(key)?.close()
    }

    // ── UDP session management ────────────────────────────────────────────────

    /**
     * Get or create a UDP proxy session for the given 4-tuple.
     */
    fun getOrCreateUdpSession(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int
    ): UdpProxySession {
        val key = UdpProxySession.sessionKey(srcIp, srcPort, dstIp, dstPort)

        return udpSessions.getOrPut(key) {
            if (udpSessions.size >= MAX_UDP_SESSIONS) {
                evictOldestUdp()
            }

            UdpProxySession(
                key = key,
                srcIp = srcIp, srcPort = srcPort,
                dstIp = dstIp, dstPort = dstPort,
                vpnService = vpnService,
                tunOutput = tunOutput,
                scope = scope
            )
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    fun activeTcpSessionCount(): Int = tcpSessions.values.count { it.isActive() }
    fun activeUdpSessionCount(): Int = udpSessions.values.count { it.isActive() }
    fun totalSessionCount(): Int = tcpSessions.size + udpSessions.size

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Poll all active TCP sessions for incoming data from the remote server.
     * Data is read from the protected SocketChannel and written back to TUN.
     */
    private fun pollTcpSessions() {
        for (session in tcpSessions.values.toList()) {
            if (!session.isActive()) continue
            try {
                session.readFromRemote()
            } catch (e: Exception) {
                Log.d(TAG, "TCP poll error for ${session.key}: ${e.message}")
                tcpSessions.remove(session.key)
                session.close()
            }
        }
    }

    /**
     * Remove sessions that are idle or closed.
     */
    private fun evictStaleSessions() {
        val now = System.currentTimeMillis()

        // TCP cleanup
        val staleTcp = tcpSessions.entries.filter { (_, session) ->
            session.isClosed() || (now - session.lastActivity > TCP_IDLE_TIMEOUT_MS)
        }
        for ((key, session) in staleTcp) {
            tcpSessions.remove(key)
            session.close()
        }

        // UDP cleanup
        val staleUdp = udpSessions.entries.filter { (_, session) ->
            !session.isActive() || (now - session.lastActivity > UDP_IDLE_TIMEOUT_MS)
        }
        for ((key, session) in staleUdp) {
            udpSessions.remove(key)
            session.close()
        }

        if (staleTcp.isNotEmpty() || staleUdp.isNotEmpty()) {
            Log.d(TAG, "Evicted ${staleTcp.size} TCP + ${staleUdp.size} UDP sessions. " +
                    "Active: ${tcpSessions.size} TCP, ${udpSessions.size} UDP")
        }
    }

    private fun evictOldestTcp() {
        val oldest = tcpSessions.entries.minByOrNull { it.value.lastActivity }
        oldest?.let {
            it.value.close()
            tcpSessions.remove(it.key)
        }
    }

    private fun evictOldestUdp() {
        val oldest = udpSessions.entries.minByOrNull { it.value.lastActivity }
        oldest?.let {
            it.value.close()
            udpSessions.remove(it.key)
        }
    }
}
