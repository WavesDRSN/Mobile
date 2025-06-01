package ru.drsn.waves.domain.model.fcm

sealed class FcmError {
    /** Ошибка, связанная с сетевым взаимодействием (например, нет подключения, таймаут, ошибка gRPC). */
    data class NetworkError(val message: String?) : FcmError()
    /** Ошибка, возвращенная сервером (например, невалидный токен, внутренняя ошибка сервера). */
    data class ServerError(val message: String?) : FcmError()
    /** Неизвестная или непредвиденная ошибка. */
    data class UnknownError(val throwable: Throwable?) : FcmError()
}