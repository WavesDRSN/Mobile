package ru.drsn.waves.ui.profile.edit

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.impl.close
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.profile.DomainUserProfile
import ru.drsn.waves.domain.model.p2p.P2pMessageEnvelope
import ru.drsn.waves.domain.model.p2p.P2pMessageSerializer
import ru.drsn.waves.domain.model.p2p.P2pMessageType
import ru.drsn.waves.domain.model.p2p.UserProfilePayload
import ru.drsn.waves.domain.model.webrtc.PeerId
import ru.drsn.waves.domain.model.webrtc.WebRTCMessage
import ru.drsn.waves.domain.repository.IWebRTCRepository
import ru.drsn.waves.domain.usecase.profile.LoadUserProfileUseCase // Новый UseCase
import ru.drsn.waves.domain.usecase.profile.SaveUserProfileUseCase // Новый UseCase
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

data class EditProfileScreenState(
    val isLoading: Boolean = false,
    val currentUserId: String = "", // Никнейм
    val displayName: String = "",
    val statusMessage: String = "",
    val avatarUri: String? = null,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val loadUserProfileUseCase: LoadUserProfileUseCase,
    private val saveUserProfileUseCase: SaveUserProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileScreenState())
    val uiState: StateFlow<EditProfileScreenState> = _uiState.asStateFlow()

    init {
        loadInitialProfileData()
    }

    private fun loadInitialProfileData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val profileResult = loadUserProfileUseCase()) {
                is Result.Success -> {
                    val profile = profileResult.value
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUserId = profile.userId,
                            displayName = profile.displayName,
                            statusMessage = profile.statusMessage ?: "",
                            avatarUri = profile.avatarUri
                        )
                    }
                }
                is Result.Error -> {
                    Timber.e("Не удалось загрузить профиль: ${profileResult.error}")
                    // Если профиль не найден (например, первый запуск после установки/очистки),
                    // это может быть нормально. userId все равно должен быть (никнейм).
                    // Попробуем загрузить только никнейм, если профиль не найден.
                    // (Логика загрузки никнейма уже в loadUserProfileUseCase -> cryptoRepository)
                    _uiState.update {
                        val errorMsg = if (profileResult.error is ru.drsn.waves.domain.model.crypto.CryptoError.NicknameNotFound) {
                            "Профиль еще не создан. Заполните данные."
                        } else if (profileResult.error is ru.drsn.waves.domain.model.crypto.CryptoError.ProfileNotFound) {
                            // Это ожидаемо, если профиль еще не сохранялся. userId должен быть из никнейма.
                            // Попробуем вытащить userId из ошибки, если это возможно, или оставить пустым.
                            // Лучше, чтобы loadUserProfileUseCase возвращал userId даже если остальное пусто.
                            // Пока просто покажем общую ошибку или инициализируем с ником.
                            "Данные профиля не найдены. Заполните их."
                        }
                        else {
                            "Не удалось загрузить данные профиля: ${profileResult.error}"
                        }
                        it.copy(isLoading = false, error = errorMsg)
                    }
                }
            }
        }
    }

    fun onDisplayNameChanged(newName: String) {
        _uiState.update { it.copy(displayName = newName, error = null, saveSuccess = false) }
    }

    fun onStatusMessageChanged(newStatus: String) {
        _uiState.update { it.copy(statusMessage = newStatus, error = null, saveSuccess = false) }
    }

    fun onAvatarUriChanged(newUri: Uri, contentResolver: ContentResolver, filesDir: File) { // Вызывается из Activity после выбора изображения
        newUri?.let { uri ->
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val localFile = File(filesDir, "copied_image_${System.currentTimeMillis()}.jpg") // filesDir - приватная директория
                    val outputStream = FileOutputStream(localFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    val localFileUri = Uri.fromFile(localFile) // Uri для вашего локального файла

                    _uiState.update { it.copy(avatarUri = localFileUri.toString(), error = null, saveSuccess = false) }
                }
            } catch (e: Exception) {
                // Обработка ошибки копирования (включая SecurityException, если она возникает здесь)
                Timber.e("ImagePicker", "Ошибка копирования файла: ${e.message}", e)
                // Показать сообщение пользователю
            }
        }
    }

    fun onChangeAvatarClicked() {
        Timber.d("Запрос на изменение аватара")
        // Activity будет обрабатывать это и вызывать onAvatarUriChanged
    }

    fun saveProfile() {
        val currentState = _uiState.value
        if (currentState.currentUserId.isBlank()) {
            _uiState.update { it.copy(error = "Ошибка: ID пользователя неизвестен. Невозможно сохранить.") }
            return
        }
        if (currentState.displayName.isBlank()) {
            _uiState.update { it.copy(error = "Отображаемое имя не может быть пустым.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, saveSuccess = false) }
            val profileToSave = DomainUserProfile(
                userId = currentState.currentUserId,
                displayName = currentState.displayName,
                statusMessage = currentState.statusMessage.ifBlank { null }, // Сохраняем null, если пустая строка
                avatarUri = currentState.avatarUri?.ifBlank { null }
            )

            when (val saveResult = saveUserProfileUseCase(profileToSave)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, saveSuccess = true) }
                    Timber.i("Профиль успешно сохранен (локально).")
                    // Опционально: отправить обновление профиля другим пирам
                    //sendProfileUpdateToPeers(profileToSave)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = "Ошибка сохранения профиля: ${saveResult.error}") }
                    Timber.e("Ошибка сохранения профиля: ${saveResult.error}")
                }
            }
        }
    }

}