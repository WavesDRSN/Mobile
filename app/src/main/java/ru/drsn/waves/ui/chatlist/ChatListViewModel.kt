package ru.drsn.waves.ui.chatlist

import android.content.Context // <-- Import Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext // <-- Import ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.chat.ChatType
import ru.drsn.waves.domain.model.chat.DomainChatSession
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.profile.DomainUserProfile
import ru.drsn.waves.domain.usecase.chatlist.ObserveChatSessionsUseCase
import ru.drsn.waves.domain.usecase.chatlist.GetOrCreateChatSessionForNavUseCase
import ru.drsn.waves.domain.usecase.crypto.GetUserNicknameUseCase
import timber.log.Timber
import javax.inject.Inject
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.usecase.fcm.RegisterFcmTokenUseCase // <-- Import RegisterFcmTokenUseCase
import ru.drsn.waves.domain.usecase.profile.LoadUserProfileUseCase
import ru.drsn.waves.domain.usecase.signaling.ConnectToSignalingUseCase
import ru.drsn.waves.domain.usecase.signaling.GetActiveUsers
import ru.drsn.waves.domain.usecase.webrtc.InitializeWebRTCUseCase
import ru.drsn.waves.services.fcm.MyFirebaseMessagingService // <-- Import for SharedPreferences constants

@HiltViewModel
class ChatListViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context, // <-- Inject Context
    private val observeChatSessionsUseCase: ObserveChatSessionsUseCase,
    private val getOrCreateChatSessionForNavUseCase: GetOrCreateChatSessionForNavUseCase,
    private val getCurrentUsernameUseCase: GetUserNicknameUseCase,
    private val connectToSignalingUseCase: ConnectToSignalingUseCase,
    private val initializeWebRTCUseCase: InitializeWebRTCUseCase,
    private val getActiveUsers: GetActiveUsers,
    private val loadUserProfileUseCase: LoadUserProfileUseCase,
    private val registerFcmTokenUseCase: RegisterFcmTokenUseCase // <-- Inject RegisterFcmTokenUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private var currentUsername: String? = null

    init {
        Timber.d("ChatListViewModel инициализирована")
        viewModelScope.launch {
            val result = getCurrentUsernameUseCase()
            if (result is Result.Success) {
                currentUsername = result.value
                Timber.i("ChatListViewModel: Пользователь '${currentUsername}' определен.")

                // Continue with other initializations that depend on username
                connectToSignalingUseCase(currentUsername!!, "10.0.2.2", 50051) // currentUsername is now guaranteed non-null here
                initializeWebRTCUseCase()
                currentUsername = result.value

                // --- TRIGGER FCM TOKEN REGISTRATION ---
                val fcmToken = getFcmTokenFromPrefs(applicationContext)
                if (fcmToken.isNullOrEmpty()) {
                    Timber.w("ChatListViewModel init: FCM токен не найден в SharedPreferences. Регистрация отложена/пропущена.")
                } else {
                    Timber.i("ChatListViewModel init: Попытка регистрации FCM токена: ${fcmToken.takeLast(10)}...")
                    // Assuming your RegisterFcmTokenUseCase now returns a Result
                    registerFcmTokenUseCase.execute(fcmToken)
                }
                // --- END FCM TOKEN REGISTRATION ---


            }
            else {
                _showToastEvent.emit("Подключение к серверу не удалось.")
            }
        }
        observeChatSessions()
    }

    private fun getFcmTokenFromPrefs(context: Context): String? {
        val prefs = context.getSharedPreferences(MyFirebaseMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(MyFirebaseMessagingService.KEY_FCM_TOKEN, null)
    }

    private fun observeChatSessions() {
        observeChatSessionsUseCase()
            .onEach { sessions ->
                Timber.d("Получено ${sessions.size} сессий чатов")
                if (sessions.isEmpty()) {
                    _uiState.value = ChatListUiState.Empty
                } else {
                    _uiState.value = ChatListUiState.Success(sessions)
                }
            }
            .catch { e ->
                Timber.e(e, "Ошибка при наблюдении за сессиями чатов")
                _uiState.value = ChatListUiState.Error("Ошибка загрузки списка чатов: ${e.message}")
            }
            .launchIn(viewModelScope)
    }

    fun onChatSessionClicked(session: DomainChatSession) {
        Timber.i("Нажата сессия чата: ${session.peerName} (ID: ${session.sessionId})")
        // Здесь можно добавить логику, например, убедиться, что сессия существует
        // Для простоты, пока просто инициируем событие навигации.
        viewModelScope.launch {
            // Можно вызвать getOrCreateChatSessionForNavUseCase, если нужно обновить/создать сессию перед переходом
            // val result = getOrCreateChatSessionForNavUseCase(session.sessionId, session.peerName, session.chatType, session.participantIds)
            // if (result is Result.Success) {
            //    _navigateToChatEvent.emit(result.value) // Отправляем обновленную сессию
            // } else {
            //    _uiState.value = ChatListUiState.Error("Не удалось открыть чат: ${(result as Result.Error).error}")
            // }
            _navigateToChatEvent.emit(session)
        }
    }

    fun onCreateChatClicked(peerNickname: String) {
        if (peerNickname.isBlank()) {
            viewModelScope.launch {
                _showToastEvent.emit("Никнейм пользователя не может быть пустым.")
            }
            return
        }
        if (peerNickname == currentUsername) {
            viewModelScope.launch {
                _showToastEvent.emit("Нельзя создать чат с самим собой.")
            }
            return
        }

        Timber.i("Запрос на создание/открытие чата с: $peerNickname")
        viewModelScope.launch {

            if (!(getActiveUsers() as Result.Success).value.contains(peerNickname)) {
                viewModelScope.launch {
                    _showToastEvent.emit("Сейчас нельзя создать чат с пользователем который оффлайн.")
                //TODO: запретить создавать с пользователем которого нет, а не который онлайн
                }
                return@launch
            }

            // Предполагаем, что это личный чат
            val result = getOrCreateChatSessionForNavUseCase(
                peerId = peerNickname,
                peerName = peerNickname, // Имя пока такое же, как ID
                chatType = ChatType.PEER_TO_PEER,
                participantIds = listOf(peerNickname) // Для P2P чата участник - это сам peerId
            )
            when (result) {
                is Result.Success -> {
                    _navigateToChatEvent.emit(result.value)
                }
                is Result.Error -> {
                    val errorMsg = when (result.error) {
                        is ChatError.NotFound -> "Пользователь с ником '$peerNickname' не найден." // Пример
                        else -> "Не удалось создать или открыть чат: ${result.error}"
                    }
                    _showToastEvent.emit(errorMsg)
                    Timber.e("Ошибка создания/открытия чата с $peerNickname: ${result.error}")
                }
            }
        }
    }

    private val _navigateToChatEvent = MutableSharedFlow<DomainChatSession>()
    val navigateToChatEvent: Flow<DomainChatSession> = _navigateToChatEvent.asSharedFlow()

    private val _showToastEvent = MutableSharedFlow<String>()
    val showToastEvent: Flow<String> = _showToastEvent.asSharedFlow()

    fun getCurrentUsername(): String? = currentUsername

    suspend fun getProfileDomainModel(): DomainUserProfile? {
        val loadProfileResult = loadUserProfileUseCase()

        if (loadProfileResult is Result.Success) return loadProfileResult.value
        else if (loadProfileResult is Result.Error) {
            _uiState.emit(ChatListUiState.Error("Не удалось загрузить информацию профиля.\n${loadProfileResult.error}"))
        }
        return null
    }

    fun onSearchQueryChanged(query: String) {
        // TODO: Реализовать логику поиска
        Timber.d("Поисковый запрос: $query")
    }
}