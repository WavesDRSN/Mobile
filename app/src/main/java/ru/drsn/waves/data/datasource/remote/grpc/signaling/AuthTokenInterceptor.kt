package ru.drsn.waves.data.datasource.remote.grpc.signaling // Или другой подходящий пакет

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
// import io.grpc.ForwardingClientCallListener // Не используется в текущей реализации
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import kotlinx.coroutines.runBlocking // Используется для вызова suspend функции из не-suspend контекста интерцептора
import ru.drsn.waves.domain.model.utils.Result // Ваш Result класс
import ru.drsn.waves.domain.model.crypto.AuthToken
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.repository.ICryptoRepository // Для получения токена
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthTokenInterceptor @Inject constructor(
    private val cryptoRepository: ICryptoRepository // Зависимость для получения токена
) : ClientInterceptor {

    companion object {
        // Ключ для заголовка авторизации. Обычно "Authorization".
        // Значение обычно "Bearer <token>".
        val AUTHORIZATION_METADATA_KEY: Metadata.Key<String> =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
        private const val TAG = "AuthTokenInterceptor"
    }

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        Timber.tag(TAG).d("Перехват вызова: ${method.fullMethodName}")

        // Получаем токен. Так как interceptCall не suspend, а getAuthToken - suspend,
        // используем runBlocking. Это не идеальный вариант для производительности,
        // но для получения токена перед вызовом может быть приемлемо.
        // В более сложных сценариях можно рассмотреть другие подходы (например, кэширование токена
        // и его асинхронное обновление).
        val tokenResult: Result<AuthToken, CryptoError> = runBlocking {
            cryptoRepository.getAuthToken()
        }

        // Создаем оригинальный ClientCall без модификации callOptions для метаданных здесь
        val originalCall = next.newCall(method, callOptions)

        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(originalCall) {
            override fun start(responseListener: Listener<RespT>?, headers: Metadata) {
                // Добавляем токен в заголовки (объект headers) здесь, это более надежный способ
                if (tokenResult is Result.Success) {
                    try {
                        headers.put(AUTHORIZATION_METADATA_KEY, "Bearer ${tokenResult.value}")
                        Timber.tag(TAG).d("Заголовок Authorization добавлен в исходящие метаданные.")
                    } catch (e: Exception) {
                        // Обработка возможных ошибок при добавлении заголовка (например, некорректные символы)
                        Timber.tag(TAG).e(e, "Ошибка при добавлении заголовка Authorization в метаданные.")
                        // Решаем, что делать дальше: отправить без заголовка или прервать вызов
                        // super.start(responseListener, headers) // Отправить без заголовка (или с исходными)
                        // cancel("Failed to add auth header", e) // Прервать вызов
                        // Пока просто логируем и отправляем как есть (без добавленного заголовка, если была ошибка)
                        super.start(responseListener, headers)
                    }
                } else {
                    Timber.tag(TAG).w("Токен не найден или произошла ошибка при его получении. Заголовок Authorization не добавлен.")
                    // В зависимости от требований, здесь можно либо прервать вызов, либо отправить его без токена.
                    // Если сервер требует токен для всех защищенных эндпоинтов, то лучше прерывать:
                    // if (isSecureEndpoint(method)) { // Нужна логика определения защищенных эндпоинтов
                    //     cancel("Missing auth token for secure endpoint", null)
                    //     return // Не вызываем super.start
                    //}
                }
                // Вызываем оригинальный метод start с (возможно) модифицированными заголовками
                super.start(responseListener, headers)
            }
        }
    }
}
