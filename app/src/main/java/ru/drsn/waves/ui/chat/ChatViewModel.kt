package ru.drsn.waves.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.drsn.waves.R
import ru.drsn.waves.databinding.ActivityChatBinding
import ru.drsn.waves.domain.model.utils.Result // Общий Result
import ru.drsn.waves.domain.model.chat.*
import ru.drsn.waves.domain.usecase.chat.*
import ru.drsn.waves.domain.usecase.crypto.GetUserNicknameUseCase
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle, // Для получения параметров навигации
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val sendMessageUseCase: SendTextMessageUseCase, // Пока только текстовые
    private val getOrCreateChatSessionUseCase: GetOrCreateChatSessionUseCase,
    val getUserNicknameUseCase: GetUserNicknameUseCase,
    private val markMessagesAsReadUseCase: MarkMessagesAsReadUseCase
    // private val loadMoreMessagesUseCase: LoadMoreMessagesUseCase // Для пагинации
) : ViewModel() {

    val sessionId: String = savedStateHandle.get<String>("session_id") ?: error("Session ID не передан в ChatViewModel")
    val peerNameFromArgs: String? = savedStateHandle.get<String>("peer_name") // Может быть null, если это новый чат
    val chatTypeFromArgs: ChatType = savedStateHandle.get<String>("chat_type")
        ?.let { ChatType.valueOf(it) } ?: ChatType.PEER_TO_PEER // По умолчанию личный чат

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _currentMessageInput = MutableStateFlow("")
    val currentMessageInput: StateFlow<String> = _currentMessageInput.asStateFlow()

    private var currentUserId: String? = null

    init {
        Timber.d("ChatViewModel инициализирована для сессии: $sessionId, имя: $peerNameFromArgs, тип: $chatTypeFromArgs")
        viewModelScope.launch {
            currentUserId = (getUserNicknameUseCase() as? Result.Success)?.value!!
            if (currentUserId == null) {
                _uiState.value = ChatUiState.Error("Не удалось определить текущего пользователя.")
                return@launch
            }
            loadChatDetailsAndMessages()
        }
    }

    private fun loadChatDetailsAndMessages() {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading
            // Получаем или создаем сессию чата для получения актуального имени и деталей
            // Для группового чата participantIds нужно будет передавать или получать отдельно
            val participantIds = if (chatTypeFromArgs == ChatType.PEER_TO_PEER) listOf(sessionId) else listOf(sessionId, currentUserId ?: "") // Упрощенно
            val sessionResult = getOrCreateChatSessionUseCase(sessionId, peerNameFromArgs ?: sessionId, chatTypeFromArgs, participantIds.distinct())

            if (sessionResult is Result.Success) {
                val sessionDetails = sessionResult.value
                observeMessagesUseCase(sessionId)
                    .onStart { Timber.d("Начало наблюдения за сообщениями для $sessionId") }
                    .catch { e ->
                        Timber.e(e, "Ошибка при наблюдении за сообщениями для $sessionId")
                        _uiState.value = ChatUiState.Error("Ошибка загрузки сообщений: ${e.message}")
                    }
                    .collect { messages ->
                        Timber.d("Получено ${messages.size} сообщений для $sessionId")
                        _uiState.value = ChatUiState.Success(messages, sessionDetails)
                        // Помечаем сообщения как прочитанные при загрузке/обновлении
                        markMessagesAsReadUseCase(sessionId)
                    }
            } else if (sessionResult is Result.Error) {
                _uiState.value = ChatUiState.Error("Не удалось загрузить детали чата: ${sessionResult.error}")
            }
        }
    }

    fun onMessageInputChanged(text: String) {
        _currentMessageInput.value = text
    }

    fun sendMessage() {
        val textToSend = _currentMessageInput.value.trim()
        if (textToSend.isEmpty()) {
            return
        }
        if (currentUserId == null) {
            Timber.e("Невозможно отправить сообщение: ID текущего пользователя неизвестен.")
            // Можно показать ошибку пользователю
            return
        }

        Timber.d("Отправка сообщения: '$textToSend' в сессию $sessionId")
        viewModelScope.launch {
            // Оптимистичное обновление (можно добавить временное сообщение в список с статусом SENDING)
            // val tempMessage = DomainMessage(...)
            // _uiState.update { if (it is ChatUiState.Success) it.copy(messages = it.messages + tempMessage) else it }

            _currentMessageInput.value = "" // Очищаем поле ввода

            when (val result = sendMessageUseCase(sessionId, textToSend)) {
                is Result.Success -> {
                    Timber.i("Сообщение отправлено (локально сохранено): ${result.value.messageId}")
                    // Список обновится через Flow от observeMessagesUseCase
                }
                is Result.Error -> {
                    Timber.e("Ошибка отправки сообщения: ${result.error}")
                    // TODO: Показать ошибку пользователю
                    _currentMessageInput.value = textToSend // Вернуть текст
                    // Можно обновить статус временного сообщения на FAILED
                }
            }
        }
    }
}