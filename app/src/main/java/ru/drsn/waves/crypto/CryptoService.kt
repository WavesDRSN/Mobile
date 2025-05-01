package ru.drsn.waves.crypto

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.inject.Singleton

@Singleton
class CryptoService(context: Context) {

    companion object {
        private const val TAG = "CryptoService"
    }

    private val keyStoreManager = KeyStoreManager(context)
    private var currentPrivateKey: PrivateKey? = null
    private var currentPublicKey: PublicKey? = null
    private var isInitialized = false
    private val initMutex = Mutex() // Для потокобезопасной инициализации

    // Используем свой CoroutineScope для фоновых задач сервиса
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Инициализирует сервис: загружает ключи из KeyStore или генерирует новые.
     * Должен быть вызван перед использованием других методов сервиса.
     *
     * @param onNewMnemonicGenerated Лямбда, которая будет вызвана *только если* были сгенерированы
     * новые ключи. Передает мнемоническую фразу, которую
     * **нужно показать пользователю для сохранения**.
     * @param onComplete Лямбда, вызываемая после успешной инициализации (загрузки или генерации).
     * @param onError Лямбда, вызываемая при ошибке инициализации.
     */
    fun initialize(
        onNewMnemonicGenerated: (mnemonic: String) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        serviceScope.launch {
            initMutex.withLock {
                if (isInitialized) {
                    Timber.tag(TAG).d("Already initialized.")
                    withContext(Dispatchers.Main) { onComplete() } // Вызываем на UI потоке
                    return@launch
                }

                try {
                    Timber.tag(TAG).d("Initializing CryptoService...")
                    if (keyStoreManager.keyExists()) {
                        Timber.tag(TAG).i("Key found in KeyStore. Loading existing keys...")
                        currentPrivateKey = keyStoreManager.loadPrivateKey()
                        currentPublicKey = keyStoreManager.loadPublicKey()
                        Timber.d(currentPrivateKey.toString())
                        Timber.d(currentPublicKey.toString())
                        if (currentPrivateKey != null && currentPublicKey != null) {
                            isInitialized = true
                            Timber.tag(TAG).i("Keys loaded successfully.")
                            withContext(Dispatchers.Main) { onComplete() }
                        } else {
                            // Ключ вроде есть, но загрузить не удалось - критическая ошибка
                            Timber.tag(TAG)
                                .e("Key alias exists, but failed to load keys. This should not happen.")
                            // Возможно, стоит попробовать удалить и сгенерировать заново?
                            // keyStoreManager.deleteKey() // Опасно, если пользователь не сохранил фразу!
                            throw RuntimeException("Failed to load existing keys from KeyStore.")
                        }
                    } else {
                        Timber.tag(TAG).i("No key found in KeyStore. Generating new keys...")
                        val mnemonic = SeedPhraseUtil.generateMnemonic()
                        // Важно: Передаем мнемонику для отображения ПОЛЬЗОВАТЕЛЮ
                        withContext(Dispatchers.Main) {
                            onNewMnemonicGenerated(mnemonic)
                        }

                        val seed = SeedPhraseUtil.generateSeedFromMnemonic(mnemonic)
                        val keyPair = SeedPhraseUtil.generateEd25519KeyPair(seed)

                        val stored = keyStoreManager.storeKeyPair(keyPair)
                        if (stored) {
                            currentPrivateKey = keyPair.private
                            currentPublicKey = keyPair.public
                            isInitialized = true
                            Timber.tag(TAG).i("New keys generated and stored successfully.")
                            withContext(Dispatchers.Main) { onComplete() }
                        } else {
                            throw RuntimeException("Failed to store newly generated keys in KeyStore.")
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Initialization failed")
                    isInitialized = false // Сбрасываем флаг при ошибке
                    withContext(Dispatchers.Main) { onError(e) }
                }
            }
        }
    }

    /**
     * Возвращает публичный ключ в виде байтового массива (X.509/SPKI encoded).
     * @throws IllegalStateException если сервис не инициализирован.
     */
    fun getPublicKeyBytes(): ByteArray {
        checkInitialized()
        // currentPublicKey не должен быть null после успешной инициализации
        return currentPublicKey!!.encoded
    }


    /**
     * Подписывает данные с использованием приватного ключа из KeyStore.
     * @param dataToSign Данные для подписи.
     * @return Байтовый массив подписи.
     * @throws IllegalStateException если сервис не инициализирован.
     * @throws RuntimeException если произошла ошибка при подписи.
     */
    suspend fun signData(dataToSign: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        checkInitialized()
        val privateKey = currentPrivateKey ?: throw IllegalStateException("Private key is null after initialization")

        try {
            // Для Ed25519 используется "EdDSA", но JCA часто ожидает "Ed25519"
            val signature: Signature = Signature.getInstance("Ed25519") // или "EdDSA" если Ed25519 не работает
            signature.initSign(privateKey)
            signature.update(dataToSign)
            signature.sign()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error signing data")
            throw RuntimeException("Failed to sign data", e)
        }
    }

    /**
     * Проверяет подпись данных с использованием публичного ключа.
     * @param data Данные, которые были подписаны.
     * @param signatureBytes Подпись для проверки.
     * @return true, если подпись верна, иначе false.
     * @throws IllegalStateException если сервис не инициализирован.
     * @throws RuntimeException если произошла ошибка при проверке.
     */
    suspend fun verifySignature(data: ByteArray, signatureBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        checkInitialized()
        val publicKey = currentPublicKey ?: throw IllegalStateException("Public key is null after initialization")

        try {
            val signature: Signature = Signature.getInstance("Ed25519") // или "EdDSA"
            signature.initVerify(publicKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error verifying signature")
            // Можно просто вернуть false или пробросить исключение в зависимости от логики
            // throw RuntimeException("Failed to verify signature", e)
            false
        }
    }


    private fun checkInitialized() {
        if (!isInitialized || currentPrivateKey == null || currentPublicKey == null) {
            throw IllegalStateException("CryptoService is not initialized or keys are missing.")
        }
    }

    // Вызывать при уничтожении компонента, который держит сервис (например, ViewModel)
    fun cleanup() {
        serviceScope.coroutineContext.cancel()
        Timber.tag(TAG).d("CryptoService cleaned up.")
    }
}