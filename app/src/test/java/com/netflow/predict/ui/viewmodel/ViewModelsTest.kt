package com.netflow.predict.ui.viewmodel

import com.netflow.predict.data.model.*
import com.netflow.predict.data.repository.TrafficRepository
import com.netflow.predict.data.repository.VpnRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

// ── Shared test data ──────────────────────────────────────────────────────────

private fun sampleTrafficSummary() = TrafficSummary(
    totalSentBytes = 25_500_000L,
    totalReceivedBytes = 196_800_000L,
    hourlyDataPoints = List(24) { 1_000_000L }
)

private fun sampleApps() = listOf(
    appInfo("com.whatsapp", "WhatsApp", RiskLevel.MEDIUM, 5_000_000L, 20_000_000L, 120),
    appInfo("com.android.chrome", "Chrome", RiskLevel.LOW, 8_000_000L, 50_000_000L, 300),
    appInfo("com.zhiliaoapp.musically", "TikTok", RiskLevel.HIGH, 3_000_000L, 80_000_000L, 200),
    appInfo("com.google.android.apps.maps", "Maps", RiskLevel.LOW, 1_000_000L, 10_000_000L, 50),
    appInfo("android", "System", RiskLevel.LOW, 500_000L, 2_000_000L, 30),
    appInfo("com.instagram.android", "Instagram", RiskLevel.MEDIUM, 4_000_000L, 30_000_000L, 150),
    appInfo("com.spotify.music", "Spotify", RiskLevel.LOW, 2_000_000L, 40_000_000L, 80)
)

private fun appInfo(
    pkg: String, name: String, risk: RiskLevel,
    sent: Long, received: Long, requests: Int
) = AppNetworkInfo(
    packageName = pkg, appName = name,
    dataSentToday = sent, dataReceivedToday = received,
    requestCountToday = requests, riskLevel = risk,
    monitorStatus = AppMonitorStatus.MONITORED,
    domainCount = 5, weeklyDataBytes = List(7) { 1_000_000L }
)

private fun samplePrediction() = PredictionResult(
    deviceRiskLevel = RiskLevel.MEDIUM,
    summary = "Moderate risk detected",
    appsToWatch = listOf(
        AppRiskEntry("com.whatsapp", "WhatsApp", RiskLevel.MEDIUM, "Tracker detected"),
        AppRiskEntry("com.zhiliaoapp.musically", "TikTok", RiskLevel.HIGH, "Suspicious domain"),
        AppRiskEntry("com.instagram.android", "Instagram", RiskLevel.MEDIUM, "Data spike")
    ),
    domainsToWatch = listOf(
        DomainRiskEntry("tracker.example.com", RiskLevel.MEDIUM, "Tracking domain", 3),
        DomainRiskEntry("shady.example.net", RiskLevel.HIGH, "Suspicious activity", 1)
    ),
    weeklyRisk = List(7) { RiskLevel.MEDIUM }
)

private fun sampleAlerts() = listOf(
    NetworkAlert("1", AlertType.SUSPICIOUS_DOMAIN, "Suspicious domain", "desc1", System.currentTimeMillis()),
    NetworkAlert("2", AlertType.DATA_SPIKE, "Data spike", "desc2", System.currentTimeMillis()),
    NetworkAlert("3", AlertType.NEW_APP, "New app", "desc3", System.currentTimeMillis()),
    NetworkAlert("4", AlertType.RISK_LEVEL_CHANGED, "Risk changed", "desc4", System.currentTimeMillis())
)

private fun sampleTrafficFlows() = listOf(
    TrafficFlow(
        id = "flow_1", appPackage = "com.whatsapp", appName = "WhatsApp",
        domain = "web.whatsapp.com", ipAddress = "1.2.3.4", port = 443,
        protocol = Protocol.TCP, direction = Direction.OUTBOUND,
        bytesSent = 1000L, bytesReceived = 5000L, bytesPerSecond = 200L,
        firstSeen = System.currentTimeMillis() - 10_000L,
        lastSeen = System.currentTimeMillis(),
        riskLevel = RiskLevel.LOW, category = DomainCategory.TRUSTED,
        sparklineData = List(5) { 200L }
    )
)

// ── Mock factory helpers ──────────────────────────────────────────────────────

private fun mockVpnRepo(): VpnRepository {
    val repo = mockk<VpnRepository>(relaxed = true)
    val state = MutableStateFlow(VpnState(VpnStatus.DISCONNECTED, 0L))
    every { repo.vpnState } returns state
    every { repo.startVpn() } answers { state.value = VpnState(VpnStatus.CONNECTED, 0L) }
    every { repo.stopVpn() } answers { state.value = VpnState(VpnStatus.DISCONNECTED, 0L) }
    return repo
}

private fun mockTrafficRepo(): TrafficRepository {
    val repo = mockk<TrafficRepository>(relaxed = true)
    every { repo.getTrafficSummary() } returns flow {
        emit(sampleTrafficSummary())
        // Keep the flow alive so collectors don't complete
        kotlinx.coroutines.awaitCancellation()
    }
    every { repo.getApps() } returns flowOf(sampleApps())
    every { repo.getPrediction() } returns flowOf(samplePrediction())
    every { repo.getAlerts() } returns flowOf(sampleAlerts())
    every { repo.liveTrafficFlow() } returns flow {
        kotlinx.coroutines.delay(1500)
        emit(sampleTrafficFlows())
        kotlinx.coroutines.awaitCancellation()
    }
    return repo
}

// ── HomeViewModel Tests ───────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var vpnRepo: VpnRepository
    private lateinit var trafficRepo: TrafficRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vpnRepo = mockVpnRepo()
        trafficRepo = mockTrafficRepo()
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
    fun `trafficSummary is populated`() = runTest {
        val vm = HomeViewModel(vpnRepo, trafficRepo)
        advanceUntilIdle()
        assertNotNull(vm.trafficSummary.value)
    }

    @Test
    fun `prediction is populated`() = runTest {
        val vm = HomeViewModel(vpnRepo, trafficRepo)
        advanceUntilIdle()
        assertNotNull(vm.prediction.value)
    }
}

// ── AppsViewModel Tests ───────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var trafficRepo: TrafficRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trafficRepo = mockTrafficRepo()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `apps are loaded on init`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        advanceUntilIdle()
        assertTrue(vm.apps.value.isNotEmpty())
    }

    @Test
    fun `isLoading becomes false after apps load`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        advanceUntilIdle()
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `setSearch filters apps by name`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        advanceUntilIdle()
        vm.setSearch("Chrome")
        advanceUntilIdle()
        val filtered = vm.filteredApps.value
        assertTrue(filtered.all { it.appName.contains("Chrome", ignoreCase = true) })
    }

    @Test
    fun `setSearch with empty string returns all apps`() = runTest {
        val vm = AppsViewModel(trafficRepo)
        advanceUntilIdle()
        // Trigger lazy stateIn by collecting filteredApps
        val job = vm.filteredApps.onEach {}.launchIn(this)
        vm.setSearch("")
        advanceUntilIdle()
        assertEquals(vm.apps.value.size, vm.filteredApps.value.size)
        job.cancel()
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
        advanceUntilIdle()
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
        advanceUntilIdle()
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

// ── PredictionsViewModel Tests ────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class PredictionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var trafficRepo: TrafficRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trafficRepo = mockTrafficRepo()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `prediction is loaded`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        val job = vm.prediction.onEach {}.launchIn(this)
        advanceUntilIdle()
        assertNotNull(vm.prediction.value)
        job.cancel()
    }

    @Test
    fun `alerts are loaded`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        val job = vm.alerts.onEach {}.launchIn(this)
        advanceUntilIdle()
        assertTrue(vm.alerts.value.isNotEmpty())
        job.cancel()
    }

    @Test
    fun `visibleAlerts initially equals all alerts`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        val job1 = vm.alerts.onEach {}.launchIn(this)
        val job2 = vm.visibleAlerts.onEach {}.launchIn(this)
        advanceUntilIdle()
        assertEquals(vm.alerts.value.size, vm.visibleAlerts.value.size)
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun `dismissAlert removes alert from visible list`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        val job1 = vm.alerts.onEach {}.launchIn(this)
        val job2 = vm.visibleAlerts.onEach {}.launchIn(this)
        advanceUntilIdle()
        val firstAlertId = vm.alerts.value.first().id
        val countBefore = vm.visibleAlerts.value.size
        vm.dismissAlert(firstAlertId)
        advanceUntilIdle()
        assertEquals(countBefore - 1, vm.visibleAlerts.value.size)
        assertTrue(vm.visibleAlerts.value.none { it.id == firstAlertId })
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun `dismissing same alert twice has no additional effect`() = runTest {
        val vm = PredictionsViewModel(trafficRepo)
        val job1 = vm.alerts.onEach {}.launchIn(this)
        val job2 = vm.visibleAlerts.onEach {}.launchIn(this)
        advanceUntilIdle()
        val firstAlertId = vm.alerts.value.first().id
        vm.dismissAlert(firstAlertId)
        advanceUntilIdle()
        val countAfterFirst = vm.visibleAlerts.value.size
        vm.dismissAlert(firstAlertId)
        advanceUntilIdle()
        assertEquals(countAfterFirst, vm.visibleAlerts.value.size)
        job1.cancel()
        job2.cancel()
    }
}

// ── LiveTrafficViewModel Tests ────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTrafficViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var trafficRepo: TrafficRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trafficRepo = mockTrafficRepo()
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
        advanceTimeBy(2000)
        advanceUntilIdle()
        assertTrue(vm.flows.value.isNotEmpty())
    }
}
