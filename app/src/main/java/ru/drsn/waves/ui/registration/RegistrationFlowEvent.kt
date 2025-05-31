package ru.drsn.waves.ui.registration

sealed class RegistrationFlowEvent {
    data object NavigateToChatList : RegistrationFlowEvent()
}