package ru.drsn.waves.authentication

import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import io.grpc.StatusRuntimeException
import ru.drsn.waves.crypto.CryptoService
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticationService : IAuthenticationService {
    private lateinit var authenticationService: AuthenticationClient

    @Inject lateinit var cryptoService: CryptoService

    private val connected = AtomicBoolean(false)

    private var nickname: String? = null
    private var reservationToken: String? = null
    private var expiresAtUnix: Long = 0

    override fun openConnection(serverAddress: String, serverPort: Int) {
        try {
            authenticationService = AuthenticationClient(serverAddress, serverPort)
            connected.set(true)
        } catch (e: IOException) {
            throw RuntimeException("Ошибка при подключении", e)
        }
    }

    override suspend fun reserveNickname(nickname: String) {
        try {
            val response = authenticationService.reserveNickname(nickname)
            reservationToken = response.reservationToken
            this.nickname = nickname
            expiresAtUnix = response.expiresAtUnix
        } catch (e: StatusRuntimeException) {
            throw RuntimeException("Никнейм уже занят", e)
        }
    }

    override suspend fun register(publicKey: ByteString) {
        if (reservationToken == null) throw RuntimeException("Сначала необходимо зарезервировать никнейм")

        if (System.currentTimeMillis() >= expiresAtUnix) {
            try {
                reserveNickname(nickname!!)
            } catch (e: RuntimeException) {
                throw RuntimeException("Время на регистрацию прошло и никнейм успели занять", e)
            }
        }

        val response = authenticationService.register(reservationToken!!, publicKey)

        if (!response.success) throw RuntimeException("Регистрация не удалась.\n${response.errorMessage}")
    }

    override suspend fun authenticate(nickname: String) {
        val challengeResponse = authenticationService.getChallenge(nickname)

        val signature = cryptoService.signData(challengeResponse.challenge.toByteArray())

        val authenticationResponse = authenticationService.authenticate(nickname, challengeResponse, signature.toByteString())

        if (authenticationResponse.success) return
        else throw RuntimeException("Ошибка авторизации\n${authenticationResponse.errorMessage}")
    }
}