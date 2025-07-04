package ru.drsn.waves.data.datasource.remote.grpc.authentication


import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import ru.drsn.waves.BuildConfig
import gRPC.v1.Authentication.*
import ru.drsn.waves.data.datasource.remote.grpc.AuthTokenInterceptor

class AuthenticationClient (
    serverAddress: String,
    serverPort: Int,
    private val authTokenInterceptor: AuthTokenInterceptor
) {
    private val channel: ManagedChannel = ManagedChannelBuilder
        .forAddress(serverAddress, serverPort)
        .apply {
            if (!BuildConfig.RELEASE) {
                usePlaintext()
            } else {
                useTransportSecurity()
            }
        }
        .intercept(authTokenInterceptor)
        .build()

    private val stub = AuthorisationGrpcKt.AuthorisationCoroutineStub(channel)

    suspend fun reserveNickname(nickname: String) : ReserveNicknameResponse {
        val request = ReserveNicknameRequest.newBuilder()
            .setNickname(nickname)
            .build()

        val response: ReserveNicknameResponse = stub.reserveNickname(request)

        return response
    }

    suspend fun getChallenge(nickname: String) : ChallengeResponse {
        val request = ChallengeRequest.newBuilder()
            .setUsername(nickname)
            .build()

        val response: ChallengeResponse = stub.requestChallenge(request)

        return response
    }

    suspend fun authenticate(nickname: String, challengeResponse: ChallengeResponse, signature: ByteString) : AuthenticationResponse {
        val request = AuthenticationRequest.newBuilder()
            .setUsername(nickname)
            .setSignature(signature)
            .setChallengeId(challengeResponse.challengeId)
            .build()

        val response = stub.authentication(request)

        return response
    }

    suspend fun register(nicknameToken: String, publicKey: ByteString): RegisterResponse {
        val request = RegisterRequest.newBuilder()
            .setReservationToken(nicknameToken)
            .setPublicKey(publicKey)
            .build()

        val response = stub.register(request)

        return response
    }

    suspend fun updateFcmToken(fcmToken: String) : UpdateTokenResponse{
        val request = UpdateTokenRequest.newBuilder()
            .setFcmToken(fcmToken)
            .build()

        val response = stub.updateFcmToken(request)

        return response
    }
}