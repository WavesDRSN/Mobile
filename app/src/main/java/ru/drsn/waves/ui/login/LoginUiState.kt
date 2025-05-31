package ru.drsn.waves.ui.login

sealed class LoginUiState {
    data object InfoEntryStep : LoginUiState()
    data object Loading : LoginUiState() // Общее состояние загрузки
    data class Error(val message: String, val step: NavigationCommand? = null) : LoginUiState() // Ошибка на каком-либо шаге
}