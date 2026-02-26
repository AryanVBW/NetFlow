package com.netflow.predict.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TcpProxySession — verifies session key generation,
 * TCP state machine transitions, and data tracking.
 */
class TcpProxySessionTest {

    @Test
    fun `sessionKey generates consistent format`() {
        val key = TcpProxySession.sessionKey("10.0.0.2", 12345, "93.184.216.34", 443)
        assertEquals("10.0.0.2:12345->93.184.216.34:443", key)
    }

    @Test
    fun `sessionKey is different for different source ports`() {
        val key1 = TcpProxySession.sessionKey("10.0.0.2", 12345, "93.184.216.34", 443)
        val key2 = TcpProxySession.sessionKey("10.0.0.2", 54321, "93.184.216.34", 443)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `sessionKey is different for different destinations`() {
        val key1 = TcpProxySession.sessionKey("10.0.0.2", 12345, "93.184.216.34", 443)
        val key2 = TcpProxySession.sessionKey("10.0.0.2", 12345, "172.217.14.100", 80)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `initial state is SYN_RECEIVED`() {
        val session = createTestSession()
        assertEquals(TcpProxySession.TcpState.SYN_RECEIVED, session.state)
    }

    @Test
    fun `initial bytes counters are zero`() {
        val session = createTestSession()
        assertEquals(0L, session.totalBytesSent)
        assertEquals(0L, session.totalBytesReceived)
    }

    @Test
    fun `new session is not active before SYN is processed`() {
        val session = createTestSession()
        // isActive only returns true for ESTABLISHED state
        assertFalse(session.isActive())
    }

    @Test
    fun `new session is not closed`() {
        val session = createTestSession()
        assertFalse(session.isClosed())
    }

    @Test
    fun `close transitions to CLOSED`() {
        val session = createTestSession()
        session.close()
        assertEquals(TcpProxySession.TcpState.CLOSED, session.state)
        assertTrue(session.isClosed())
    }

    @Test
    fun `onRst transitions to CLOSED`() {
        val session = createTestSession()
        session.onRst()
        assertTrue(session.isClosed())
    }

    @Test
    fun `key matches expected format`() {
        val session = createTestSession("10.0.0.2", 55555, "8.8.8.8", 443)
        assertEquals("10.0.0.2:55555->8.8.8.8:443", session.key)
    }

    @Test
    fun `lastActivity is set on creation`() {
        val before = System.currentTimeMillis()
        val session = createTestSession()
        val after = System.currentTimeMillis()

        assertTrue(session.lastActivity >= before)
        assertTrue(session.lastActivity <= after)
    }

    @Test
    fun `getChannel returns null before onSyn`() {
        val session = createTestSession()
        assertNull(session.getChannel())
    }

    /** Helper to create a test session without needing VpnService */
    private fun createTestSession(
        srcIp: String = "10.0.0.2",
        srcPort: Int = 12345,
        dstIp: String = "93.184.216.34",
        dstPort: Int = 443
    ): TcpProxySession {
        val key = TcpProxySession.sessionKey(srcIp, srcPort, dstIp, dstPort)
        // We create the session with a mock tunOutput; for state-only tests we
        // never need to actually write packets.
        return TcpProxySession(
            key = key,
            srcIp = srcIp, srcPort = srcPort,
            dstIp = dstIp, dstPort = dstPort,
            vpnService = FakeVpnService(),
            tunOutput = java.io.FileOutputStream(java.io.File.createTempFile("tun", "test"))
        )
    }

    /**
     * Minimal VpnService stub for unit tests. Since VpnService is abstract
     * and Android framework classes return default values, we just need it
     * to compile. The protect() call will be a no-op.
     */
    private class FakeVpnService : android.net.VpnService()
}
