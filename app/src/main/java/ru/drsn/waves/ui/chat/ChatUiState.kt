package ru.drsn.waves.ui.chat

import ru.drsn.waves.domain.model.chat.DomainChatSession
import ru.drsn.waves.domain.model.chat.DomainMessage

sealed class ChatUiState {
    data object Loading : ChatUiState()
    data class Success(
        val messages: List<DomainMessage>,
        val chatSessionDetails: DomainChatSession? // Информация о чате для Toolbar
    ) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}