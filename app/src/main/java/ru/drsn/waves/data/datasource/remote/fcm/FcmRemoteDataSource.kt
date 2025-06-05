package ru.drsn.waves.data.datasource.remote.fcm

import gRPC.v1.Authentication.AuthorisationGrpcKt
import gRPC.v1.Authentication.UpdateTokenRequest
import gRPC.v1.Authentication.UpdateTokenResponse
import gRPC.v1.Notification.NotificationServiceGrpcKt
import gRPC.v1.Notification.MessageEvent
import gRPC.v1.Notification.NotificationResponse
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
    private val authorisationStub: AuthorisationGrpcKt.AuthorisationCoroutineStub,
    private val notificationStub: NotificationServiceGrpcKt.NotificationServiceCoroutineStub
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

    /**
     * Уведомляет сервер о том, что необходимо отправить push-уведомление получателю
     * о новом сообщении, которое не удалось доставить напрямую через P2P соединение.
     *
     * @param senderId ID пользователя-отправителя сообщения
     * @param receiverId ID пользователя-получателя сообщения
     * @return [NotificationResponse] Ответ от сервера о результате отправки уведомления
     * @throws io.grpc.StatusRuntimeException если произошла ошибка во время gRPC вызова
     */
    suspend fun notifyPendingMessage(senderId: String, receiverId: String): NotificationResponse {
        val messageEvent = MessageEvent.newBuilder()
            .setSenderId(senderId)
            .setReceiverId(receiverId)
            .build()

        return notificationStub.notifyServer(messageEvent)
    }

}