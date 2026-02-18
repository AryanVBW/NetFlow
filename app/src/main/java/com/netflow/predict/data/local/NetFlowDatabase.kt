package com.netflow.predict.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.netflow.predict.data.local.dao.*
import com.netflow.predict.data.local.entity.*

@Database(
    entities = [
        AppEntity::class,
        DomainEntity::class,
        ConnectionEntity::class,
        DnsQueryEntity::class,
        AlertEntity::class,
        PredictionSnapshotEntity::class,
        TrafficStatsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NetFlowDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun domainDao(): DomainDao
    abstract fun connectionDao(): ConnectionDao
    abstract fun dnsQueryDao(): DnsQueryDao
    abstract fun alertDao(): AlertDao
    abstract fun predictionSnapshotDao(): PredictionSnapshotDao
    abstract fun trafficStatsDao(): TrafficStatsDao
}
