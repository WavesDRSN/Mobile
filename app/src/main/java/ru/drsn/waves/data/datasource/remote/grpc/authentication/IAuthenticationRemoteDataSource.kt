package ru.drsn.waves.data.datasource.remote.grpc.authentication

import com.google.protobuf.ByteString
import gRPC.v1.Authentication.ChallengeResponse

interface IAuthenticationRemoteDataSource {
    suspend fun reserveNickname(nickname: String): NicknameReservationDto // Замени NicknameReservationDto на твой gRPC Response/DTO
    suspend fun register(reservationToken: String, publicKey: ByteString): RegistrationResponseDto // Замени RegistrationResponseDto
    suspend fun getChallenge(nickname: String): ChallengeResponse // Замени ChallengeResponseDto
    suspend fun authenticate(nickname: String, challengeResponse: ChallengeResponse, signature: ByteString): AuthenticationResponseDto // Замени ...Dto
    suspend fun updateFcmToken(fcmToken: String): UpdateTokenResponseDto
    // Метод для установки соединения, если нужно управлять явно
    fun setupConnection(serverAddress: String, serverPort: Int)
    fun isConnected(): Boolean
    fun closeConnection()
}