package ru.drsn.waves.webrtc

import org.webrtc.DataChannel
import ru.drsn.waves.webrtc.contract.WebRTCListener
import timber.log.Timber
import java.nio.charset.Charset

// Обработчик событий одного DataChannel
class DataChannelHandler(
    private val target: String,
    private val webRTCListener: WebRTCListener?,
    private val dataChannel: DataChannel
) : DataChannel.Observer {

    override fun onBufferedAmountChange(previousAmount: Long) {
        Timber.d("[$target] DataChannel buffered amount changed: $previousAmount")
    }

    override fun onStateChange() {
        // Состояние DataChannel изменилось (OPEN, CONNECTING, CLOSING, CLOSED)
        // Это важно для понимания, можно ли отправлять сообщения
        // val state = // как получить состояние из observer? Нужно брать у самого DataChannel
        Timber.d("[$target] DataChannel state changed to ${dataChannel.state()}.")
        // Можно уведомлять webRTCListener?.onDataChannelStateChanged(target, newState)
    }

    override fun onMessage(buffer: DataChannel.Buffer) {
        Timber.e("[$target] ******** ON_MESSAGE CALLED! Buffer received. Size: ${buffer.data.remaining()} *********")
        val data = buffer.data
        val bytes = ByteArray(data.remaining())
        data.get(bytes)
        // Важно: Убедись, что обе стороны используют одну кодировку! UTF-8 - стандартный выбор.
        val message = String(bytes)
        Timber.i("[$target] Message received: $message")
        webRTCListener?.onMessageReceived(target, message)
    }
}