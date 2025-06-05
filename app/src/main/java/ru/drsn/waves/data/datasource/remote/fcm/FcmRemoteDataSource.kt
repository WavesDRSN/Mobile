package ru.drsn.waves.data.datasource.remote.fcm

import gRPC.v1.Authentication.AuthorisationGrpcKt
import gRPC.v1.Authentication.UpdateTokenRequest
import gRPC.v1.Authentication.UpdateTokenResponse // gRPC сгенерированный тип
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Источник данных для взаимодействия с удаленным сервером по вопросам, связанным с FCM.
 * В данном случае, отвечает за отправку FCM токена на сервер через gRPC.
 * Использует тот же gRPC стаб (`AuthorisationCoroutineStub`), что и для аутентификации,
 * так как метод `updateFcmToken` является частью сервиса `Authorisation`.
 */
@Singleton
class FcmRemoteDataSource @Inject constructor(
    private val authorisationStub: AuthorisationGrpcKt.AuthorisationCoroutineStub
) {

    /**
     * Отправляет FCM токен на бэкенд-сервер для регистрации или обновления.
     * Этот метод вызывается, когда генерируется новый токен или когда существующий токен нужно обновить на сервере.
     * Предполагается, что для этого вызова требуется аутентификация пользователя (JWT токен будет добавлен `AuthInterceptor`).
     *
     * @param fcmTokenValue Новый FCM токен устройства.
     * @return [UpdateTokenResponse] Ответ от сервера, указывающий на успех или неудачу операции.
     * @throws io.grpc.StatusRuntimeException если произошла ошибка во время gRPC вызова (например, сетевая проблема или ошибка сервера).
     */
    suspend fun updateFcmTokenOnServer(fcmTokenValue: String): UpdateTokenResponse {
        val request = UpdateTokenRequest.newBuilder()
            .setFcmToken(fcmTokenValue)
            .build()
        // Вызов gRPC метода. AuthInterceptor автоматически добавит JWT, если он есть.
        return authorisationStub.updateFcmToken(request)
    }
}