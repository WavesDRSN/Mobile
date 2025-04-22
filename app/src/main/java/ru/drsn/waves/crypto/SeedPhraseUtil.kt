package ru.drsn.waves.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Utils // Для безопасного сравнения времени
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.MnemonicException
import org.bitcoinj.wallet.DeterministicSeed // Для работы с сидом bitcoinj
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.io.IOException // MnemonicCode может бросать IOException
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.ArrayList // bitcoinj использует List<String>

object SeedPhraseUtil {

    // Инициализация BouncyCastle провайдера один раз
    init {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME) // Удаляем, если уже был добавлен
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        // Инициализация MnemonicCode (загружает список слов)
        // Лучше делать это один раз, т.к. операция может быть затратной
        try {
            MnemonicCode.INSTANCE ?: throw RuntimeException("Could not initialize MnemonicCode")
        } catch (e: IOException) {
            throw RuntimeException("Failed to load BIP39 word list", e)
        }
    }

    // bitcoinj использует пустую строку для стандартного сида BIP39
    private const val PASSPHRASE = ""

    /**
     * Генерирует новую мнемоническую фразу (12 слов) с использованием bitcoinj.
     * ВАЖНО: Эта фраза должна быть показана пользователю для безопасного хранения!
     */
    suspend fun generateMnemonic(): String = withContext(Dispatchers.Default) {
        val entropy = ByteArray(DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8) // 128 bits for 12 words
        SecureRandom().nextBytes(entropy)
        val mnemonicCode = MnemonicCode.INSTANCE // Получаем инстанс (уже инициализирован в init)
        val mnemonicList: List<String> = try {
            mnemonicCode.toMnemonic(entropy)
        } catch (e: MnemonicException.MnemonicLengthException) {
            // Этого не должно произойти с правильной длиной энтропии
            throw RuntimeException("Internal error: Invalid entropy length for mnemonic generation", e)
        }
        mnemonicList.joinToString(" ")
    }

    /**
     * Генерирует сид (байты) из мнемонической фразы с использованием bitcoinj.
     */
    suspend fun generateSeedFromMnemonic(mnemonic: String): ByteArray = withContext(Dispatchers.Default) {
        val mnemonicList = ArrayList(mnemonic.split(" "))
        val mnemonicCode = MnemonicCode.INSTANCE

        // Валидация (опционально, но рекомендуется)
        try {
            mnemonicCode.check(mnemonicList)
        } catch (e: MnemonicException) {
            throw IllegalArgumentException("Invalid mnemonic phrase: ${e.message}", e)
        }

        // Генерация сида
        MnemonicCode.toSeed(mnemonicList, PASSPHRASE)
    }

    /**
     * Генерирует пару ключей Ed25519 из сида (первых 32 байт).
     * Возвращает стандартную java.security.KeyPair.
     * (Эта часть остается без изменений, т.к. использует Bouncy Castle)
     */
    suspend fun generateEd25519KeyPair(seed: ByteArray): KeyPair = withContext(Dispatchers.Default) {
        if (seed.size < Ed25519.SECRET_KEY_SIZE) {
            throw IllegalArgumentException("Seed must be at least ${Ed25519.SECRET_KEY_SIZE} bytes long for Ed25519")
        }
        // Используем первые 32 байта сида как приватный ключ Ed25519
        val privateKeyParams = Ed25519PrivateKeyParameters(seed, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()

        // Конвертируем ключи BouncyCastle в формат java.security
        val keyFactory = KeyFactory.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)

        // Приватный ключ
        val privateKeyInfo: PrivateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(privateKeyParams)
        val pkcs8KeySpec = PKCS8EncodedKeySpec(privateKeyInfo.encoded)
        val privateKey: PrivateKey = keyFactory.generatePrivate(pkcs8KeySpec)

        // Публичный ключ
        val publicKeyInfo: SubjectPublicKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicKeyParams)
        val x509KeySpec = X509EncodedKeySpec(publicKeyInfo.encoded)
        val publicKey: PublicKey = keyFactory.generatePublic(x509KeySpec)

        KeyPair(publicKey, privateKey)
    }
}