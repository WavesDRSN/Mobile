package ru.drsn.waves.domain.repository

import ru.drsn.waves.domain.model.fcm.FcmError
import ru.drsn.waves.domain.model.utils.Result

/**
 * Интерфейс репозитория для операций, связанных с Firebase Cloud Messaging (FCM).
 * Абстрагирует слой данных от доменного слоя для управления FCM токенами.
 */
interface FcmRepository {
    /**
     * Регистрирует (или обновляет) FCM токен устройства на бэкенд-сервере.
     *
     * @param token Новый FCM токен устройства.
     * @return [Result] обертка, содержащая либо [Unit] в случае успеха, либо [FcmError] в случае ошибки.
     */
    suspend fun registerFcmTokenWithServer(token: String): Result<Unit, FcmError>
}