package com.netflow.predict.ui.viewmodel

import app.cash.turbine.test
import com.netflow.predict.data.model.*
import com.netflow.predict.data.repository.TrafficRepository
import com.netflow.predict.data.repository.VpnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var vpnRepo: VpnRepository
    private lateinit var trafficRepo: TrafficRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vpnRepo = VpnRepository()
        trafficRepo = TrafficRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial vpn state is disconnected`() = runTest {
        val vm = HomeViewModel(vpnRepo, trafficRepo)
        assertEquals(VpnStatus.DISCONNECTED, vm.vpnState.value.status)
    }

    @Test
    fun `startVpn updates vpn state to connected`() = runTest {
        val vm = HomeViewModel(vpnRepo, trafficRepo)
        vm.startVpn()
        assertEquals(VpnStatus.CONNECTED, vm.vpnState.value.status)
    }

    @Test
    fun `stopVpn updates vpn state to disconnected`() = runTest {
        val vm = HomeViewModel(vpnRepo, trafficRepo)
        vm.startVpn()
        vm.stopVpn()
        assertEquals(VpnStatus.DISCONNECTED, vm.vpnState.value.status)
    }

    @Test
    fun `isLoading is initially true`() = runTest {
        val vm = HomeViewModel(vpnRepo, trafficRepo)
        // isLoading starts true before the delay completes
        assertTrue(vm.isLoading.value)
    }

    @Test
    fun `isLoading becomes false after delay`() = runTest {
        val vm = HomeViewModel(vpnRepo, trafficRepo)
        advanceTimeBy(1500)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `trafficSummary is populated after delay`() = runTest {
        val vm = HomeViewModel(vpnRepo, trafficRepo)
        advanceTimeBy(1500)
        assertNotNull(vm.trafficSummary.value)
    }

    @Test
    fun `prediction is populated after delay`() = runTest {
        val vm = HomeViewModel(vpnRepo, trafficRepo)
        advanceTimeBy(2000)
        assertNotNull(vm.prediction.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var trafficRepo: TrafficRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trafficRepo = TrafficRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `apps are loaded on init`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        assertTrue(vm.apps.value.isNotEmpty())
    }

    @Test
    fun `isLoading becomes false after apps load`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `setSearch filters apps by name`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        vm.setSearch("Chrome")
        // Allow the combine flow to process
        advanceUntilIdle()
        val filtered = vm.filteredApps.value
        assertTrue(filtered.all { it.appName.contains("Chrome", ignoreCase = true) })
    }

    @Test
    fun `setSearch with empty string returns all apps`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        vm.setSearch("")
        advanceUntilIdle()
        assertEquals(vm.apps.value.size, vm.filteredApps.value.size)
    }

    @Test
    fun `setSort changes sort mode`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        vm.setSort(AppSortMode.HIGHEST_RISK)
        assertEquals(AppSortMode.HIGHEST_RISK, vm.sortMode.value)
    }

    @Test
    fun `filteredApps sorted by MOST_DATA are in descending order`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        vm.setSort(AppSortMode.MOST_DATA)
        advanceUntilIdle()
        val filtered = vm.filteredApps.value
        for (i in 0 until filtered.size - 1) {
            val current = filtered[i].dataSentToday + filtered[i].dataReceivedToday
            val next = filtered[i + 1].dataSentToday + filtered[i + 1].dataReceivedToday
            assertTrue("Expected descending order by data", current >= next)
        }
    }

    @Test
    fun `filteredApps sorted by MOST_REQUESTS are in descending order`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        vm.setSort(AppSortMode.MOST_REQUESTS)
        advanceUntilIdle()
        val filtered = vm.filteredApps.value
        for (i in 0 until filtered.size - 1) {
            assertTrue(
                "Expected descending order by requests",
                filtered[i].requestCountToday >= filtered[i + 1].requestCountToday
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PredictionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var trafficRepo: TrafficRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trafficRepo = TrafficRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `prediction is loaded`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        advanceUntilIdle()
        assertNotNull(vm.prediction.value)
    }

    @Test
    fun `alerts are loaded`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        advanceUntilIdle()
        assertTrue(vm.alerts.value.isNotEmpty())
    }

    @Test
    fun `visibleAlerts initially equals all alerts`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        advanceUntilIdle()
        assertEquals(vm.alerts.value.size, vm.visibleAlerts.value.size)
    }

    @Test
    fun `dismissAlert removes alert from visible list`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        advanceUntilIdle()
        val firstAlertId = vm.alerts.value.first().id
        val countBefore = vm.visibleAlerts.value.size
        vm.dismissAlert(firstAlertId)
        advanceUntilIdle()
        assertEquals(countBefore - 1, vm.visibleAlerts.value.size)
        assertTrue(vm.visibleAlerts.value.none { it.id == firstAlertId })
    }

    @Test
    fun `dismissing same alert twice has no additional effect`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        advanceUntilIdle()
        val firstAlertId = vm.alerts.value.first().id
        vm.dismissAlert(firstAlertId)
        advanceUntilIdle()
        val countAfterFirst = vm.visibleAlerts.value.size
        vm.dismissAlert(firstAlertId)
        advanceUntilIdle()
        assertEquals(countAfterFirst, vm.visibleAlerts.value.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTrafficViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var trafficRepo: TrafficRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trafficRepo = TrafficRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial filter is ALL`() = runTest {
        val vm = LiveTrafficViewModel(trafficRepo)
        assertEquals(TrafficFilter.ALL, vm.activeFilter.value)
    }

    @Test
    fun `isCapturing is initially true`() = runTest {
        val vm = LiveTrafficViewModel(trafficRepo)
        assertTrue(vm.isCapturing.value)
    }

    @Test
    fun `toggleCapture flips capturing state`() = runTest {
        val vm = LiveTrafficViewModel(trafficRepo)
        vm.toggleCapture()
        assertFalse(vm.isCapturing.value)
        vm.toggleCapture()
        assertTrue(vm.isCapturing.value)
    }

    @Test
    fun `setFilter updates active filter`() = runTest {
        val vm = LiveTrafficViewModel(trafficRepo)
        vm.setFilter(TrafficFilter.SUSPICIOUS)
        assertEquals(TrafficFilter.SUSPICIOUS, vm.activeFilter.value)
    }

    @Test
    fun `flows are populated after emission`() = runTest {
        val vm = LiveTrafficViewModel(trafficRepo)
        advanceTimeBy(2000) // let the first emission happen at 1500ms
        assertTrue(vm.flows.value.isNotEmpty())
    }
}
