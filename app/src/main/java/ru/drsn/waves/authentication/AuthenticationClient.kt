package ru.drsn.waves.authentication

import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import ru.drsn.waves.BuildConfig
import gRPC.v1.Authentication.*

class AuthenticationClient (
    serverAddress: String,
    serverPort: Int
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
        .build()

    private val stub = AuthorisationGrpcKt.AuthorisationCoroutineStub(channel)

    suspend fun reserveNickname(nickname: String) : String {
        val request = ReserveNicknameRequest.newBuilder()
            .setNickname(nickname)
            .build()

        val response: ReserveNicknameResponse = stub.reserveNickname(request)

        return response.reservationToken
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

}