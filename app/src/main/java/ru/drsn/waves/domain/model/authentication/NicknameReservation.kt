package ru.drsn.waves.domain.model.authentication

data class NicknameReservation(
    val reservationToken: String,
    val expiresAtUnix: Long,
    val nickname: String
)