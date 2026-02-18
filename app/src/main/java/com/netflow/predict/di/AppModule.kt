package com.netflow.predict.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.netflow.predict.data.local.NetFlowDatabase
import com.netflow.predict.data.local.dao.*
import com.netflow.predict.data.repository.SettingsRepository
import com.netflow.predict.data.repository.TrafficRepository
import com.netflow.predict.data.repository.VpnRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module — provides all singleton dependencies.
 *
 * Provides the Room database, all DAOs, repositories,
 * and WorkManager instance.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Database ──────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NetFlowDatabase {
        return Room.databaseBuilder(
            context,
            NetFlowDatabase::class.java,
            "netflow_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    // ── DAOs ──────────────────────────────────────────────────────────────────

    @Provides
    fun provideAppDao(db: NetFlowDatabase): AppDao = db.appDao()

    @Provides
    fun provideDomainDao(db: NetFlowDatabase): DomainDao = db.domainDao()

    @Provides
    fun provideConnectionDao(db: NetFlowDatabase): ConnectionDao = db.connectionDao()

    @Provides
    fun provideDnsQueryDao(db: NetFlowDatabase): DnsQueryDao = db.dnsQueryDao()

    @Provides
    fun provideAlertDao(db: NetFlowDatabase): AlertDao = db.alertDao()

    @Provides
    fun providePredictionSnapshotDao(db: NetFlowDatabase): PredictionSnapshotDao =
        db.predictionSnapshotDao()

    @Provides
    fun provideTrafficStatsDao(db: NetFlowDatabase): TrafficStatsDao = db.trafficStatsDao()

    // ── WorkManager ───────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
