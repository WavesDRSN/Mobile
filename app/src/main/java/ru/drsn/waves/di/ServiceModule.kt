package ru.drsn.waves.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.drsn.waves.authentication.AuthenticationService
import ru.drsn.waves.authentication.IAuthenticationService
import ru.drsn.waves.crypto.CryptoService
import ru.drsn.waves.signaling.SignalingService
import ru.drsn.waves.signaling.SignalingServiceImpl
import ru.drsn.waves.webrtc.WebRTCManager
import ru.drsn.waves.webrtc.contract.IWebRTCManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindSignalingService(
        impl: SignalingServiceImpl
    ): SignalingService

    @Binds
    @Singleton
    abstract fun bindWebRTCManager(
        impl: WebRTCManager
    ): IWebRTCManager

    @Binds
    @Singleton
    abstract fun bindCryptoService(
        impl: CryptoService
    ): CryptoService

    @Binds
    @Singleton
    abstract fun bindAuthenticationService(
        impl: AuthenticationService
    ): IAuthenticationService
}