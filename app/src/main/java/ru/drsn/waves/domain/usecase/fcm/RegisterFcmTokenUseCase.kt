package ru.drsn.waves.domain.usecase.fcm

import ru.drsn.waves.domain.model.authentication.AuthError
import ru.drsn.waves.domain.repository.IAuthenticationRepository
import ru.drsn.waves.domain.repository.ICryptoRepository
import ru.drsn.waves.domain.model.utils.Result
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case для регистрации или обновления FCM токена устройства на бэкенд-сервере.
 * Этот use case инкапсулирует бизнес-логику, связанную с отправкой токена.
 * Он использует [IAuthenticationRepository] (так как gRPC метод `updateFcmToken` находится в `AuthorisationService`)
 * и опционально [UserPreferencesRepository] для проверки статуса аутентификации пользователя.
 */
class RegisterFcmTokenUseCase @Inject constructor(
    private val authenticationRepository: IAuthenticationRepository,
    private val cryptoRepository: ICryptoRepository // <--- ИСПОЛЬЗУЕМ ICryptoRepository
) {
    /**
     * Выполняет операцию регистрации/обновления FCM токена.
     *
     * @param token Новый FCM токен устройства.
     */
    suspend fun execute(token: String) {
        // Опциональная проверка: отправлять токен только если пользователь аутентифицирован.
        // Это важно, если сервер связывает FCM токен с конкретным пользователем.
        // AuthInterceptor автоматически добавит JWT, если он доступен.
        val authTokenResult = cryptoRepository.getAuthToken() // Получаем токен
        val isLoggedIn = authTokenResult is Result.Success // Предполагаем, что Success всегда содержит токен, если он есть

        if (!isLoggedIn) {
            Timber.w("User not logged in (token check via ICryptoRepository). FCM token registration deferred or skipped for token: ${token.takeLast(10)}...")
            return
        }

        Timber.d("Attempting to register FCM token with server: ${token.takeLast(10)}...")
        // Вызов метода репозитория для обновления токена.
        // Используется authenticationRepository, так как метод updateFcmToken определен в его интерфейсе
        // и реализуется через AuthorisationService на бэкенде.
        val result = authenticationRepository.updateFcmToken(token)

        result.fold(
            onSuccess = {
                Timber.i("FCM token registration successful with server.")
            },
            onFailure = { error ->
                // Обработка ошибок, возвращенных репозиторием.
                val errorMessage = when (error) {
                    is AuthError.ConnectionError -> "Connection error during FCM token registration."
                    is AuthError.FcmError -> "Operation failed for FCM token: ${error.message}"
                    is AuthError.AuthenticationFailed -> "Authentication failed, cannot register FCM token: ${error.message}"
                    // Добавьте другие специфичные AuthError типы, если они могут возникнуть при вызове updateFcmToken.
                    else -> "Unknown error during FCM token registration: $error"
                }
                Timber.e(errorMessage)
                // Здесь можно добавить логику для повторной попытки или более детального логирования ошибки.
            }
        )
    }
}