package ru.drsn.waves.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    // Сюда Hilt будет внедрять ваши зависимости (UseCases, Repositories и т.д.)
    // private val someUseCase: SomeUseCase
) : ViewModel() {

    // StateFlow для хранения и предоставления состояния UI
    private val _uiState = MutableStateFlow<WelcomeUiState>(WelcomeUiState.Initial)
    val uiState = _uiState.asStateFlow() // Activity подписывается на этот Flow

    // SharedFlow для отправки одноразовых событий в Activity
    private val _event = MutableSharedFlow<WelcomeEvent>()
    val event = _event.asSharedFlow() // Activity подписывается на этот Flow

    fun onLoginClicked() {
        // Здесь может быть логика (валидация, вызов use case и т.д.)
        viewModelScope.launch {
            // Отправляем событие для навигации
            _event.emit(WelcomeEvent.NavigateToLogin)
        }
    }

    fun onRegistrationClicked() {
        viewModelScope.launch {
            _event.emit(WelcomeEvent.NavigateToRegistration)
        }
    }

    /**
     * Обрабатывает данные, переданные из LauncherActivity.
     * @param showError Показать ли ошибку.
     * @param errorMessage Сообщение об ошибке.
     */
    fun processLaunchError(showError: Boolean, errorMessage: String?) {
        if (showError && errorMessage != null) {
            _uiState.value = WelcomeUiState.ShowAuthFailureDialog(errorMessage)
        } else {
            _uiState.value = WelcomeUiState.Initial // Если ошибки нет, просто начальное состояние
        }
    }

    /**
     * Вызывается, когда диалог ошибки был закрыт пользователем.
     */
    fun onErrorDialogDismissed() {
        _uiState.value = WelcomeUiState.Initial // Возвращаемся в начальное состояние
    }

    // Можно добавить другие функции для обработки действий пользователя
    // или для загрузки данных при инициализации ViewModel (в init блоке)
}