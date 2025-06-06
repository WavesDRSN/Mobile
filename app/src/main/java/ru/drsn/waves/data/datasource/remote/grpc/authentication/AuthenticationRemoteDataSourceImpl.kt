package ru.drsn.waves.data.datasource.remote.grpc.authentication

import com.google.protobuf.ByteString
import gRPC.v1.Authentication.ChallengeResponse
import gRPC.v1.Authentication.UpdateTokenRequest
import io.grpc.StatusRuntimeException
import ru.drsn.waves.data.datasource.remote.grpc.AuthTokenInterceptor
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticationRemoteDataSourceImpl @Inject constructor(
    private val authTokenInterceptor: AuthTokenInterceptor
) : IAuthenticationRemoteDataSource {

    private var authenticationClient: AuthenticationClient? = null

    override fun setupConnection(serverAddress: String, serverPort: Int) {
        if (authenticationClient == null) {
            try {
                // Создаем клиент здесь (или получаем из фабрики)
                authenticationClient = AuthenticationClient(serverAddress, serverPort, authTokenInterceptor)
            } catch (e: Exception) {
                // Логирование ошибки
                throw IOException("Failed to initialize AuthenticationClient", e)
            }
        }
    }

    override fun isConnected(): Boolean {
        return authenticationClient != null // Упрощенная проверка
    }

    override fun closeConnection() {
        authenticationClient = null
    }


    private fun getClient(): AuthenticationClient {
        // Можно добавить ленивое создание или проверку соединения здесь
        return authenticationClient ?: throw IllegalStateException("Authentication client not initialized or connection closed. Call setupConnection first.")
    }

    override suspend fun reserveNickname(nickname: String): NicknameReservationDto {
        try {
            val response = getClient().reserveNickname(nickname) // Вызов твоего gRPC метода
            // Маппинг из gRPC Response в DTO
            return NicknameReservationDto(
                reservationToken = response.reservationToken,
                expiresAtUnix = response.expiresAtUnix,
                nickname = nickname
            )
        } catch (e: StatusRuntimeException) {
            // Преобразование gRPC ошибки в кастомное исключение или Result.Error
            throw mapGrpcError(e) // Нужен метод-маппер ошибок gRPC
        } catch (e: Exception) {
            throw IOException("Network error during reserveNickname", e)
        }
    }

    override suspend fun register(reservationToken: String, publicKey: ByteString): RegistrationResponseDto {
        try {
            val response = getClient().register(reservationToken, publicKey)
            return RegistrationResponseDto(response.success, response.errorMessage.takeIf { it.isNotEmpty() })
        } catch (e: StatusRuntimeException) {
            throw mapGrpcError(e)
        } catch (e: Exception) {
            throw IOException("Network error during register", e)
        }
    }

    override suspend fun getChallenge(nickname: String): ChallengeResponse {
        try {
            val response = getClient().getChallenge(nickname)
            return response
        } catch (e: StatusRuntimeException) {
            throw mapGrpcError(e)
        } catch (e: Exception) {
            throw IOException("Network error during getChallenge", e)
        }
    }

    override suspend fun authenticate(
        nickname: String,
        challengeResponse: ChallengeResponse,
        signature: ByteString
    ): AuthenticationResponseDto {
        try {
            // Адаптируй вызов под твой gRPC метод authenticate, передавая нужные параметры
            // Предполагаем, что gRPC метод authenticate возвращает AuthenticateResponse с полями success, errorMessage, jwtToken, userId
            val response = getClient().authenticate(nickname, challengeResponse, signature) // Пример вызова

            return AuthenticationResponseDto(
                success = response.success,
                errorMessage = response.errorMessage.takeIf { it.isNotEmpty() },
                jwtToken = response.token.takeIf { it.isNotEmpty() },
                userId = response.userId.takeIf { it.isNotEmpty() }
            )
        } catch (e: StatusRuntimeException) {
            throw mapGrpcError(e)
        } catch (e: Exception) {
            throw IOException("Network error during authenticate", e)
        }
    }

    override suspend fun updateFcmToken(fcmToken: String): UpdateTokenResponseDto { // <--- НОВЫЙ МЕТОД
        try {
            val request = UpdateTokenRequest.newBuilder()
                .setFcmToken(fcmToken)
                .build()
            val response = getClient().updateFcmToken(fcmToken)
            return UpdateTokenResponseDto(
                success = response.success,
                errorMessage = response.errorMessage.takeIf { it.isNotEmpty() }
            )
        } catch (e: StatusRuntimeException) {
            throw mapGrpcError(e)
        } catch (e: Exception) {
            throw IOException("Network error during updateFcmToken: ${e.message}", e)
        }
    }

    // Пример маппера gRPC ошибок
    private fun mapGrpcError(e: StatusRuntimeException): AuthenticationError {
        return when (e.status.code) {
            io.grpc.Status.Code.ALREADY_EXISTS -> AuthenticationError.NicknameTakenException("Nickname already taken", null) // Пример кастомного исключения
            io.grpc.Status.Code.UNAUTHENTICATED -> AuthenticationError.AuthenticationFailedException(
                "Authentication failed",
                null
            )
            io.grpc.Status.Code.UNAVAILABLE -> AuthenticationError.ConnectionException(
                "Server unavailable",
                e
            )
            // ... другие коды gRPC
            else -> AuthenticationError.GrpcException("gRPC error: ${e.status.code}", e)
        }
    }
}
