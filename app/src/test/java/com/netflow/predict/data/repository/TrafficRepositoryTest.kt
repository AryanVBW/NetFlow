package com.netflow.predict.data.repository

import app.cash.turbine.test
import com.netflow.predict.data.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrafficRepositoryTest {

    private lateinit var repo: TrafficRepository

    @Before
    fun setUp() {
        repo = TrafficRepository()
    }

    // ── getTrafficSummary ─────────────────────────────────────────────────────

    @Test
    fun `getTrafficSummary emits non-null summary`() = runTest {
        repo.getTrafficSummary().test {
            val summary = awaitItem()
            assertNotNull(summary)
            assertEquals(25_500_000L, summary.totalSentBytes)
            assertEquals(196_800_000L, summary.totalReceivedBytes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTrafficSummary has 24 hourly data points`() = runTest {
        repo.getTrafficSummary().test {
            val summary = awaitItem()
            assertEquals(24, summary.hourlyDataPoints.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getApps ───────────────────────────────────────────────────────────────

    @Test
    fun `getApps emits 7 apps matching sample data`() = runTest {
        repo.getApps().test {
            val apps = awaitItem()
            assertEquals(7, apps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getApps includes expected app names`() = runTest {
        repo.getApps().test {
            val apps = awaitItem()
            val names = apps.map { it.appName }
            assertTrue("WhatsApp" in names)
            assertTrue("Chrome" in names)
            assertTrue("TikTok" in names)
            assertTrue("Maps" in names)
            assertTrue("System" in names)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getApps all apps are monitored by default`() = runTest {
        repo.getApps().test {
            val apps = awaitItem()
            assertTrue(apps.all { it.monitorStatus == AppMonitorStatus.MONITORED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getApps each app has 7-day weekly data`() = runTest {
        repo.getApps().test {
            val apps = awaitItem()
            assertTrue(apps.all { it.weeklyDataBytes.size == 7 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getAppDomains ─────────────────────────────────────────────────────────

    @Test
    fun `getAppDomains emits list of domains`() = runTest {
        repo.getAppDomains("com.whatsapp").test {
            val domains = awaitItem()
            assertTrue(domains.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAppDomains suspicious domains have high risk`() = runTest {
        repo.getAppDomains("com.whatsapp").test {
            val domains = awaitItem()
            val suspicious = domains.filter { it.category == DomainCategory.SUSPICIOUS }
            assertTrue(suspicious.all { it.riskLevel == RiskLevel.HIGH })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAppDomains tracking domains have medium risk`() = runTest {
        repo.getAppDomains("com.whatsapp").test {
            val domains = awaitItem()
            val tracking = domains.filter { it.category == DomainCategory.TRACKING }
            assertTrue(tracking.all { it.riskLevel == RiskLevel.MEDIUM })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getPrediction ─────────────────────────────────────────────────────────

    @Test
    fun `getPrediction emits result with medium device risk`() = runTest {
        repo.getPrediction().test {
            val prediction = awaitItem()
            assertEquals(RiskLevel.MEDIUM, prediction.deviceRiskLevel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPrediction has 3 apps to watch`() = runTest {
        repo.getPrediction().test {
            val prediction = awaitItem()
            assertEquals(3, prediction.appsToWatch.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPrediction has 2 domains to watch`() = runTest {
        repo.getPrediction().test {
            val prediction = awaitItem()
            assertEquals(2, prediction.domainsToWatch.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPrediction has 7-day weekly risk`() = runTest {
        repo.getPrediction().test {
            val prediction = awaitItem()
            assertEquals(7, prediction.weeklyRisk.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getAlerts ─────────────────────────────────────────────────────────────

    @Test
    fun `getAlerts emits 4 alerts`() = runTest {
        repo.getAlerts().test {
            val alerts = awaitItem()
            assertEquals(4, alerts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAlerts all alerts are unread by default`() = runTest {
        repo.getAlerts().test {
            val alerts = awaitItem()
            assertTrue(alerts.none { it.isRead })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAlerts contain expected alert types`() = runTest {
        repo.getAlerts().test {
            val alerts = awaitItem()
            val types = alerts.map { it.type }.toSet()
            assertTrue(AlertType.SUSPICIOUS_DOMAIN in types)
            assertTrue(AlertType.DATA_SPIKE in types)
            assertTrue(AlertType.NEW_APP in types)
            assertTrue(AlertType.RISK_LEVEL_CHANGED in types)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── liveTrafficFlow ───────────────────────────────────────────────────────

    @Test
    fun `liveTrafficFlow emits at least one batch`() = runTest {
        repo.liveTrafficFlow().test {
            val batch = awaitItem()
            assertTrue(batch.isNotEmpty())
            assertTrue(batch.size in 1..3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `liveTrafficFlow entries have valid fields`() = runTest {
        repo.liveTrafficFlow().test {
            val batch = awaitItem()
            batch.forEach { flow ->
                assertTrue(flow.id.startsWith("flow_"))
                assertTrue(flow.appName.isNotBlank())
                assertTrue(flow.domain.isNotBlank())
                assertTrue(flow.port > 0)
                assertTrue(flow.sparklineData.size == 5)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
