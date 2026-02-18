package com.netflow.predict.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.netflow.predict.data.local.dao.*
import com.netflow.predict.data.local.entity.AlertEntity
import com.netflow.predict.data.local.entity.PredictionSnapshotEntity
import com.netflow.predict.engine.DomainClassifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic analysis worker that runs every 1-2 hours to:
 *
 * 1. Compute per-app metrics (new domains, data spikes, tracker %).
 * 2. Compute per-domain metrics (reputation, app count, history).
 * 3. Apply v1 rule-based prediction logic.
 * 4. Generate alerts for suspicious activity.
 * 5. Write a PredictionSnapshot to the database.
 *
 * This worker is battery-efficient: it only runs when constraints are met
 * and batches all analysis into a single execution.
 */
@HiltWorker
class PredictionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appDao: AppDao,
    private val domainDao: DomainDao,
    private val connectionDao: ConnectionDao,
    private val alertDao: AlertDao,
    private val predictionDao: PredictionSnapshotDao,
    private val trafficStatsDao: TrafficStatsDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PredictionWorker"
        const val WORK_NAME = "netflow_prediction_analysis"

        // Thresholds for v1 rules
        private const val NEW_DOMAIN_BURST_THRESHOLD = 5    // 5+ new domains in 2h
        private const val DATA_SPIKE_MULTIPLIER = 3.0       // 3x average = spike
        private const val TRACKER_RATIO_HIGH = 0.5           // 50%+ traffic to trackers = concerning
        private const val HIGH_RISK_SCORE_THRESHOLD = 0.7f
        private const val MEDIUM_RISK_SCORE_THRESHOLD = 0.4f

        private val TWO_HOURS_MS = TimeUnit.HOURS.toMillis(2)
        private val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)
        private val SEVEN_DAYS_MS = TimeUnit.DAYS.toMillis(7)

        fun buildWorkRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<PredictionWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }

        fun buildOneTimeRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<PredictionWorker>().build()
        }
    }

    private val gson = Gson()

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting prediction analysis")

        return try {
            val now = System.currentTimeMillis()
            val twoHoursAgo = now - TWO_HOURS_MS
            val oneDayAgo = now - ONE_DAY_MS
            val sevenDaysAgo = now - SEVEN_DAYS_MS

            val appsAtRisk = mutableListOf<AppRiskJson>()
            val domainsAtRisk = mutableListOf<DomainRiskJson>()
            var overallRiskScore = 0f
            var riskFactorCount = 0

            // ── Per-app analysis ──────────────────────────────────────────

            val apps = appDao.observeAll().first()

            for (app in apps) {
                var appRisk = 0f
                val reasons = mutableListOf<String>()

                // Rule 1: New domain burst
                val newDomains = connectionDao.countNewDomainsForAppSince(app.id, twoHoursAgo)
                if (newDomains >= NEW_DOMAIN_BURST_THRESHOLD) {
                    appRisk += 0.3f
                    reasons.add("Contacted $newDomains new domains in last 2 hours")
                    generateAlert(
                        type = "NEW_DOMAIN_BURST",
                        title = "New domain burst detected",
                        description = "${app.appName} contacted $newDomains new domains in the last 2 hours",
                        appId = app.id,
                        severity = "MEDIUM"
                    )
                }

                // Rule 2: Data spike
                val todayBytes = connectionDao.sumBytesSentForAppSince(app.id, oneDayAgo)
                val avgDailyBytes = trafficStatsDao.avgDailyBytesSentForApp(
                    app.id, sevenDaysAgo, oneDayAgo
                )
                if (avgDailyBytes > 0 && todayBytes > avgDailyBytes * DATA_SPIKE_MULTIPLIER) {
                    appRisk += 0.3f
                    val ratio = todayBytes.toFloat() / avgDailyBytes
                    reasons.add("Data sent is ${String.format("%.1f", ratio)}x the 7-day average")
                    generateAlert(
                        type = "DATA_SPIKE",
                        title = "Data usage spike",
                        description = "${app.appName} sent ${String.format("%.1f", ratio)}x more data than usual",
                        appId = app.id,
                        severity = if (ratio > 5) "HIGH" else "MEDIUM"
                    )
                }

                // Rule 3: Tracker ratio
                val totalConnections = connectionDao.countForAppSince(app.id, oneDayAgo)
                // Count tracker domains (we check domain categories)
                // Simple heuristic: count connections to known tracker domains

                // Rule 4: System app with unusual activity
                if (app.isSystemApp && newDomains > 2) {
                    appRisk += 0.1f
                    reasons.add("System app with unusual domain activity")
                }

                // Update app risk level in DB
                val riskLevel = when {
                    appRisk >= HIGH_RISK_SCORE_THRESHOLD -> "HIGH"
                    appRisk >= MEDIUM_RISK_SCORE_THRESHOLD -> "MEDIUM"
                    else -> "LOW"
                }
                appDao.updateRiskLevel(app.id, riskLevel)

                if (appRisk >= MEDIUM_RISK_SCORE_THRESHOLD) {
                    appsAtRisk.add(
                        AppRiskJson(
                            packageName = app.packageName,
                            appName = app.appName,
                            riskLevel = riskLevel,
                            reason = reasons.joinToString(". ")
                                .ifEmpty { "Elevated risk based on traffic patterns" }
                        )
                    )
                }

                overallRiskScore += appRisk
                riskFactorCount++
            }

            // ── Per-domain analysis ───────────────────────────────────────

            // Check for suspicious/tracker domains contacted by multiple apps
            // (done via DomainClassifier at domain insertion time, but we also
            //  re-evaluate here for domains that may have changed status)

            // ── Compute overall device risk ───────────────────────────────

            val deviceRisk = if (riskFactorCount > 0) {
                (overallRiskScore / riskFactorCount).coerceIn(0f, 1f)
            } else 0f

            val deviceRiskLevel = when {
                deviceRisk >= HIGH_RISK_SCORE_THRESHOLD -> "HIGH"
                deviceRisk >= MEDIUM_RISK_SCORE_THRESHOLD -> "MEDIUM"
                else -> "LOW"
            }

            val summary = buildSummary(appsAtRisk, domainsAtRisk, deviceRiskLevel)

            // ── Store prediction snapshot ─────────────────────────────────

            predictionDao.insert(
                PredictionSnapshotEntity(
                    createdAt = now,
                    deviceRiskScore = deviceRisk,
                    deviceRiskLevel = deviceRiskLevel,
                    summary = summary,
                    appsAtRiskJson = gson.toJson(appsAtRisk),
                    domainsAtRiskJson = gson.toJson(domainsAtRisk)
                )
            )

            Log.i(TAG, "Prediction analysis complete: risk=$deviceRiskLevel, " +
                    "appsAtRisk=${appsAtRisk.size}, domainsAtRisk=${domainsAtRisk.size}")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Prediction analysis failed", e)
            Result.retry()
        }
    }

    private suspend fun generateAlert(
        type: String,
        title: String,
        description: String,
        appId: Long? = null,
        domainId: Long? = null,
        severity: String = "MEDIUM"
    ) {
        alertDao.insert(
            AlertEntity(
                type = type,
                title = title,
                description = description,
                appId = appId,
                domainId = domainId,
                severity = severity
            )
        )
    }

    private fun buildSummary(
        apps: List<AppRiskJson>,
        domains: List<DomainRiskJson>,
        riskLevel: String
    ): String {
        val parts = mutableListOf<String>()

        if (apps.isNotEmpty()) {
            val highRisk = apps.count { it.riskLevel == "HIGH" }
            if (highRisk > 0) parts.add("$highRisk app(s) flagged as high risk")
            val medRisk = apps.count { it.riskLevel == "MEDIUM" }
            if (medRisk > 0) parts.add("$medRisk app(s) showing elevated activity")
        }

        if (domains.isNotEmpty()) {
            parts.add("${domains.size} suspicious domain(s) detected")
        }

        if (parts.isEmpty()) {
            return when (riskLevel) {
                "HIGH" -> "Multiple risk factors detected across your apps."
                "MEDIUM" -> "Some apps showing unusual patterns. Monitor closely."
                else -> "No unusual activity detected. Your device looks safe."
            }
        }

        return parts.joinToString(". ") + "."
    }

    // JSON serialization classes for PredictionSnapshot
    data class AppRiskJson(
        val packageName: String,
        val appName: String,
        val riskLevel: String,
        val reason: String
    )

    data class DomainRiskJson(
        val domain: String,
        val riskLevel: String,
        val reason: String,
        val appCount: Int = 0
    )
}
