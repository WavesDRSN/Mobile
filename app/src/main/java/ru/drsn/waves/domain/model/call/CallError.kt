package ru.drsn.waves.domain.model.call

sealed class CallError {
    object NetworkError : CallError()
    object AlreadyInCall : CallError()
    object NotInCall : CallError()
    data class Unknown(val message: String) : CallError()
}