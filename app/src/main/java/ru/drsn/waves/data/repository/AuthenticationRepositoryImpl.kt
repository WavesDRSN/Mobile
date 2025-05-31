package ru.drsn.waves.data.repository

import gRPC.v1.Authentication.ChallengeResponse
import ru.drsn.waves.data.datasource.remote.grpc.authentication.AuthenticationError.*
import ru.drsn.waves.data.datasource.remote.grpc.authentication.IAuthenticationRemoteDataSource
import ru.drsn.waves.domain.model.authentication.*
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IAuthenticationRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticationRepositoryImpl @Inject constructor(
    private val remoteDataSource: IAuthenticationRemoteDataSource // Инжектим интерфейс
) : IAuthenticationRepository {

    // Явное управление соединением уходит из репозитория.
    // DataSource должен сам управлять своим состоянием или принимать параметры соединения.
    // Если нужно явное открытие/закрытие - сделать UseCase для этого, который дергает DataSource.


    init {
        if (!remoteDataSource.isConnected()) remoteDataSource.setupConnection("tt.vld.su", 50051)
    }

    override suspend fun reserveNickname(nickname: String): Result<NicknameReservation, AuthError> {
        return try {
            // Убедимся, что соединение установлено (DataSource может делать это неявно)
            // remoteDataSource.ensureConnected() // Пример

            val dto = remoteDataSource.reserveNickname(nickname)
            // Маппинг DTO -> Domain Model
            Result.Success(NicknameReservation(dto.reservationToken, dto.expiresAtUnix, nickname))
        } catch (e: NicknameTakenException) {
            Result.Error(AuthError.NicknameTaken)
        } catch (e: ConnectionException) {
            Result.Error(AuthError.ConnectionError)
        } catch (e: IOException) { // Общая сетевая ошибка
            Result.Error(AuthError.ConnectionError)
        } catch (e: Exception) {
            Result.Error(AuthError.Unknown("Failed to reserve nickname", e))
        }
    }

    override suspend fun register(reservationToken: String, publicKey: PublicKey): Result<Unit, AuthError> {
        return try {
            val response = remoteDataSource.register(reservationToken, publicKey)
            if (response.success) {
                Result.Success(Unit)
            } else {
                Result.Error(AuthError.RegistrationFailed(response.errorMessage))
            }
        } catch (e: ConnectionException) {
            Result.Error(AuthError.ConnectionError)
        } catch (e: IOException) {
            Result.Error(AuthError.ConnectionError)
        } catch (e: Exception) {
            Result.Error(AuthError.Unknown("Failed to register", e))
        }
    }

    override suspend fun getChallenge(nickname: String): Result<ChallengeResponse, AuthError> {
        return try {
            val response = remoteDataSource.getChallenge(nickname)
            Result.Success(response)
        } catch (e: ConnectionException) {
            Result.Error(AuthError.ConnectionError)
        } catch (e: IOException) {
            Result.Error(AuthError.ConnectionError)
        } catch (e: Exception) {
            Result.Error(AuthError.Unknown("Failed to get challenge", e))
        }
    }

    override suspend fun verifyAuthentication(nickname: String, challengeResponse: ChallengeResponse, signature: Signature): Result<String, AuthError> {
        return try {
            // Тут нужно передать в DataSource необходимые данные. Возможно, ему нужен challenge DTO?
            // Адаптируй под свой remoteDataSource.authenticate(...)
            // Предположим, ему нужен challenge в виде ByteString и signature
            // val challengeDto = ChallengeResponseDto(challenge.toByteString()) // Пример

            val authResponse = remoteDataSource.authenticate(nickname, challengeResponse, signature)

            return if (authResponse.success && authResponse.jwtToken != null) {
                Result.Success(authResponse.jwtToken) // Возвращаем JWT
            } else {
                Result.Error(AuthError.AuthenticationFailed(authResponse.errorMessage))
            }
        } catch (e: ConnectionException) {
            Result.Error(AuthError.ConnectionError)
        } catch (e: IOException) {
            Result.Error(AuthError.ConnectionError)
        } catch (e: Exception) {
            Result.Error(AuthError.Unknown("Failed to verify authentication", e))
        }
    }
}