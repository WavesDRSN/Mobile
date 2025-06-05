package ru.drsn.waves.data.datasource.remote.grpc.authentication

sealed class AuthenticationError(message: String, cause: Throwable?) : Error(message, cause) {
    class NicknameTakenException(message: String, cause: Throwable?) : AuthenticationError(message, cause)
    class AuthenticationFailedException(message: String, cause: Throwable?) : AuthenticationError(message, cause)
    class ConnectionException(message: String, cause: Throwable? = null) : AuthenticationError(message, cause)
    class GrpcException(message: String, cause: Throwable? = null) : AuthenticationError(message, cause)
}