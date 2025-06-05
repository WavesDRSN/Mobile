package ru.drsn.waves.ui.chat.info

import ru.drsn.waves.domain.model.profile.DomainUserProfile

sealed class ChatInfoUiState {
    data object Loading : ChatInfoUiState()
    data class Success (val profile: DomainUserProfile) : ChatInfoUiState()
    data class Error(val message: String) : ChatInfoUiState()
}