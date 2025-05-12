package ru.drsn.waves.data.datasource.remote.grpc.authentication

import com.google.protobuf.ByteString

data class NicknameReservationDto(val reservationToken: String, val expiresAtUnix: Long, val nickname: String)
data class RegistrationResponseDto(val success: Boolean, val errorMessage: String?)
data class ChallengeResponseDto(val challenge: ByteString, val challengeId: String)
data class AuthenticationResponseDto(val success: Boolean, val errorMessage: String?, val jwtToken: String?)
