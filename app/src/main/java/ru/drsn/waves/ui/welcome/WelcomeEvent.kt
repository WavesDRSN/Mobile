package ru.drsn.waves.ui.welcome

sealed interface WelcomeEvent {
    object NavigateToLogin : WelcomeEvent
    object NavigateToRegistration : WelcomeEvent
}