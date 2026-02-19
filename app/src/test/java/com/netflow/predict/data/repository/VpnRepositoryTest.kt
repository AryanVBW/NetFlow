package com.netflow.predict.data.repository

import android.content.Context
import com.netflow.predict.data.model.VpnStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VpnRepositoryTest {

    private lateinit var repo: VpnRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.startForegroundService(any()) } returns null
        every { context.startService(any()) } returns null

        repo = VpnRepository(context)
    }

    @Test
    fun `initial state is disconnected`() {
        val state = repo.vpnState.value
        assertEquals(VpnStatus.DISCONNECTED, state.status)
        assertEquals(0L, state.uptimeSeconds)
    }

    @Test
    fun `startVpn sets status to reconnecting`() {
        repo.startVpn()
        // startVpn sets RECONNECTING; the init poller will set CONNECTED once isRunning is true
        assertEquals(VpnStatus.RECONNECTING, repo.vpnState.value.status)
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
    fun `startVpn after stop transitions back to reconnecting`() {
        repo.startVpn()
        repo.stopVpn()
        repo.startVpn()
        assertEquals(VpnStatus.RECONNECTING, repo.vpnState.value.status)
    }
}
