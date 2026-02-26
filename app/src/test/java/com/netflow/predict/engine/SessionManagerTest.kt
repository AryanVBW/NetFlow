package com.netflow.predict.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SessionManager key generation and session limits.
 * Full integration tests require a VpnService, so these focus on
 * the session key format and data structures.
 */
class SessionManagerTest {

    @Test
    fun `TCP session key format matches TcpProxySession`() {
        val expected = TcpProxySession.sessionKey("10.0.0.2", 12345, "1.2.3.4", 443)
        assertEquals("10.0.0.2:12345->1.2.3.4:443", expected)
    }

    @Test
    fun `UDP session key format matches UdpProxySession`() {
        val expected = UdpProxySession.sessionKey("10.0.0.2", 12345, "8.8.8.8", 53)
        assertEquals("10.0.0.2:12345->8.8.8.8:53/udp", expected)
    }

    @Test
    fun `TCP and UDP session keys dont collide for same tuple`() {
        val tcp = TcpProxySession.sessionKey("10.0.0.2", 12345, "1.2.3.4", 443)
        val udp = UdpProxySession.sessionKey("10.0.0.2", 12345, "1.2.3.4", 443)
        assertNotEquals("TCP and UDP keys for same tuple should differ", tcp, udp)
    }

    @Test
    fun `TCP flags constants are correct`() {
        assertEquals(0x02, IpPacketBuilder.TCP_FLAG_SYN)
        assertEquals(0x10, IpPacketBuilder.TCP_FLAG_ACK)
        assertEquals(0x01, IpPacketBuilder.TCP_FLAG_FIN)
        assertEquals(0x04, IpPacketBuilder.TCP_FLAG_RST)
        assertEquals(0x08, IpPacketBuilder.TCP_FLAG_PSH)
    }

    @Test
    fun `SYN-ACK flags combine correctly`() {
        val synAck = IpPacketBuilder.TCP_FLAG_SYN or IpPacketBuilder.TCP_FLAG_ACK
        assertEquals(0x12, synAck)
    }

    @Test
    fun `FIN-ACK flags combine correctly`() {
        val finAck = IpPacketBuilder.TCP_FLAG_FIN or IpPacketBuilder.TCP_FLAG_ACK
        assertEquals(0x11, finAck)
    }
}
