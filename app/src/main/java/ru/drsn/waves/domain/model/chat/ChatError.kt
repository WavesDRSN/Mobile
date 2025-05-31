package ru.drsn.waves.domain.model.chat

sealed class ChatError {
    data class StorageError(val message: String?, val cause: Throwable? = null) : ChatError()
    data class EncryptionError(val message: String?, val cause: Throwable? = null) : ChatError()
    data class CompressionError(val message: String?, val cause: Throwable? = null) : ChatError()
    data class NotFound(val message: String?) : ChatError()
    data class OperationFailed(val message: String?, val cause: Throwable? = null) : ChatError()
    data class NetworkError(val message: String?, val cause: Throwable? = null) : ChatError()
}