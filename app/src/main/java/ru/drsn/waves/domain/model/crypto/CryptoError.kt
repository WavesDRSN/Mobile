package ru.drsn.waves.domain.model.crypto

sealed class CryptoError {
    data object KeyNotFound : CryptoError()
    data class StoreError(val message: String?, val cause: Throwable? = null) : CryptoError()
    data class LoadError(val message: String?, val cause: Throwable? = null) : CryptoError()
    data class SigningError(val message: String?, val cause: Throwable? = null) : CryptoError()
    data class VerificationError(val message: String?, val cause: Throwable? = null) : CryptoError()
    data class GenerationError(val message: String?, val cause: Throwable? = null) : CryptoError()
    data class InitializationError(val message: String?, val cause: Throwable? = null) : CryptoError()
    data class DeletionError(val message: String?, val cause: Throwable? = null) : CryptoError()
    data class Unknown(val message: String?, val cause: Throwable? = null) : CryptoError()
}