package ru.drsn.waves.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.drsn.waves.data.repository.AuthenticationRepositoryImpl
import ru.drsn.waves.data.repository.CryptoRepositoryImpl
import ru.drsn.waves.data.repository.SignalingRepositoryImpl
import ru.drsn.waves.data.repository.WebRTCRepositoryImpl
import ru.drsn.waves.domain.repository.IAuthenticationRepository
import ru.drsn.waves.domain.repository.ICryptoRepository
import ru.drsn.waves.domain.repository.ISignalingRepository
import ru.drsn.waves.domain.repository.IWebRTCRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthenticationRepository(
        impl: AuthenticationRepositoryImpl
    ): IAuthenticationRepository

    @Binds
    @Singleton
    abstract fun bindCryptoRepository(
        impl: CryptoRepositoryImpl
    ): ICryptoRepository

    @Binds
    @Singleton
    abstract fun bindSignalingRepository(
        impl: SignalingRepositoryImpl
    ): ISignalingRepository

    @Binds
    @Singleton
    abstract fun bindWebRTCRepository(
        impl: WebRTCRepositoryImpl
    ): IWebRTCRepository
}