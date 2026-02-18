package com.netflow.predict

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.netflow.predict.worker.DataRetentionWorker
import com.netflow.predict.worker.PredictionWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class — sets up Hilt DI and initializes WorkManager
 * with the Hilt worker factory for injected workers.
 *
 * Declared in AndroidManifest.xml via android:name=".NetFlowApp"
 */
@HiltAndroidApp
class NetFlowApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        schedulePredictionWork()
        scheduleRetentionWork()
    }

    private fun schedulePredictionWork() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PredictionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PredictionWorker.buildWorkRequest()
        )
    }

    private fun scheduleRetentionWork() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DataRetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            DataRetentionWorker.buildWorkRequest()
        )
    }
}
