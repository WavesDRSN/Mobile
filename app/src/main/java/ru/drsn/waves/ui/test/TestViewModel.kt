package ru.drsn.waves.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.drsn.waves.domain.model.authentication.AuthError
import ru.drsn.waves.domain.model.authentication.NicknameReservation
import ru.drsn.waves.domain.model.authentication.PublicKey
import ru.drsn.waves.domain.model.crypto.AuthToken
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.crypto.InitializationResult
import ru.drsn.waves.domain.model.signaling.SignalingError
import ru.drsn.waves.domain.model.signaling.SignalingEvent
import ru.drsn.waves.domain.model.signaling.SignalingUser
import ru.drsn.waves.domain.model.webrtc.PeerId
import ru.drsn.waves.domain.model.webrtc.WebRTCMessage
import ru.drsn.waves.domain.usecase.authentication.AuthenticateUseCase
import ru.drsn.waves.domain.usecase.authentication.RegisterUseCase
import ru.drsn.waves.domain.usecase.authentication.ReserveNicknameUseCase
import ru.drsn.waves.domain.usecase.crypto.DeleteAuthTokenUseCase
import ru.drsn.waves.domain.usecase.crypto.GetAuthTokenUseCase
import ru.drsn.waves.domain.usecase.crypto.GetPublicKeyUseCase
import ru.drsn.waves.domain.usecase.crypto.InitializeCryptoUseCase
import ru.drsn.waves.domain.usecase.crypto.SaveAuthTokenUseCase
import ru.drsn.waves.domain.usecase.signaling.ConnectToSignalingUseCase
import ru.drsn.waves.domain.usecase.signaling.DisconnectFromSignalingUseCase
import ru.drsn.waves.domain.usecase.signaling.ObserveSignalingEventsUseCase
import ru.drsn.waves.domain.usecase.webrtc.CloseAllWebRTCConnectionsUseCase
import ru.drsn.waves.domain.usecase.webrtc.InitializeWebRTCUseCase
import ru.drsn.waves.domain.usecase.webrtc.InitiateCallUseCase
import ru.drsn.waves.domain.usecase.webrtc.ObserveWebRTCEventsUseCase
import ru.drsn.waves.domain.usecase.webrtc.SendMessageWebRTCUseCase
import timber.log.Timber
import javax.inject.Inject


// --- ViewModel ---
@HiltViewModel
class TestViewModel @Inject constructor(
    private val initializeCryptoUseCase: InitializeCryptoUseCase,
    private val getPublicKeyUseCase: GetPublicKeyUseCase,
    private val reserveNicknameUseCase: ReserveNicknameUseCase,
    private val registerUseCase: RegisterUseCase,
    private val connectToSignalingUseCase: ConnectToSignalingUseCase,
    private val observeSignalingEventsUseCase: ObserveSignalingEventsUseCase,
    private val disconnectFromSignalingUseCase: DisconnectFromSignalingUseCase,
    private val initializeWebRTCUseCase: InitializeWebRTCUseCase, // Добавлен
    private val initiateCallUseCase: InitiateCallUseCase,
    private val observeWebRTCEventsUseCase: ObserveWebRTCEventsUseCase,
    private val sendMessageWebRTCUseCase: SendMessageWebRTCUseCase,
    private val closeAllWebRTCConnectionsUseCase: CloseAllWebRTCConnectionsUseCase,
    private val authenticateUseCase: AuthenticateUseCase, // Теперь возвращает Result<AuthToken, AuthError>
    private val saveAuthTokenUseCase: SaveAuthTokenUseCase,
    private val getAuthTokenUseCase: GetAuthTokenUseCase,
    private val deleteAuthTokenUseCase: DeleteAuthTokenUseCase

) : ViewModel() {

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    private val _signalingUsers = MutableStateFlow<List<SignalingUser>>(emptyList())
    val signalingUsers: StateFlow<List<SignalingUser>> = _signalingUsers.asStateFlow()

    private var currentPublicKey: PublicKey? = null
    private var currentNicknameReservation: NicknameReservation? = null

    init {
        addLog("ViewModel initialized.")
        initializeCrypto()
        initializeWebRTC() // Инициализируем WebRTC
        observeSignalingEvents()
        observeWebRTCEvents()
    }

    private fun addLog(message: String) {
        Timber.d("TEST_LOG: $message")
        _logMessages.value = (_logMessages.value + message).takeLast(100) // Храним последние 100 логов
    }

    private fun initializeCrypto() {
        viewModelScope.launch {
            addLog("Инициализация криптографии...")
            when (val result = initializeCryptoUseCase()) {
                is Result.Success -> {
                    when (val cryptoInitResult = result.value) {
                        is InitializationResult.KeysLoaded -> addLog("Крипто-ключи загружены.")
                        is InitializationResult.KeysGenerated -> addLog("Новые крипто-ключи сгенерированы. Мнемоника: ${cryptoInitResult.mnemonic.value} (СОХРАНИТЕ ЭТО!)")
                    }
                    // Получаем публичный ключ после инициализации
                    when (val pkResult = getPublicKeyUseCase()) {
                        is Result.Success -> {
                            currentPublicKey = pkResult.value
                            addLog("Публичный ключ получен (размер): ${currentPublicKey?.size()} байт.")
                        }
                        is Result.Error -> addLog("Ошибка получения публичного ключа: ${mapCryptoError(pkResult.error)}")
                    }
                }
                is Result.Error -> addLog("Ошибка инициализации криптографии: ${mapCryptoError(result.error)}")
            }
        }
    }

    private fun initializeWebRTC() {
        viewModelScope.launch {
            addLog("Инициализация WebRTC...")
            when(val result = initializeWebRTCUseCase()) {
                is Result.Success -> addLog("WebRTC успешно инициализирован.")
                is Result.Error -> addLog("Ошибка инициализации WebRTC: ${result.error}")
            }
        }
    }


    fun reserveNickname(nickname: String) {
        viewModelScope.launch {
            addLog("Резервирование никнейма: $nickname...")
            when (val result = reserveNicknameUseCase(nickname)) {
                is Result.Success -> {
                    currentNicknameReservation = result.value
                    addLog("Никнейм '$nickname' зарезервирован. Токен: ${result.value.reservationToken}, истекает: ${result.value.expiresAtUnix}")
                }
                is Result.Error -> addLog("Ошибка резервирования никнейма: ${mapAuthError(result.error)}")
            }
        }
    }

    fun registerUser() {
        val reservation = currentNicknameReservation
        val pk = currentPublicKey
        if (reservation == null) {
            addLog("Ошибка: Сначала зарезервируйте никнейм.")
            return
        }
        if (pk == null) {
            addLog("Ошибка: Публичный ключ не доступен.")
            return
        }
        // Проверка срока действия токена (упрощенная)
        if (System.currentTimeMillis() / 1000 >= reservation.expiresAtUnix) {
            addLog("Ошибка: Срок резервирования никнейма истек. Пожалуйста, зарезервируйте заново.")
            // Можно автоматически вызвать reserveNickname(reservation.nickname) здесь, если это нужно
            return
        }

        viewModelScope.launch {
            addLog("Регистрация пользователя с токеном ${reservation.reservationToken}...")
            when (val result = registerUseCase(reservation)) {
                is Result.Success -> addLog("Пользователь успешно зарегистрирован!")
                is Result.Error -> addLog("Ошибка регистрации: ${mapAuthError(result.error)}")
            }
        }
    }

    fun authenticateUser(nickname: String) {
        viewModelScope.launch {
            addLog("Аутентификация пользователя $nickname...")
            when (val result = authenticateUseCase(nickname)) {
                is Result.Success -> {
                    val jwtToken = result.value
                    addLog("Пользователь $nickname успешно аутентифицирован! JWT получен (длина: ${jwtToken.length}).")
                    // Сохраняем токен
                    saveToken(jwtToken)
                }
                is Result.Error -> addLog("Ошибка аутентификации: ${mapAuthError(result.error)}")
            }
        }
    }

    private fun saveToken(token: AuthToken) {
        viewModelScope.launch {
            addLog("Сохранение токена...")
            when (val saveResult = saveAuthTokenUseCase(token)) {
                is Result.Success -> addLog("Токен успешно сохранен.")
                // Обрати внимание, что тип ошибки здесь CryptoError
                is Result.Error -> addLog("Ошибка сохранения токена: ${mapCryptoError(saveResult.error)}")
            }
        }
    }

    fun checkSavedToken() {
        viewModelScope.launch {
            addLog("Проверка сохраненного токена...")
            when (val getResult = getAuthTokenUseCase()) {
                is Result.Success -> addLog("Сохраненный токен: ${getResult.value}")
                is Result.Error -> addLog("Ошибка получения токена: ${mapCryptoError(getResult.error)}") // Вероятно, CryptoError.KeyNotFound
            }
        }
    }


    fun connectToSignaling(username: String, host: String, portStr: String) {
        val port = portStr.toIntOrNull()
        if (port == null) {
            addLog("Неверный порт: $portStr")
            return
        }
        viewModelScope.launch {
            addLog("Подключение к серверу сигнализации $host:$port от имени $username...")
            when (val result = connectToSignalingUseCase(username, host, port)) {
                is Result.Success -> addLog("Команда подключения к сигнализации отправлена.") // Успех здесь означает, что команда отправлена
                is Result.Error -> addLog("Ошибка подключения к сигнализации: ${mapSignalingError(result.error)}")
            }
        }
    }

    private fun observeSignalingEvents() {
        observeSignalingEventsUseCase()
            .onEach { event ->
                addLog("Событие сигнализации: $event")
                when (event) {
                    is SignalingEvent.UserListUpdated -> _signalingUsers.value = event.users
                    // Дополнительная обработка других событий сигнализации при необходимости
                    else -> {}
                }
            }
            .launchIn(viewModelScope)
    }

    fun initiateCall(peerIdValue: String) {
        if (peerIdValue.isBlank()) {
            addLog("ID пира не может быть пустым для звонка.")
            return
        }
        val peerId = PeerId(peerIdValue)
        viewModelScope.launch {
            addLog("Инициация WebRTC звонка пиру: ${peerId.value}...")
            val result = initiateCallUseCase(peerId)
            if (result is Result.Success) {
                addLog("Команда инициации звонка пиру ${peerId.value} отправлена.")
            } else if (result is Result.Error) {
                addLog("Ошибка инициации звонка пиру ${peerId.value}: ${result.error}")
            }
        }
    }

    private fun observeWebRTCEvents() {
        observeWebRTCEventsUseCase()
            .onEach { event ->
                addLog("Событие WebRTC: $event")
                // Здесь можно обновлять UI в зависимости от WebRTC событий
            }
            .launchIn(viewModelScope)
    }

    fun sendWebRTCMessage(targetPeerId: String, messageContent: String) {
        if (targetPeerId.isBlank() || messageContent.isBlank()) {
            addLog("ID пира и сообщение не могут быть пустыми.")
            return
        }
        val message = WebRTCMessage(PeerId(targetPeerId), messageContent)
        viewModelScope.launch {
            addLog("Отправка WebRTC сообщения пиру ${targetPeerId}: \"$messageContent\"...")
            val result = sendMessageWebRTCUseCase(message)
            if (result is Result.Success) {
                addLog("WebRTC сообщение отправлено пиру ${targetPeerId}.")
            } else if (result is Result.Error) {
                addLog("Ошибка отправки WebRTC сообщения пиру ${targetPeerId}: ${result.error}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        addLog("ViewModel cleared. Отключение от сигнализации и закрытие WebRTC соединений...")
        viewModelScope.launch {
            disconnectFromSignalingUseCase()
            closeAllWebRTCConnectionsUseCase() // Закрываем все WebRTC соединения
            addLog("Отключено и очищено.")
        }
    }

    // Вспомогательные функции для маппинга ошибок (упрощенные)
    private fun mapAuthError(error: AuthError): String = "AuthError: $error"
    private fun mapCryptoError(error: CryptoError): String = "CryptoError: $error"
    private fun mapSignalingError(error: SignalingError): String = "SignalingError: $error"
}
