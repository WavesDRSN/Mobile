package ru.drsn.waves.data.repository

import android.util.Base64
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import ru.drsn.waves.data.datasource.local.crypto.ICryptoLocalDataSource
import ru.drsn.waves.data.utils.CryptoUtils
import ru.drsn.waves.domain.model.onion.OnionPacket
import ru.drsn.waves.domain.model.onion.OnionPayload
import ru.drsn.waves.domain.model.webrtc.PeerId
import ru.drsn.waves.domain.repository.IOnionPathBuilder
import timber.log.Timber
import javax.inject.Inject

class OnionPathBuilderImpl @Inject constructor(
    private val localDataSource: ICryptoLocalDataSource
) : IOnionPathBuilder {
    override suspend fun buildOnionPacket(senderId : String, message: ByteArray, path: List<PeerId>): OnionPacket {
        val json: Json = Json
        require(path.isNotEmpty()) { "Path must contain at least the receiver" }

        Timber.d("[$senderId] Начало построения OnionPacket. Размер сообщения: ${message.size} байт. Путь: $path")

        var currentLayer: ByteArray = message

        for (i in path.size - 1 downTo 0) {
            val targetNodeId = path[i]

            val ephemeral = localDataSource.generateEphemeralKeyPair()

            val sharedSecret = localDataSource.computeAndStoreSharedSecret(
                privateKeyToBase64(ephemeral?.private),
                TODO("получить публичный ключ у targetNodeId")
            )

            if (sharedSecret.isEmpty()) {
                return OnionPacket(ByteArray(0), "")
            }

            val nextHop : PeerId? = if (i < path.lastIndex) path[i + 1] else null
            Timber.d("[$senderId] Шифруем слой для узла ${targetNodeId.value}. Следующий hop: '$nextHop'")

            val payload = OnionPayload(
                nextPeerId = nextHop,
                encryptedMessage = currentLayer
            )

            val serializedpayload = json.encodeToString(payload).toByteArray(Charsets.UTF_8)

            val payloadAndKey = OnionPacket(
                payload = serializedpayload,
                ephemeralPublicKey = privateKeyToBase64(ephemeral?.private)
            )

            val serialized = json.encodeToString(payloadAndKey).toByteArray(Charsets.UTF_8)

            Timber.d("[$senderId] Сериализованный OnionPayload для $targetNodeId: ${serialized.size} байт")

            val encrypted = CryptoUtils.encryptWithSharedSecret(serialized, sharedSecret)

            currentLayer = encrypted
        }

        Timber.d("[$senderId] Построение завершено. Финальный OnionPacket, размер payload: ${currentLayer.size} байт")
        return OnionPacket(payload = currentLayer, ephemeralPublicKey = "")
    }

    private fun privateKeyToBase64(privateKey: AsymmetricKeyParameter?): String {
        val privKeyBytes = (privateKey as X25519PrivateKeyParameters).encoded
        return Base64.encodeToString(privKeyBytes, Base64.NO_WRAP)
    }

}