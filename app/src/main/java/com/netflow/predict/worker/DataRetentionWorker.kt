package com.netflow.predict.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.netflow.predict.data.local.dao.ConnectionDao
import com.netflow.predict.data.local.dao.DnsQueryDao
import com.netflow.predict.data.local.dao.TrafficStatsDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic cleanup worker that deletes old data based on user retention settings.
 * Runs once per day, preferably while charging.
 */
@HiltWorker
class DataRetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val connectionDao: ConnectionDao,
    private val dnsQueryDao: DnsQueryDao,
    private val trafficStatsDao: TrafficStatsDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DataRetentionWorker"
        const val WORK_NAME = "netflow_data_retention"
        const val KEY_RETENTION_DAYS = "retention_days"

        fun buildWorkRequest(retentionDays: Int = 30): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<DataRetentionWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .build()
                )
                .setInputData(
                    workDataOf(KEY_RETENTION_DAYS to retentionDays)
                )
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val retentionDays = inputData.getInt(KEY_RETENTION_DAYS, 30)
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())

        return try {
            Log.i(TAG, "Cleaning data older than $retentionDays days")

            connectionDao.deleteOlderThan(cutoff)
            dnsQueryDao.deleteOlderThan(cutoff)
            trafficStatsDao.deleteOlderThan(cutoff)

            Log.i(TAG, "Data retention cleanup complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Data retention cleanup failed", e)
            Result.retry()
        }
    }
}
