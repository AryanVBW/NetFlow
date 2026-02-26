package com.netflow.predict.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for UdpProxySession — verifies session key format.
 */
class UdpProxySessionTest {

    @Test
    fun `sessionKey generates correct format with udp suffix`() {
        val key = UdpProxySession.sessionKey("10.0.0.2", 12345, "8.8.8.8", 53)
        assertEquals("10.0.0.2:12345->8.8.8.8:53/udp", key)
    }

    @Test
    fun `sessionKey is different for different source ports`() {
        val key1 = UdpProxySession.sessionKey("10.0.0.2", 1111, "8.8.8.8", 53)
        val key2 = UdpProxySession.sessionKey("10.0.0.2", 2222, "8.8.8.8", 53)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `sessionKey is different for different destinations`() {
        val key1 = UdpProxySession.sessionKey("10.0.0.2", 1111, "8.8.8.8", 53)
        val key2 = UdpProxySession.sessionKey("10.0.0.2", 1111, "1.1.1.1", 53)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `sessionKey includes port in destination`() {
        val key = UdpProxySession.sessionKey("10.0.0.2", 5000, "1.2.3.4", 443)
        assertTrue(key.contains("443"))
        assertTrue(key.endsWith("/udp"))
    }
}
