package ru.drsn.waves.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gRPC.v1.Authentication.AuthorisationGrpcKt // Added import
import gRPC.v1.Notification.NotificationServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder // Added import
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GrpcModule { // Changed to object for @Provides methods in a module without @Binds

    private const val GRPC_HOST = "10.0.2.2"
    private const val GRPC_PORT = 50051 // Example port, change if yours is different

    @Provides
    @Singleton
    fun provideManagedChannel(): ManagedChannel {
        // Consider your security needs: usePlaintext() is for development/testing.
        // For production, use .useTransportSecurity() and configure TLS.
        return ManagedChannelBuilder.forAddress(GRPC_HOST, GRPC_PORT)
            .usePlaintext()
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthorisationCoroutineStub(channel: ManagedChannel): AuthorisationGrpcKt.AuthorisationCoroutineStub {
        return AuthorisationGrpcKt.AuthorisationCoroutineStub(channel)
    }

    @Provides
    @Singleton
    fun provideNotificationStub(channel: ManagedChannel): NotificationServiceGrpcKt.NotificationServiceCoroutineStub {
        return NotificationServiceGrpcKt.NotificationServiceCoroutineStub(channel)
    }
}