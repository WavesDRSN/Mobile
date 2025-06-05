package ru.drsn.waves.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.drsn.waves.data.datasource.remote.fcm.FcmRemoteDataSource
import ru.drsn.waves.data.repository.FcmRepositoryImpl
import ru.drsn.waves.domain.repository.FcmRepository
import ru.drsn.waves.domain.repository.IAuthenticationRepository
import ru.drsn.waves.domain.usecase.fcm.ProcessFcmMessageUseCase
import ru.drsn.waves.domain.usecase.fcm.RegisterFcmTokenUseCase
import gRPC.v1.Authentication.AuthorisationGrpcKt // Для FcmRemoteDataSource
import gRPC.v1.Notification.NotificationServiceGrpcKt
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Singleton

/**
 * Hilt модуль для предоставления зависимостей, связывающих интерфейсы с их реализациями,
 * специфичных для функциональности FCM.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FcmBindingModule {

    /**
     * Связывает интерфейс [FcmRepository] с его конкретной реализацией [FcmRepositoryImpl].
     * Hilt будет использовать эту привязку, когда требуется экземпляр [FcmRepository].
     */
    @Binds
    @Singleton
    abstract fun bindFcmRepository(fcmRepositoryImpl: FcmRepositoryImpl): FcmRepository
}

/**
 * Hilt модуль для предоставления конкретных реализаций и UseCase'ов,
 * специфичных для функциональности FCM.
 */
@Module
@InstallIn(SingletonComponent::class)
object FcmProvidesModule {

    /**
     * Предоставляет экземпляр [FcmRemoteDataSource].
     * [FcmRemoteDataSource] зависит от [AuthorisationGrpcKt.AuthorisationCoroutineStub],
     * который предоставляется [GrpcModule].
     */
    @Provides
    @Singleton
    fun provideFcmRemoteDataSource(
        authorisationStub: AuthorisationGrpcKt.AuthorisationCoroutineStub,
        notificationStub: NotificationServiceGrpcKt.NotificationServiceCoroutineStub
    ): FcmRemoteDataSource {
        return FcmRemoteDataSource(authorisationStub, notificationStub)
    }

    /**
     * Предоставляет экземпляр [RegisterFcmTokenUseCase].
     * Этот UseCase отвечает за логику регистрации FCM токена.
     * Зависит от [IAuthenticationRepository] (для вызова gRPC метода) и [UserPreferencesRepository] (для проверки статуса логина).
     */
    @Provides
    @Singleton // UseCases могут быть Singleton, если они stateless или их создание дорого.
    fun provideRegisterFcmTokenUseCase(
        authenticationRepository: IAuthenticationRepository,
        cryptoRepository: ICryptoRepository // <--- Обновленная зависимость
    ): RegisterFcmTokenUseCase {
        return RegisterFcmTokenUseCase(authenticationRepository, cryptoRepository)
    }

    /**
     * Предоставляет экземпляр [ProcessFcmMessageUseCase].
     * Этот UseCase отвечает за обработку содержимого входящих FCM сообщений.
     * Может зависеть от других репозиториев для выполнения действий на основе сообщения.
     */
    @Provides
    @Singleton
    fun provideProcessFcmMessageUseCase(
        // Пример: сюда можно добавить зависимости, если ProcessFcmMessageUseCase их требует
        // messageRepository: MessageRepository
    ): ProcessFcmMessageUseCase {
        // return ProcessFcmMessageUseCase(messageRepository)
        return ProcessFcmMessageUseCase() // В текущей реализации он не имеет доп. зависимостей в конструкторе
    }
}