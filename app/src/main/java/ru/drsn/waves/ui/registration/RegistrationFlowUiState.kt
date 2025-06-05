package ru.drsn.waves.ui.registration

import ru.drsn.waves.domain.model.crypto.MnemonicPhrase

sealed class RegistrationFlowUiState {
    data object NicknameEntryStep : RegistrationFlowUiState() // Шаг ввода никнейма
    data class MnemonicDisplayStep(val mnemonic: MnemonicPhrase) : RegistrationFlowUiState() // Шаг отображения мнемоники
    data class MnemonicVerificationStep(val originalMnemonicWords: List<String>) : RegistrationFlowUiState() // Шаг проверки мнемоники
    data object RegistrationSuccessStep : RegistrationFlowUiState() // Успешное завершение
    data object Loading : RegistrationFlowUiState() // Общее состояние загрузки
    data class Error(val message: String, val step: RegistrationStep? = null) : RegistrationFlowUiState() // Ошибка на каком-либо шаге
}