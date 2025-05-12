package ru.drsn.waves.domain.model.authentication

sealed class AuthError {
    data object NicknameTaken : AuthError()
    data object ReservationExpired : AuthError()
    data class RegistrationFailed(val message: String?) : AuthError()
    data class AuthenticationFailed(val message: String?) : AuthError()
    data object ConnectionError : AuthError()
    data class SigningError(val cause: Throwable?) : AuthError()
    data class Unknown(val message: String?, val cause: Throwable? = null) : AuthError()
}
