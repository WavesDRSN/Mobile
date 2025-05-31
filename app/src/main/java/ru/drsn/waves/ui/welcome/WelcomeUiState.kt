package ru.drsn.waves.ui.welcome

sealed class WelcomeUiState {
    data object Initial : WelcomeUiState() // Начальное состояние
    data class ShowAuthFailureDialog(val message: String) : WelcomeUiState() // Показать диалог ошибки от Launcher
    // Можно добавить Loading, если WelcomeViewModel будет выполнять какие-то асинхронные операции сама
}