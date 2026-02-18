package com.netflow.predict.di

import com.netflow.predict.data.repository.TrafficRepository
import com.netflow.predict.data.repository.VpnRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module — provides singleton repository instances.
 *
 * Both repositories are annotated with @Singleton and @Inject constructor,
 * so Hilt can construct them directly. This module exists as an explicit
 * binding point to make replacement with fakes in tests straightforward.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVpnRepository(): VpnRepository = VpnRepository()

    @Provides
    @Singleton
    fun provideTrafficRepository(): TrafficRepository = TrafficRepository()
}
