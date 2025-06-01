package ru.drsn.waves.domain.usecase.fcm

import android.content.Context
import ru.drsn.waves.ui.launcher.LauncherActivity // Укажите вашу главную Activity
import ru.drsn.waves.ui.utils.NotificationHelper
// Импортируйте другие репозитории или use cases, если они нужны для обработки сообщения
// import ru.drsn.waves.domain.repository.MessageRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case для обработки входящих FCM сообщений.
 * Отвечает за:
 * 1. Отображение уведомления пользователю с помощью [NotificationHelper].
 * 2. Выполнение дополнительных действий в зависимости от типа события, указанного в сообщении
 *    (например, обновление локальной базы данных, запуск синхронизации, навигация).
 */
class ProcessFcmMessageUseCase @Inject constructor(
    // Сюда можно внедрить зависимости, если они нужны для обработки сообщения, например:
    // private val messageRepository: MessageRepository,
    // private val userRepository: UserRepository
) {
    /**
     * Обрабатывает входящее FCM сообщение.
     *
     * @param eventType Тип события, извлеченный из полезной нагрузки FCM сообщения (например, "new_message").
     * @param data Карта с данными из полезной нагрузки FCM сообщения.
     * @param context Контекст приложения, необходимый, например, для [NotificationHelper].
     */
    suspend fun execute(eventType: String, data: Map<String, String>, context: Context) {
        Timber.i("Processing FCM event: '$eventType', data: $data")

        // Шаг 1: Показать уведомление пользователю.
        // Можно настроить targetActivityClass в зависимости от eventType, если это необходимо.
        NotificationHelper.showNotification(context, eventType, data, LauncherActivity::class.java)

        // Шаг 2: Выполнить дополнительные действия в зависимости от типа события.
        // Эта логика сильно зависит от требований вашего приложения.
        when (eventType) {
            "new_message" -> {
                val chatId = data["chat_id"]
                val messageId = data["message_id"]
                Timber.d("New message event received. ChatID: $chatId, MessageID: $messageId")
                // Примеры действий:
                // - Обновить локальную базу данных: пометить чат как непрочитанный, сохранить превью сообщения.
                //   (e.g., messageRepository.markChatAsUnread(chatId))
                // - Если приложение активно и экран чата открыт, можно обновить UI напрямую через EventBus или LiveData.
                // - Инициировать фоновую синхронизацию для получения полного сообщения.
            }
            "incoming_call" -> {
                val callerName = data["caller_nickname"]
                val callSessionId = data["session_id"] // Пример данных для звонка
                Timber.d("Incoming call event received from $callerName. Session: $callSessionId")
                // Примеры действий:
                // - Запустить UI для входящего звонка (например, полноэкранную Activity или Heads-up уведомление с действиями).
                // - Использовать системные API для звонков (CallKit/ConnectionService) для лучшей интеграции.
            }
            "contact_request" -> {
                val requesterName = data["requester_nickname"]
                Timber.d("Contact request received from $requesterName.")
                // Примеры действий:
                // - Обновить счетчик запросов в друзья в локальной БД или SharedPreferences.
                // - Показать индикатор (badge) в UI, если приложение активно.
            }
            "message_read" -> { // Пример: уведомление о прочтении сообщения другим пользователем
                val chatId = data["chat_id"]
                val lastReadMessageId = data["last_read_message_id"]
                Timber.d("Message read event. ChatID: $chatId, LastReadMessageID: $lastReadMessageId")
                // Примеры действий:
                // - Обновить статус сообщений в локальной базе данных как "прочитанные".
                // - Обновить UI, если соответствующий чат открыт.
            }
            // Добавьте обработку других event_type, которые ваш сервер может отправлять.
            else -> {
                Timber.w("Unhandled FCM event type in UseCase logic: $eventType. Data: $data")
            }
        }
    }
}