package ru.drsn.waves.services.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.drsn.waves.domain.usecase.fcm.ProcessFcmMessageUseCase
import ru.drsn.waves.domain.usecase.fcm.RegisterFcmTokenUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Сервис для обработки сообщений от Firebase Cloud Messaging (FCM).
 * Этот сервис отвечает за:
 * 1. Получение и обновление FCM токенов устройства.
 * 2. Прием входящих push-уведомлений (data-only messages) от сервера.
 *
 * Аннотация `@AndroidEntryPoint` позволяет Hilt внедрять зависимости в этот сервис.
 */
@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var registerFcmTokenUseCase: RegisterFcmTokenUseCase

    @Inject
    lateinit var processFcmMessageUseCase: ProcessFcmMessageUseCase

    private val serviceJob = SupervisorJob()
    // Используем Dispatchers.IO для потенциально блокирующих операций, таких как сетевые вызовы.
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /**
     * Вызывается, когда Firebase генерирует новый FCM токен для устройства
     * или когда существующий токен обновляется (например, после переустановки приложения,
     * восстановления на новом устройстве, или если Firebase решит обновить токен).
     * Крайне важно отправить этот токен на ваш сервер для корректной доставки уведомлений.
     *
     * @param token Новый или обновленный FCM токен.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag(TAG).d("Refreshed FCM token: $token")
        serviceScope.launch {
            try {
                // Инициируем процесс регистрации/обновления токена на бэкенд-сервере.
                registerFcmTokenUseCase.execute(token)
                Timber.tag(TAG).i("FCM token registration process initiated with server.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to initiate FCM token registration")
                // Здесь можно добавить логику для сохранения токена и повторной отправки позже.
            }
        }
    }

    /**
     * Вызывается, когда приложение получает входящее FCM сообщение.
     * Этот метод будет вызван, если приложение на переднем плане,
     * или если это data-only сообщение (когда приложение в фоне или закрыто).
     * Ваш сервер должен отправлять data-only сообщения для полного контроля над отображением уведомлений.
     *
     * @param remoteMessage Объект, представляющий полученное FCM сообщение.
     *                      Содержит информацию об отправителе и полезную нагрузку (data payload).
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.tag(TAG).d("FCM Message Received From: ${remoteMessage.from}")

        // Извлекаем полезную нагрузку из data-секции сообщения.
        // Предполагается, что сервер всегда отправляет 'event_type' для определения типа уведомления.
        val dataPayload = remoteMessage.data
        if (dataPayload.isNotEmpty()) {
            Timber.tag(TAG).d("Message data payload: $dataPayload")
            val eventType = dataPayload["event_type"] ?: "unknown_event" // Тип события из полезной нагрузки
            serviceScope.launch {
                try {
                    // Передаем данные в UseCase для дальнейшей обработки (например, отображения уведомления).
                    processFcmMessageUseCase.execute(eventType, dataPayload, applicationContext)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error processing FCM message in UseCase")
                }
            }
        } else {
            Timber.tag(TAG).w("Received FCM message without data payload.")
        }

        // Обработка 'notification' payload, если он вдруг придет (например, из Firebase Console).
        // Для сообщений от вашего кастомного сервера это поле обычно будет null.
        remoteMessage.notification?.let {
            Timber.tag(TAG).d("Message Notification Body (unexpected for data-only from custom server): ${it.body}")
            // Здесь можно было бы отобразить стандартное уведомление, если это необходимо.
        }
    }

    /**
     * Вызывается при уничтожении сервиса.
     * Необходимо отменить все запущенные корутины, чтобы избежать утечек.
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Отменяет все корутины в serviceScope
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}