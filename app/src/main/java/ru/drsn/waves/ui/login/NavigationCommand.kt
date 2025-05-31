package ru.drsn.waves.ui.login

sealed class NavigationCommand {
    data object ToMainApp : NavigationCommand()
    data class ToWelcome(val showError: Boolean, val errorMessage: String? = null) : NavigationCommand()
}