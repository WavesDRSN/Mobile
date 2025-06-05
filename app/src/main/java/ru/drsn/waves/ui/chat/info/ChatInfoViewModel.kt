package ru.drsn.waves.ui.chat.info

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.drsn.waves.domain.model.utils.Result // Общий Result
import ru.drsn.waves.domain.model.chat.*
import ru.drsn.waves.domain.usecase.chat.*
import ru.drsn.waves.domain.usecase.crypto.GetUserNicknameUseCase
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle, // Для получения параметров навигации
    private val getSessionInfoUseCase: GetSessionInfoUseCase
) : ViewModel() {

    val sessionId: String = savedStateHandle.get<String>("session_id") ?: error("Session ID не передан в ChatViewModel")
    val chatTypeFromArgs: ChatType = savedStateHandle.get<String>("chat_type")
        ?.let { ChatType.valueOf(it) } ?: ChatType.PEER_TO_PEER // По умолчанию личный чат

    private val _uiState = MutableStateFlow<ChatInfoUiState>(ChatInfoUiState.Loading)
    val uiState: StateFlow<ChatInfoUiState> = _uiState.asStateFlow()

    init {
        Timber.d("ChatInfoViewModel инициализирована для сессии: $sessionId, тип: $chatTypeFromArgs")
        viewModelScope.launch {

            loadProfileInfo()
        }
    }

    private fun loadProfileInfo() {
        viewModelScope.launch {
            _uiState.value = ChatInfoUiState.Loading
            val sessionInfoResult = getSessionInfoUseCase(sessionId)
            // Получаем или создаем сессию чата для получения актуального имени и деталей
            // Для группового чата participantIds нужно будет передавать или получать отдельно

            if (sessionInfoResult is Result.Success) {
                _uiState.emit(ChatInfoUiState.Success(sessionInfoResult.value))
            } else if (sessionInfoResult is Result.Error) {
                _uiState.value = ChatInfoUiState.Error("Не удалось загрузить детали профиля: ${sessionInfoResult.error}")
            }
        }
    }

}