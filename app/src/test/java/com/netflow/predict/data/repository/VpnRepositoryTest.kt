package com.netflow.predict.data.repository

import com.netflow.predict.data.model.VpnStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VpnRepositoryTest {

    private lateinit var repo: VpnRepository

    @Before
    fun setUp() {
        repo = VpnRepository()
    }

    @Test
    fun `initial state is disconnected`() {
        val state = repo.vpnState.value
        assertEquals(VpnStatus.DISCONNECTED, state.status)
        assertEquals(0L, state.uptimeSeconds)
    }

    @Test
    fun `startVpn sets status to connected`() {
        repo.startVpn()
        assertEquals(VpnStatus.CONNECTED, repo.vpnState.value.status)
    }

    @Test
    fun `stopVpn sets status to disconnected`() {
        repo.startVpn()
        repo.stopVpn()
        assertEquals(VpnStatus.DISCONNECTED, repo.vpnState.value.status)
    }

    @Test
    fun `stopVpn resets uptime to zero`() {
        repo.startVpn()
        repo.stopVpn()
        assertEquals(0L, repo.vpnState.value.uptimeSeconds)
    }

    @Test
    fun `startVpn after stop transitions back to connected`() {
        repo.startVpn()
        repo.stopVpn()
        repo.startVpn()
        assertEquals(VpnStatus.CONNECTED, repo.vpnState.value.status)
    }
}
