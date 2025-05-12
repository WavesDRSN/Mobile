package ru.drsn.waves.data.datasource.remote.grpc.signaling

import gRPC.v1.Signaling.IceCandidatesMessage
import gRPC.v1.Signaling.InitialUserConnectionResponse
import gRPC.v1.Signaling.SessionDescription
import gRPC.v1.Signaling.UsersList
import ru.drsn.waves.domain.model.signaling.SignalingError

sealed class SignalingServerEvent {
    data class InitialResponse(val response: InitialUserConnectionResponse) : SignalingServerEvent()
    data class UsersListReceived(val usersList: UsersList) : SignalingServerEvent()
    data class SdpMessageReceived(val sdpMessage: SessionDescription) : SignalingServerEvent()
    data class IceCandidatesReceived(val iceCandidatesMessage: IceCandidatesMessage) : SignalingServerEvent()
    data class SdpStreamStatus(val approved: Boolean) : SignalingServerEvent()
    data class IceStreamStatus(val approved: Boolean) : SignalingServerEvent()
    data object ConnectionEstablished : SignalingServerEvent() // Соединение успешно установлено и инициализировано
    data class ErrorOccurred(val error: SignalingError) : SignalingServerEvent()
    data object StreamEnded : SignalingServerEvent() // Основной стрим завершился
}