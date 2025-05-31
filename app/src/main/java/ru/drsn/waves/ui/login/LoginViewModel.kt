package ru.drsn.waves.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.drsn.waves.domain.model.crypto.MnemonicPhrase
import ru.drsn.waves.domain.usecase.authentication.AuthenticateUseCase
import ru.drsn.waves.domain.usecase.crypto.DeleteAuthKeysUseCase
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.usecase.crypto.RegenerateKeysFromSeedUseCase
import ru.drsn.waves.domain.usecase.crypto.SaveAuthTokenUseCase
import ru.drsn.waves.domain.usecase.crypto.SaveUserNicknameUseCase
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val saveUserNicknameUseCase: SaveUserNicknameUseCase,
    private val regenerateKeysFromSeedUseCase: RegenerateKeysFromSeedUseCase,
    private val authenticateUseCase: AuthenticateUseCase,
    private val deleteAuthKeysUseCase: DeleteAuthKeysUseCase,
    private val saveAuthTokenUseCase: SaveAuthTokenUseCase
): ViewModel() {
    private val _navigationCommand = MutableStateFlow<NavigationCommand?>(null)
    val navigationCommand: StateFlow<NavigationCommand?> = _navigationCommand.asStateFlow()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.InfoEntryStep)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun authAttempt(nickname: String, seedPhrase: String) {

        Timber.d("pppppppppppp")

        viewModelScope.launch {
            if (nickname.isBlank()) {
                _uiState.emit(LoginUiState.Error("Имя пользователя не может быть пустым"))
                return@launch
            }

            if (seedPhrase.split(" ").size != 12) {
                _uiState.emit(LoginUiState.Error("Сид фраза должна быть длиной 12 слов"))
                return@launch
            }

            _uiState.emit(LoginUiState.Loading)
            val resultRegen = regenerateKeysFromSeedUseCase(MnemonicPhrase(seedPhrase))

            if (resultRegen is Result.Error) {
                _uiState.emit(LoginUiState.Error(resultRegen.error.toString()))
                return@launch
            }

            val resultAuth = authenticateUseCase(nickname)

            if (resultAuth is Result.Success) {
                saveAuthTokenUseCase(resultAuth.value)
                saveUserNicknameUseCase(nickname)
                _navigationCommand.emit(NavigationCommand.ToMainApp)

                Timber.i("$nickname logged successfully")
            } else {
                deleteAuthKeysUseCase()
                _uiState.emit(LoginUiState.Error((resultAuth as Result.Error).error.toString()))

                Timber.e("Unauthenticated for user $nickname")
            }
        }
    }
}