package ru.drsn.waves.ui.chatlist

import ru.drsn.waves.domain.model.chat.DomainChatSession


sealed class ChatListUiState {
    data object Loading : ChatListUiState()
    data object MovedFromProfile: ChatListUiState()
    data class Success(val sessions: List<DomainChatSession>) : ChatListUiState()
    data class Error(val message: String) : ChatListUiState()
    data object Empty : ChatListUiState()
}