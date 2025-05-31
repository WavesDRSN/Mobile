package ru.drsn.waves.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.usecase.authentication.AuthenticateUseCase
import ru.drsn.waves.domain.usecase.crypto.GetUserNicknameUseCase
import ru.drsn.waves.domain.usecase.crypto.SaveAuthTokenUseCase
import javax.inject.Inject
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.usecase.crypto.InitializeCryptoUseCase
import timber.log.Timber
@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val initializeCryptoUseCase: InitializeCryptoUseCase, // Заменен IsCryptoInitializedUseCase
    private val getUserNicknameUseCase: GetUserNicknameUseCase,
    private val authenticateUseCase: AuthenticateUseCase,
    private val saveAuthTokenUseCase: SaveAuthTokenUseCase
) : ViewModel() {

    private val _navigationCommand = MutableStateFlow<NavigationCommand?>(null)
    val navigationCommand: StateFlow<NavigationCommand?> = _navigationCommand.asStateFlow()

    fun decideNextScreen() {
        viewModelScope.launch {
            Timber.d("Launcher: Проверка состояния для навигации...")
            delay(500)

            when (val cryptoInitResult = initializeCryptoUseCase()) {
                is Result.Success -> {
                    // Ключи успешно загружены
                    Timber.d("Launcher: Криптография инициализирована/ключи загружены.")
                    // 2. Пытаемся получить никнейм
                    when (val nicknameResult = getUserNicknameUseCase()) {
                        is Result.Success -> {
                            val nickname = nicknameResult.value
                            Timber.d("Launcher: Никнейм (${nickname}) найден. Попытка авто-аутентификации...")
                            // 3. Пытаемся аутентифицироваться с загруженным никнеймом
                            when (val authResult = authenticateUseCase(nickname)) {
                                is Result.Success -> {
                                    val authToken = authResult.value
                                    Timber.i("Launcher: Авто-аутентификация успешна для ${nickname}.")
                                    saveAuthTokenUseCase(authToken)
                                    _navigationCommand.value = NavigationCommand.ToMainApp
                                }
                                is Result.Error -> {
                                    Timber.w("Launcher: Авто-аутентификация не удалась для ${nickname}: ${authResult.error}")
                                    _navigationCommand.value = NavigationCommand.ToWelcome(
                                        showError = true,
                                        errorMessage = "Автоматический вход не удался. Пожалуйста, проверьте данные или войдите/зарегистрируйтесь заново."
                                    )
                                }
                            }
                        }
                        is Result.Error -> {
                            // Никнейм не найден, но ключи есть. Это странная ситуация, возможно, стоит тоже на Welcome.
                            Timber.w("Launcher: Ключи есть, но никнейм не найден: ${nicknameResult.error}. Переход на Welcome.")
                            _navigationCommand.value = NavigationCommand.ToWelcome(showError = false)
                        }
                    }
                }
                is Result.Error -> {
                    // Ошибка инициализации криптографии
                    val cryptoError = cryptoInitResult.error
                    Timber.e("Launcher: Ошибка инициализации криптографии: $cryptoError")
                    when (cryptoError) {
                        is CryptoError.KeyNotFound -> {
                            // Ключи не найдены - это нормальный сценарий для первого запуска или после удаления данных
                            Timber.d("Launcher: Ключи не найдены. Переход на Welcome.")
                            _navigationCommand.value = NavigationCommand.ToWelcome(showError = false)
                        }
                        is CryptoError.LoadError -> {
                            // Ключи есть, но не удалось загрузить (повреждены?)
                            Timber.w("Launcher: Ошибка загрузки существующих ключей: ${cryptoError.message}. Переход на Welcome с предложением восстановления.")
                            _navigationCommand.value = NavigationCommand.ToWelcome(
                                showError = true,
                                errorMessage = "Ошибка загрузки ваших ключей. Возможно, данные повреждены. Попробуйте восстановить доступ с помощью вашей сид-фразы или зарегистрируйтесь заново."
                            )
                        }
                        else -> {
                            // Другие крипто-ошибки (InitializationError, Unknown и т.д.)
                            Timber.e("Launcher: Критическая ошибка криптографии. Переход на Welcome с сообщением об ошибке.")
                            _navigationCommand.value = NavigationCommand.ToWelcome(
                                showError = true,
                                errorMessage = "Произошла критическая ошибка (${cryptoError::class.simpleName}). Пожалуйста, попробуйте позже или свяжитесь с поддержкой."
                            )
                        }
                    }
                }
            }
        }
    }

    fun resetNavigation() {
        _navigationCommand.value = null
    }
}