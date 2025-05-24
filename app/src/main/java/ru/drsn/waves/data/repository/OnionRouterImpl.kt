package ru.drsn.waves.data.repository

import android.util.Base64
import kotlinx.serialization.json.Json
import ru.drsn.waves.data.datasource.local.crypto.ICryptoLocalDataSource
import ru.drsn.waves.data.datasource.remote.webrtc.WebRTCControllerImpl
import ru.drsn.waves.data.utils.CryptoUtils
import ru.drsn.waves.domain.model.onion.OnionPayload
import ru.drsn.waves.domain.repository.IOnionRouter
import timber.log.Timber

class OnionRouterImpl(
    private val webRTCController: WebRTCControllerImpl,
    private val localDataSource: ICryptoLocalDataSource
) : IOnionRouter {
    override suspend fun handleIncomingOnionData(fromPeerId: String, encryptedData: ByteArray, otherPublicKeyEncoded : String): String {
        val json : Json = Json
        Timber.d("Received Onion packet from $fromPeerId, size=${encryptedData.size} bytes")

        try {
            // 1. Расшифровываем текущий слой Onion
            Timber.d("Attempting to decrypt Onion layer from $fromPeerId")

            val decrypted = decryptLayer(fromPeerId, encryptedData, otherPublicKeyEncoded)

            Timber.d("Successfully decrypted layer from $fromPeerId")

            // 2. Десериализация OnionPayload
            val payload = Json.decodeFromString<OnionPayload>(decrypted)
            Timber.d("Deserialized OnionPayload: nextPeerId=${payload.nextPeerId}, encryptedMessageSize=${payload.encryptedMessage.size}")

            // 3. Маршрутизация или обработка финального сообщения
            when {
                (payload.nextPeerId?.value?.isEmpty() == true || payload.nextPeerId?.value == null) -> {
                    Timber.d("FINAL MESSAGE - ${payload.encryptedMessage.toString(Charsets.UTF_8)}")
                    return payload.encryptedMessage.toString(Charsets.UTF_8)
                }

                else -> {
                    Timber.d("Node is an intermediate relay. Forwarding encrypted payload to ${payload.nextPeerId}")
                    val serializedPacket = json.encodeToString(payload.encryptedMessage)
                    webRTCController.sendMessage(payload.nextPeerId, "", serializedPacket.toByteArray())
                    return ""
                }
            }
            return ""
        } catch (e: Exception) {
            Timber.e(e, "Onion processing failed for packet from $fromPeerId")
            return ""
        }
    }

    private suspend fun decryptLayer(peerId: String, data: ByteArray, otherPublicKeyEncoded : String): String {
        Timber.d("Retrieving shared secret for $peerId to decrypt layer")
        val myPrivateKey = Base64.encodeToString(localDataSource.loadDhKeyPair()?.private?.encoded, Base64.NO_WRAP)
        val key = localDataSource.computeAndStoreSharedSecret(myPrivateKey, otherPublicKeyEncoded)
        Timber.d("Shared secret found. Decrypting layer...")
        val result = CryptoUtils.decryptData(data, key).decodeToString()
        Timber.d("Layer from $peerId successfully decrypted")
        return result
    }
}