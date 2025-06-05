package ru.drsn.waves.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gRPC.v1.Notification.NotificationServiceGrpcKt
import io.grpc.ManagedChannel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class GrpcModule {
    @Provides
    @Singleton
    fun provideNotificationStub(channel: ManagedChannel): NotificationServiceGrpcKt.NotificationServiceCoroutineStub {
        return NotificationServiceGrpcKt.NotificationServiceCoroutineStub(channel)
    }
}