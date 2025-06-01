package ru.drsn.waves.domain.repository

import gRPC.v1.Authentication.ChallengeResponse
import ru.drsn.waves.domain.model.authentication.AuthError
import ru.drsn.waves.domain.model.authentication.NicknameReservation
import ru.drsn.waves.domain.model.authentication.PublicKey
import ru.drsn.waves.domain.model.authentication.Signature
import ru.drsn.waves.domain.model.crypto.AuthToken
import ru.drsn.waves.domain.model.utils.Result

interface IAuthenticationRepository {

    suspend fun reserveNickname(nickname: String): Result<NicknameReservation, AuthError>

    suspend fun register(reservationToken: String, publicKey: PublicKey): Result<Unit, AuthError>

    // Разделяем получение challenge и проверку аутентификации
    suspend fun getChallenge(nickname: String): Result<ChallengeResponse, AuthError>

    suspend fun updateFcmToken(fcmToken: String): Result<Unit, AuthError>

    suspend fun verifyAuthentication(
        nickname: String,
        challengeResponse: ChallengeResponse,
        signature: Signature
    ): Result<String, AuthError>
}