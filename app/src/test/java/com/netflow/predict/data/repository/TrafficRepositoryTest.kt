package com.netflow.predict.data.repository

import com.netflow.predict.data.local.dao.*
import com.netflow.predict.data.local.entity.*
import com.netflow.predict.data.model.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrafficRepositoryTest {

    private lateinit var repo: TrafficRepository
    private lateinit var connectionDao: ConnectionDao
    private lateinit var appDao: AppDao
    private lateinit var domainDao: DomainDao
    private lateinit var alertDao: AlertDao
    private lateinit var predictionDao: PredictionSnapshotDao
    private lateinit var dnsQueryDao: DnsQueryDao

    @Before
    fun setUp() {
        connectionDao = mockk(relaxed = true)
        appDao = mockk(relaxed = true)
        domainDao = mockk(relaxed = true)
        alertDao = mockk(relaxed = true)
        predictionDao = mockk(relaxed = true)
        dnsQueryDao = mockk(relaxed = true)

        // ── Traffic summary stubs ─────────────────────────────────────────
        every { connectionDao.observeTotalSentSince(any()) } returns flowOf(25_500_000L)
        every { connectionDao.observeTotalReceivedSince(any()) } returns flowOf(196_800_000L)
        coEvery { connectionDao.getHourlyBreakdown(any()) } returns (0L until 24L).map {
            HourlyBucket(hourSlot = it, totalBytes = 1_000_000L)
        }

        // ── Apps stubs ────────────────────────────────────────────────────
        val sampleApps = listOf(
            appWithStats("com.whatsapp", "WhatsApp", riskLevel = "MEDIUM"),
            appWithStats("com.android.chrome", "Chrome"),
            appWithStats("com.zhiliaoapp.musically", "TikTok", riskLevel = "HIGH"),
            appWithStats("com.google.android.apps.maps", "Maps"),
            appWithStats("android", "System", isSystem = true),
            appWithStats("com.instagram.android", "Instagram"),
            appWithStats("com.spotify.music", "Spotify")
        )
        every { connectionDao.observeAppsWithStats(any()) } returns flowOf(sampleApps)
        coEvery { appDao.getByPackage(any()) } returns null
        coEvery { connectionDao.getDailyBytesForApp(any(), any()) } returns emptyList()

        // ── Domains stubs ─────────────────────────────────────────────────
        every { domainDao.observeDomainsForApp("com.whatsapp") } returns flowOf(
            listOf(
                domainWithStats("web.whatsapp.com", "TRUSTED"),
                domainWithStats("tracker.example.com", "TRACKING"),
                domainWithStats("shady.example.net", "SUSPICIOUS")
            )
        )

        // ── Prediction stubs ──────────────────────────────────────────────
        every { predictionDao.observeLatest() } returns flowOf(
            PredictionSnapshotEntity(
                id = 1,
                deviceRiskLevel = "MEDIUM",
                summary = "Moderate risk detected",
                appsAtRiskJson = """[
                    {"packageName":"com.whatsapp","appName":"WhatsApp","riskLevel":"MEDIUM","reason":"Tracker detected"},
                    {"packageName":"com.zhiliaoapp.musically","appName":"TikTok","riskLevel":"HIGH","reason":"Suspicious domain"},
                    {"packageName":"com.instagram.android","appName":"Instagram","riskLevel":"MEDIUM","reason":"Data spike"}
                ]""",
                domainsAtRiskJson = """[
                    {"domain":"tracker.example.com","riskLevel":"MEDIUM","reason":"Tracking domain","appCount":3},
                    {"domain":"shady.example.net","riskLevel":"HIGH","reason":"Suspicious activity","appCount":1}
                ]"""
            )
        )

        // ── Alerts stubs ──────────────────────────────────────────────────
        every { alertDao.observeAll() } returns flowOf(
            listOf(
                AlertEntity(id = 1, type = "SUSPICIOUS_DOMAIN", title = "Suspicious domain", description = "desc1", severity = "HIGH"),
                AlertEntity(id = 2, type = "DATA_SPIKE", title = "Data spike", description = "desc2", severity = "MEDIUM"),
                AlertEntity(id = 3, type = "NEW_APP", title = "New app", description = "desc3", severity = "LOW"),
                AlertEntity(id = 4, type = "RISK_LEVEL_CHANGED", title = "Risk changed", description = "desc4", severity = "MEDIUM")
            )
        )
        coEvery { appDao.getById(any()) } returns null
        coEvery { domainDao.getById(any()) } returns null

        repo = TrafficRepository(connectionDao, appDao, domainDao, alertDao, predictionDao, dnsQueryDao)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun appWithStats(
        pkg: String,
        name: String,
        isSystem: Boolean = false,
        riskLevel: String = "LOW"
    ) = AppWithStats(
        packageName = pkg,
        appName = name,
        isSystemApp = isSystem,
        monitorStatus = "MONITORED",
        riskLevel = riskLevel,
        blockBackground = false,
        blockTrackers = false,
        alwaysAllowOnWifi = true,
        totalBytesSent = 1_000_000L,
        totalBytesReceived = 5_000_000L,
        connectionCount = 42,
        domainCount = 5
    )

    private fun domainWithStats(name: String, category: String) = DomainWithStats(
        domainId = name.hashCode().toLong(),
        name = name,
        category = category,
        reputationScore = 0.5f,
        isBlocked = false,
        isTrusted = category == "TRUSTED",
        firstSeen = System.currentTimeMillis() - 86_400_000L,
        lastSeen = System.currentTimeMillis(),
        totalBytesSent = 500_000L,
        totalBytesReceived = 2_000_000L,
        requestCount = 20,
        appCount = 1
    )

    // ── getTrafficSummary ─────────────────────────────────────────────────────

    @Test
    fun `getTrafficSummary emits non-null summary`() = runTest {
        val summary = repo.getTrafficSummary().first()
        assertNotNull(summary)
        assertEquals(25_500_000L, summary.totalSentBytes)
        assertEquals(196_800_000L, summary.totalReceivedBytes)
    }

    @Test
    fun `getTrafficSummary has 24 hourly data points`() = runTest {
        val summary = repo.getTrafficSummary().first()
        assertEquals(24, summary.hourlyDataPoints.size)
    }

    // ── getApps ───────────────────────────────────────────────────────────────

    @Test
    fun `getApps emits 7 apps matching sample data`() = runTest {
        val apps = repo.getApps().first()
        assertEquals(7, apps.size)
    }

    @Test
    fun `getApps includes expected app names`() = runTest {
        val apps = repo.getApps().first()
        val names = apps.map { it.appName }
        assertTrue("WhatsApp" in names)
        assertTrue("Chrome" in names)
        assertTrue("TikTok" in names)
        assertTrue("Maps" in names)
        assertTrue("System" in names)
    }

    @Test
    fun `getApps all apps are monitored by default`() = runTest {
        val apps = repo.getApps().first()
        assertTrue(apps.all { it.monitorStatus == AppMonitorStatus.MONITORED })
    }

    @Test
    fun `getApps each app has 7-day weekly data`() = runTest {
        val apps = repo.getApps().first()
        assertTrue(apps.all { it.weeklyDataBytes.size == 7 })
    }

    // ── getAppDomains ─────────────────────────────────────────────────────────

    @Test
    fun `getAppDomains emits list of domains`() = runTest {
        val domains = repo.getAppDomains("com.whatsapp").first()
        assertTrue(domains.isNotEmpty())
    }

    @Test
    fun `getAppDomains suspicious domains have high risk`() = runTest {
        val domains = repo.getAppDomains("com.whatsapp").first()
        val suspicious = domains.filter { it.category == DomainCategory.SUSPICIOUS }
        assertTrue(suspicious.isNotEmpty())
        assertTrue(suspicious.all { it.riskLevel == RiskLevel.HIGH })
    }

    @Test
    fun `getAppDomains tracking domains have medium risk`() = runTest {
        val domains = repo.getAppDomains("com.whatsapp").first()
        val tracking = domains.filter { it.category == DomainCategory.TRACKING }
        assertTrue(tracking.isNotEmpty())
        assertTrue(tracking.all { it.riskLevel == RiskLevel.MEDIUM })
    }

    // ── getPrediction ─────────────────────────────────────────────────────────

    @Test
    fun `getPrediction emits result with medium device risk`() = runTest {
        val prediction = repo.getPrediction().first()
        assertEquals(RiskLevel.MEDIUM, prediction.deviceRiskLevel)
    }

    @Test
    fun `getPrediction has 3 apps to watch`() = runTest {
        val prediction = repo.getPrediction().first()
        assertEquals(3, prediction.appsToWatch.size)
    }

    @Test
    fun `getPrediction has 2 domains to watch`() = runTest {
        val prediction = repo.getPrediction().first()
        assertEquals(2, prediction.domainsToWatch.size)
    }

    @Test
    fun `getPrediction has 7-day weekly risk`() = runTest {
        val prediction = repo.getPrediction().first()
        assertEquals(7, prediction.weeklyRisk.size)
    }

    // ── getAlerts ─────────────────────────────────────────────────────────────

    @Test
    fun `getAlerts emits 4 alerts`() = runTest {
        val alerts = repo.getAlerts().first()
        assertEquals(4, alerts.size)
    }

    @Test
    fun `getAlerts all alerts are unread by default`() = runTest {
        val alerts = repo.getAlerts().first()
        assertTrue(alerts.none { it.isRead })
    }

    @Test
    fun `getAlerts contain expected alert types`() = runTest {
        val alerts = repo.getAlerts().first()
        val types = alerts.map { it.type }.toSet()
        assertTrue(AlertType.SUSPICIOUS_DOMAIN in types)
        assertTrue(AlertType.DATA_SPIKE in types)
        assertTrue(AlertType.NEW_APP in types)
        assertTrue(AlertType.RISK_LEVEL_CHANGED in types)
    }
}
