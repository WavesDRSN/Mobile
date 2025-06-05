package ru.drsn.waves.data.repository

import io.grpc.StatusRuntimeException
import ru.drsn.waves.data.datasource.remote.fcm.FcmRemoteDataSource
import ru.drsn.waves.domain.model.fcm.FcmError
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.FcmRepository
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [FcmRepository] для управления FCM токенами.
 * Взаимодействует с [FcmRemoteDataSource] для отправки токена на сервер
 * и обрабатывает возможные ошибки, преобразуя их в доменную модель [Result].
 */
@Singleton
class FcmRepositoryImpl @Inject constructor(
    private val fcmRemoteDataSource: FcmRemoteDataSource
) : FcmRepository {

    /**
     * Отправляет FCM токен на сервер через [FcmRemoteDataSource] и обрабатывает ответ.
     *
     * @param token Новый FCM токен.
     * @return [Result.Success] с [Unit] в случае успешной регистрации/обновления токена на сервере,
     *         либо [Result.Error] с [FcmError] в случае ошибки.
     */
    override suspend fun registerFcmTokenWithServer(token: String): Result<Unit, FcmError> {
        return try {
            // Вызов метода из DataSource для отправки токена на сервер.
            val response = fcmRemoteDataSource.updateFcmTokenOnServer(token)
            if (response.success) {
                Timber.i("FCM token successfully registered/updated on server.")
                Result.Success(Unit)
            } else {
                // Сервер вернул неуспешный статус.
                Timber.w("Server indicated failure for FCM token update: ${response.errorMessage}")
                Result.Error(FcmError.ServerError(response.errorMessage.takeIf { it.isNotEmpty() }))
            }
        } catch (e: StatusRuntimeException) {
            // Обработка ошибок gRPC (например, сетевые проблемы, ошибки сервера, не пойманные выше).
            Timber.e(e, "gRPC error during FCM token registration: ${e.status}")
            Result.Error(FcmError.NetworkError("gRPC error: ${e.status.code} - ${e.status.description}"))
        } catch (e: IOException) {
            // Обработка общих ошибок ввода-вывода (например, отсутствие сетевого подключения).
            Timber.e(e, "Network IOException during FCM token registration")
            Result.Error(FcmError.NetworkError("Network connection issue: ${e.message}"))
        } catch (e: Exception) {
            // Обработка любых других непредвиденных исключений.
            Timber.e(e, "Unknown error during FCM token registration")
            Result.Error(FcmError.UnknownError(e))
        }
    }
}