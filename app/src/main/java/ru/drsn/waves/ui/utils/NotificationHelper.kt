package ru.drsn.waves.ui.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.drsn.waves.R
import ru.drsn.waves.ui.launcher.LauncherActivity // Укажите вашу главную Activity
import timber.log.Timber

/**
 * Утилитарный объект (singleton) для работы с системными уведомлениями Android.
 * Отвечает за:
 * 1. Создание каналов уведомлений (обязательно для Android 8.0 Oreo и выше).
 * 2. Построение и отображение уведомлений на основе полученных данных.
 */
object NotificationHelper {

    /** ID канала для стандартных уведомлений (новые сообщения, запросы и т.д.). */
    const val DEFAULT_CHANNEL_ID = "waves_default_notifications_v1"
    /** ID канала для уведомлений о входящих звонках (может иметь другие настройки звука/вибрации). */
    const val CALLS_CHANNEL_ID = "waves_incoming_calls_v1"
    // Другие ID каналов можно добавить здесь по мере необходимости.

    /**
     * Создает каналы уведомлений. Должен вызываться при старте приложения.
     * Для Android 8.0 (API 26) и выше, уведомления должны быть привязаны к каналу.
     * Пользователь может настраивать поведение каждого канала (звук, вибрация, показ на экране блокировки).
     *
     * @param context Контекст приложения.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Канал для общих уведомлений
            val defaultChannelName = context.getString(R.string.notification_channel_default_name)
            val defaultChannelDescription = context.getString(R.string.notification_channel_default_description)
            val defaultChannel = NotificationChannel(
                DEFAULT_CHANNEL_ID,
                defaultChannelName,
                NotificationManager.IMPORTANCE_HIGH // Важность влияет на то, как уведомление будет прерывать пользователя
            ).apply {
                description = defaultChannelDescription
                // Здесь можно настроить дополнительные параметры канала:
                // setShowBadge(true) // Показывать точку на иконке приложения
                // enableLights(true)
                // lightColor = Color.BLUE
                // enableVibration(true)
                // vibrationPattern = longArrayOf(100, 200, 300, 400, 500)
            }
            notificationManager.createNotificationChannel(defaultChannel)

            // Канал для входящих звонков
            val callsChannelName = context.getString(R.string.notification_channel_calls_name)
            val callsChannelDescription = context.getString(R.string.notification_channel_calls_description)
            val callsChannel = NotificationChannel(
                CALLS_CHANNEL_ID,
                callsChannelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = callsChannelDescription
                // Для звонков можно установить специфический звук и паттерн вибрации,
                // который будет использоваться по умолчанию для этого канала.
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500) // Пример паттерна "пульсирующей" вибрации
            }
            notificationManager.createNotificationChannel(callsChannel)

            Timber.i("Notification channels created: '$defaultChannelName', '$callsChannelName'.")
        }
    }

    /**
     * Создает и отображает системное уведомление.
     *
     * @param context Контекст для доступа к системным сервисам и ресурсам.
     * @param eventType Тип события (например, "new_message", "incoming_call"), используется для настройки контента.
     * @param data Карта с данными, полученными из FCM, для наполнения уведомления (например, имя отправителя, текст сообщения).
     * @param targetActivityClass Класс Activity, которая будет запущена при нажатии на уведомление. По умолчанию - LauncherActivity.
     */
    fun showNotification(
        context: Context,
        eventType: String,
        data: Map<String, String>,
        targetActivityClass: Class<*> = LauncherActivity::class.java
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Генерируем уникальный ID для каждого уведомления, чтобы они не перезаписывали друг друга.
        // Для группировки или обновления можно использовать более осмысленные ID.
        val notificationId = System.currentTimeMillis().toInt()

        var title = context.getString(R.string.app_name) // Заголовок по умолчанию
        var body = context.getString(R.string.notification_default_body) // Тело по умолчанию
        var channelId = DEFAULT_CHANNEL_ID // Канал по умолчанию
        val smallIcon = R.drawable.ic_stat_notification // Иконка для статус-бара (должна быть монохромной)

        // Настройка контента уведомления в зависимости от типа события
        when (eventType) {
            "new_message" -> {
                title = data["sender_nickname"] ?: context.getString(R.string.notification_new_message_title)
                body = data["message_preview"] ?: context.getString(R.string.notification_new_message_body_default)
                // channelId остается DEFAULT_CHANNEL_ID
            }
            "incoming_call" -> {
                title = context.getString(R.string.notification_incoming_call_title)
                body = "${context.getString(R.string.notification_incoming_call_from)} ${data["caller_nickname"] ?: context.getString(R.string.unknown_caller)}"
                channelId = CALLS_CHANNEL_ID // Используем специальный канал для звонков
            }
            "contact_request" -> {
                title = context.getString(R.string.notification_contact_request_title)
                body = "${data["requester_nickname"] ?: context.getString(R.string.someone)} ${context.getString(R.string.notification_contact_request_body_suffix)}"
            }
            "generic_notification", "unknown_event" -> { // Общее или неизвестное уведомление
                title = data["title"] ?: context.getString(R.string.app_name)
                body = data["body"] ?: data["message"] ?: context.getString(R.string.notification_generic_body)
            }
            else -> {
                Timber.w("Received unknown event_type for notification: $eventType")
                body = "${context.getString(R.string.notification_unknown_event_type)}: $eventType"
            }
        }

        // Intent, который будет запущен при нажатии на уведомление.
        val intent = Intent(context, targetActivityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Стандартные флаги для запуска Activity из уведомления
            // Передаем данные из уведомления в Activity для дальнейшей обработки (например, навигации к чату).
            putExtra("fcm_event_type", eventType) // Передаем тип события
            data.forEach { (key, value) ->
                // Добавляем префикс, чтобы избежать конфликтов с другими extras в Intent.
                if (!key.equals("fcm_event_type", ignoreCase = true)) {
                    putExtra("fcm_data_$key", value)
                }
            }
            // Например, для "new_message" можно передать ID чата:
            // if (eventType == "new_message") putExtra("fcm_data_chat_id", data["chat_id"])
        }

        // Флаг для PendingIntent в зависимости от версии Android
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationId, intent, pendingIntentFlag)

        // Создание уведомления с помощью NotificationCompat.Builder для совместимости.
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon) // Маленькая иконка (обязательно)
            .setContentTitle(title) // Заголовок уведомления
            .setContentText(body) // Текст уведомления
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // Позволяет отображать длинный текст
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Приоритет (влияет на "heads-up" уведомления)
            .setAutoCancel(true) // Уведомление исчезнет после нажатия
            .setContentIntent(pendingIntent) // Intent, который выполнится при нажатии
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS) // Использовать светодиод по умолчанию (если есть и настроен в канале)

        // Настройка звука и вибрации для версий Android < 8.0 (Oreo).
        // Для Oreo и выше звук/вибрация управляются через NotificationChannel.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (channelId == CALLS_CHANNEL_ID) { // Если это звонок, используем звук рингтона
                notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                notificationBuilder.setVibrate(longArrayOf(0, 500, 200, 500, 200, 500)) // Паттерн вибрации для звонка
            } else { // Для других уведомлений - стандартный звук и вибрация
                notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                notificationBuilder.setVibrate(longArrayOf(0, 250, 100, 250)) // Короткая вибрация
            }
        }

        try {
            // Отображение уведомления.
            notificationManager.notify(notificationId, notificationBuilder.build())
            Timber.i("Notification displayed for event: $eventType, ID: $notificationId, Channel: $channelId")
        } catch (e: SecurityException) {
            // Это может произойти на Android 13+, если не предоставлено разрешение POST_NOTIFICATIONS.
            Timber.e(e, "SecurityException: Missing POST_NOTIFICATIONS permission on Android 13+?")
        } catch (e: Exception) {
            Timber.e(e, "Failed to show notification for event: $eventType")
        }
    }
}