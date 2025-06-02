package ru.drsn.waves.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.drsn.waves.domain.model.chat.ChatType
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.usecase.chat.GetSessionInfoUseCase
import ru.drsn.waves.domain.usecase.profile.LoadUserProfileUseCase
import ru.drsn.waves.ui.chat.info.ChatInfoUiState
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val loadUserProfileUseCase: LoadUserProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        Timber.d("ProfileViewModel инициализирована")
        viewModelScope.launch {

            loadProfileInfo()
        }
    }

    private fun loadProfileInfo() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val profileResult = loadUserProfileUseCase()
            // Получаем или создаем сессию чата для получения актуального имени и деталей
            // Для группового чата participantIds нужно будет передавать или получать отдельно

            if (profileResult is Result.Success) {
                _uiState.emit(ProfileUiState.Success(profileResult.value))
            } else if (profileResult is Result.Error) {
                _uiState.value = ProfileUiState.Error("Не удалось загрузить детали профиля: ${profileResult.error}")
            }
        }
    }

}