package ru.drsn.waves.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.drsn.waves.data.datasource.local.compression.ChatCompressorImpl
import ru.drsn.waves.data.datasource.local.compression.IChatCompressor
import ru.drsn.waves.data.datasource.local.crypto.ChatCipherImpl
import ru.drsn.waves.data.datasource.local.crypto.CryptoLocalDataSourceImpl
import ru.drsn.waves.data.datasource.local.crypto.IChatCipher
import ru.drsn.waves.data.datasource.local.crypto.ICryptoLocalDataSource
import ru.drsn.waves.data.datasource.remote.grpc.authentication.AuthenticationRemoteDataSourceImpl
import ru.drsn.waves.data.datasource.remote.grpc.authentication.IAuthenticationRemoteDataSource
import ru.drsn.waves.data.datasource.remote.grpc.signaling.ISignalingRemoteDataSource
import ru.drsn.waves.data.datasource.remote.grpc.signaling.SignalingRemoteDataSourceImpl
import ru.drsn.waves.data.datasource.remote.webrtc.IWebRTCController
import ru.drsn.waves.data.datasource.remote.webrtc.WebRTCControllerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    @Singleton
    abstract fun bindAuthenticationRemoteDataSource(
        impl: AuthenticationRemoteDataSourceImpl
    ): IAuthenticationRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCryptoLocalDataSource(
        impl: CryptoLocalDataSourceImpl
    ): ICryptoLocalDataSource

    @Binds
    @Singleton
    abstract fun bindSignalingRemoteDataSource(
        impl: SignalingRemoteDataSourceImpl
    ): ISignalingRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindWebRTCController( // IWebRTCController является своего рода локальным источником данных для WebRTC
        impl: WebRTCControllerImpl
    ): IWebRTCController

    @Binds
    @Singleton
    abstract fun bindChatCompressor( // IWebRTCController является своего рода локальным источником данных для WebRTC
        impl: ChatCompressorImpl
    ): IChatCompressor

    @Binds
    @Singleton
    abstract fun bindChatCipher( // IWebRTCController является своего рода локальным источником данных для WebRTC
        impl: ChatCipherImpl
    ): IChatCipher
}