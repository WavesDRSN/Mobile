package ru.drsn.waves.ui.profile

import ru.drsn.waves.domain.model.profile.DomainUserProfile

sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Success (val profile: DomainUserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}