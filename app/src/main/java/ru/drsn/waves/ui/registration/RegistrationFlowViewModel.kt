package ru.drsn.waves.ui.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.ByteString // Для PublicKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.drsn.waves.domain.model.authentication.AuthError
import ru.drsn.waves.domain.model.authentication.NicknameReservation
import ru.drsn.waves.domain.model.utils.Result // Общий Result
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.crypto.InitializationResult
import ru.drsn.waves.domain.model.crypto.MnemonicPhrase
import ru.drsn.waves.domain.model.crypto.UserNickname
import ru.drsn.waves.domain.usecase.authentication.AuthenticateUseCase
import ru.drsn.waves.domain.usecase.authentication.RegisterUseCase
import ru.drsn.waves.domain.usecase.authentication.ReserveNicknameUseCase
import ru.drsn.waves.domain.usecase.crypto.GenerateNewKeysUseCase
import ru.drsn.waves.domain.usecase.crypto.GetPublicKeyUseCase
import ru.drsn.waves.domain.usecase.crypto.SaveAuthTokenUseCase
import ru.drsn.waves.domain.usecase.crypto.SaveUserNicknameUseCase
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RegistrationFlowViewModel @Inject constructor(
    private val generateNewKeysUseCase: GenerateNewKeysUseCase,
    private val getPublicKeyUseCase: GetPublicKeyUseCase,
    private val reserveNicknameUseCase: ReserveNicknameUseCase,
    private val registerUseCase: RegisterUseCase,
    private val saveUserNicknameUseCase: SaveUserNicknameUseCase,
    private val saveAuthTokenUseCase: SaveAuthTokenUseCase,
    private val authenticateUseCase: AuthenticateUseCase // Для аутентификации после регистрации
) : ViewModel() {

    private val _uiState = MutableStateFlow<RegistrationFlowUiState>(RegistrationFlowUiState.NicknameEntryStep)
    val uiState: StateFlow<RegistrationFlowUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<RegistrationFlowEvent>()
    val event: SharedFlow<RegistrationFlowEvent> = _event.asSharedFlow()

    // Временное хранение данных между шагами
    private var currentMnemonic: MnemonicPhrase? = null
    private var currentNickname: String? = null
    private var currentPublicKey: ByteString? = null
    private var currentReservationToken: NicknameReservation? = null

    // --- Шаг 1: Ввод и резервирование никнейма ---
    fun onNicknameEntered(nickname: String) {
        if (nickname.isBlank()) {
            _uiState.value = RegistrationFlowUiState.Error("Никнейм не может быть пустым", RegistrationStep.NICKNAME_RESERVATION)
            return
        }
        this.currentNickname = nickname
        viewModelScope.launch {
            _uiState.value = RegistrationFlowUiState.Loading
            when (val result = reserveNicknameUseCase(nickname)) {
                is Result.Success -> {
                    currentReservationToken = result.value
                    Timber.d("Никнейм '$nickname' зарезервирован, токен: $currentReservationToken")
                    generateKeysAndProceedToMnemonic()
                }
                is Result.Error -> {
                    val errorMsg = when (result.error) {
                        AuthError.NicknameTaken -> "Этот никнейм уже занят. Пожалуйста, выберите другой."
                        else -> "Ошибка резервирования никнейма: ${result.error}"
                    }
                    _uiState.value = RegistrationFlowUiState.Error(errorMsg, RegistrationStep.NICKNAME_RESERVATION)
                }
            }
        }
    }

    // --- Шаг 2: Генерация ключей и отображение мнемоники ---
    private fun generateKeysAndProceedToMnemonic() {
        viewModelScope.launch {
            // _uiState.value = RegistrationFlowUiState.Loading // Уже в Loading после резервирования
            when (val result = generateNewKeysUseCase()) { // Этот UseCase генерирует и сохраняет ключи
                is Result.Success -> {
                    currentMnemonic = result.value.mnemonic
                    Timber.d("Ключи сгенерированы, мнемоника: ${currentMnemonic?.value}")
                    // Получаем публичный ключ для последующей регистрации
                    when (val pkResult = getPublicKeyUseCase()) {
                        is Result.Success -> {
                            currentPublicKey = pkResult.value
                            _uiState.value = RegistrationFlowUiState.MnemonicDisplayStep(currentMnemonic!!)
                        }
                        is Result.Error -> {
                            _uiState.value = RegistrationFlowUiState.Error("Ошибка получения публичного ключа: ${pkResult.error}", RegistrationStep.KEY_GENERATION)
                        }
                    }
                }
                is Result.Error -> {
                    _uiState.value = RegistrationFlowUiState.Error("Ошибка генерации ключей: ${result.error}", RegistrationStep.KEY_GENERATION)
                }
            }
        }
    }

    // --- Шаг 3: Переход к проверке мнемоники ---
    fun onProceedToMnemonicVerification() {
        currentMnemonic?.let {
            _uiState.value = RegistrationFlowUiState.MnemonicVerificationStep(it.value.split(" "))
        } ?: run {
            _uiState.value = RegistrationFlowUiState.Error("Ошибка: мнемоническая фраза отсутствует.", null)
            // Вернуться на предыдущий шаг или показать ошибку
        }
    }

    // --- Шаг 4: Проверка мнемоники и финальная регистрация ---
    fun onVerifyMnemonicAndRegister(enteredWords: List<String>) {
        val originalWords = currentMnemonic?.value?.split(" ")
        if (originalWords == null || currentNickname == null || currentPublicKey == null || currentReservationToken == null) {
            _uiState.value = RegistrationFlowUiState.Error("Ошибка: необходимые данные для регистрации отсутствуют.", RegistrationStep.FINAL_REGISTRATION)
            return
        }

        // Простая проверка первых N слов (в твоем дизайне - 3 слова)
        val wordsToVerify = originalWords.take(enteredWords.size)
        if (enteredWords != wordsToVerify) {
            _uiState.value = RegistrationFlowUiState.Error("Введенные слова не совпадают с вашей фразой.", RegistrationStep.MNEMONIC_VERIFICATION)
            return
        }

        Timber.d("Мнемоника подтверждена. Регистрация пользователя $currentNickname...")
        viewModelScope.launch {
            _uiState.value = RegistrationFlowUiState.Loading
            when (val regResult = registerUseCase(currentReservationToken!!)) {
                is Result.Success -> {
                    Timber.i("Пользователь $currentNickname успешно зарегистрирован на сервере.")
                    // Сохраняем никнейм локально
                    saveUserNicknameUseCase(currentNickname!!) // Ошибки сохранения можно обработать
                    // Пытаемся сразу аутентифицироваться, чтобы получить JWT
                    authenticateAfterRegistration(currentNickname!!)
                }
                is Result.Error -> {
                    _uiState.value = RegistrationFlowUiState.Error("Ошибка регистрации на сервере: ${regResult.error}", RegistrationStep.FINAL_REGISTRATION)
                }
            }
        }
    }

    private fun authenticateAfterRegistration(nickname: String) {
        viewModelScope.launch {
            when (val authResult = authenticateUseCase(nickname)) {
                is Result.Success -> {
                    saveAuthTokenUseCase(authResult.value) // Сохраняем JWT
                    _uiState.value = RegistrationFlowUiState.RegistrationSuccessStep
                }
                is Result.Error -> {
                    // Регистрация прошла, но авто-логин нет. Это странно, но возможно.
                    // Можно показать экран успеха, но предупредить, что нужно будет войти.
                    // Или просто показать экран успеха, а пользователь войдет позже.
                    Timber.e("Авто-аутентификация после регистрации не удалась: ${authResult.error}")
                    _uiState.value = RegistrationFlowUiState.RegistrationSuccessStep // Все равно считаем регистрацию успешной
                }
            }
        }
    }


    // --- Шаг 5: Завершение регистрации ---
    fun onRegistrationCompleteAndNavigate() {
        viewModelScope.launch {
            _event.emit(RegistrationFlowEvent.NavigateToChatList)
        }
    }

    // Вызывается, если пользователь нажал "назад" на экране мнемоники или верификации
    fun returnToNicknameEntry() {
        currentMnemonic = null
        currentPublicKey = null
        // currentReservationToken и currentNickname остаются, чтобы не вводить заново, если это была ошибка верификации
        _uiState.value = RegistrationFlowUiState.NicknameEntryStep
    }
}
