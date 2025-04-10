package ru.drsn.waves

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log // Добавлен импорт
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.PeerConnection // Добавлен импорт
import ru.drsn.waves.signaling.SignalingService
import ru.drsn.waves.signaling.SignalingServiceImpl // Убедись, что это правильный импорт интерфейса/класса
import ru.drsn.waves.ui.chat.ChatActivity // Импорт ChatActivity
import ru.drsn.waves.webrtc.SdpObserver
import ru.drsn.waves.webrtc.WebRTCManager
import ru.drsn.waves.webrtc.contract.IWebRTCManager
import ru.drsn.waves.webrtc.contract.WebRTCListener // Импорт интерфейса Listener
import ru.drsn.waves.webrtc.utils.DataModelType
import timber.log.Timber
import java.nio.ByteBuffer
import kotlin.random.Random

// Реализуем интерфейс WebRTCListener
class WebRTCActivity : AppCompatActivity(), WebRTCListener {
    // Убираем lateinit для signalingService, т.к. получаем его из Application
    private val signalingService: SignalingService by lazy {
        (application as WavesApplication).signalingService
    }
    private val webRTCManager: IWebRTCManager by lazy {
        (application as WavesApplication).webRTCManager
    }
    private lateinit var username: String
    private lateinit var targetUser: String // ID пользователя, которому звоним

    private lateinit var editTextTarget: EditText
    private lateinit var editTextMessage: EditText
    private lateinit var btnConnectServer: Button
    private lateinit var btnRequestConnection: Button
    private lateinit var btnSendMessage: Button

    // Флаг, чтобы не запускать ChatActivity несколько раз для одного и того же вызова
    private var chatLaunchedForTarget: String? = null

    // Логгирование
    private val TAG = "WebRTCActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webrtc)
        Timber.tag(TAG).d("onCreate")

        editTextTarget = findViewById(R.id.editTextTarget)
        editTextMessage = findViewById(R.id.editTextMessage)
        btnConnectServer = findViewById(R.id.btnConnectServer)
        btnRequestConnection = findViewById(R.id.btnRequestConnection)
        btnSendMessage = findViewById(R.id.btnSendMessage)

        username = "User_" + Random.nextInt(1000)
        // Инициализация webRTCManager и signalingService происходит через lazy делегаты выше

        btnConnectServer.setOnClickListener {
            connectToServer()
        }

        btnRequestConnection.setOnClickListener {
            targetUser = editTextTarget.text.toString().trim()
            if (targetUser.isNotEmpty()) {
                // Сбрасываем флаг перед новым вызовом
                chatLaunchedForTarget = null
                requestConnection(targetUser)
            } else {
                Toast.makeText(this, "Введите ID собеседника", Toast.LENGTH_SHORT).show()
            }
        }

        btnSendMessage.setOnClickListener {
            val message = editTextMessage.text.toString()
            if (message.isNotEmpty() && ::targetUser.isInitialized) {
                sendMessage(targetUser, message)
            } else if (!::targetUser.isInitialized) {
                Toast.makeText(this, "Сначала установите соединение", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Timber.tag(TAG).d("onStart - Registering WebRTC listener")
        // Устанавливаем эту Activity как слушателя
        webRTCManager.listener = this
        // Сбрасываем флаг при возвращении на экран (возможно, соединение уже было установлено, пока нас не было)
        // Хотя, если мы хотим открыть чат только один раз при установке, это может и не нужно.
        // chatLaunchedForTarget = null;
    }

    override fun onStop() {
        super.onStop()
        Timber.tag(TAG).d("onStop - Unregistering WebRTC listener")
        // Убираем слушателя, чтобы избежать утечек и проблем с lifecycle
        if (webRTCManager.listener === this) {
            webRTCManager.listener = null
        }
        // Если мы уходим с экрана, возможно, стоит сбросить флаг?
        // chatLaunchedForTarget = null
    }

    private fun connectToServer() {
        lifecycleScope.launch {
            try {
                // Убедимся, что передаем правильный тип в connect, если он изменился
                signalingService.connect(username, "tt.vld.su", 50051) // Используем интерфейс
                Toast.makeText(this@WebRTCActivity, "Подключено к серверу как $username!", Toast.LENGTH_SHORT).show()
                Timber.tag(TAG).i("Connected to signaling as $username")
            } catch (e: Exception) {
                Timber.e(e, "Ошибка подключения к серверу")
                Toast.makeText(this@WebRTCActivity, "Ошибка подключения: ${e.message}", Toast.LENGTH_LONG).show()
                Timber.tag(TAG).e(e, "Connection error")
            }
        }
    }

    private fun requestConnection(target: String) {
        Timber.tag(TAG).i("Requesting connection to $target")
        Toast.makeText(this, "Запрос соединения к $target...", Toast.LENGTH_SHORT).show()
        webRTCManager.call(target)
    }

    private fun sendMessage(target: String, message: String) {
        Timber.tag(TAG).d("Sending message to $target: $message")
        webRTCManager.sendMessage(target, message)
        editTextMessage.text.clear() // Очищаем поле после отправки
        Toast.makeText(this, "Сообщение отправлено (WebRTC)", Toast.LENGTH_SHORT).show()
    }

    // --- Реализация методов WebRTCListener ---

    override fun onConnectionStateChanged(target: String, state: PeerConnection.IceConnectionState) {
        Timber.tag(TAG).i("Listener: Connection state for $target: $state")
        // Можно обновлять какой-то статус в UI этой активити, если нужно
        runOnUiThread {
            // Пример: можно менять текст кнопки или добавлять TextView со статусом
            btnRequestConnection.text = when(state) {
                PeerConnection.IceConnectionState.CHECKING -> "Проверка $target..."
                PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> "Соединено с $target"
                PeerConnection.IceConnectionState.DISCONNECTED -> "Отключено от $target"
                PeerConnection.IceConnectionState.FAILED -> "Ошибка $target"
                PeerConnection.IceConnectionState.CLOSED -> "Закрыто $target"
                else -> "Соединение с $target"
            }
        }
    }

    override fun onMessageReceived(sender: String, message: String) {
        // Эта активити не отображает чат, поэтому просто логируем
        // Или можно показать Toast для теста
        Timber.tag(TAG).i("Listener: Message received from $sender: $message")
        runOnUiThread {
            Toast.makeText(this, "Сообщение от $sender: $message", Toast.LENGTH_LONG).show()
        }
    }

    override fun onError(target: String?, error: String) {
        Timber.tag(TAG).e("Listener: WebRTC Error (target: $target): $error")
        runOnUiThread {
            Toast.makeText(this, "Ошибка WebRTC ($target): $error", Toast.LENGTH_LONG).show()
        }
    }

    // !!! ВОТ ОБРАБОТЧИК ОТКРЫТИЯ КАНАЛА !!!
    override fun onDataChannelOpen(target: String) {
        Timber.tag(TAG).i("Listener: DataChannel OPEN for target: $target")

        if (!::targetUser.isInitialized) targetUser = target

        // Проверяем, совпадает ли target с тем, кому мы звонили И не запускали ли мы уже чат
        if (::targetUser.isInitialized && target == targetUser && chatLaunchedForTarget != target) {
            Timber.tag(TAG)
                .d("DataChannel open matches target user ($target). Checking if chat launched...")
            // Если совпадает и чат еще не запущен для этого вызова
            chatLaunchedForTarget = target // Устанавливаем флаг, что для этого target запускаем

            // TODO: Получить реальное имя пользователя (recipientName) по target ID.
            // Сейчас используем target ID в качестве имени для примера.
            val recipientName = target

            Timber.tag(TAG).i("Launching ChatActivity for $recipientName (ID: $target)")

            // Запускаем ChatActivity в главном потоке
            runOnUiThread {
                val intent = ChatActivity.newIntent(
                    context = this,
                    recipientId = target,
                    recipientName = recipientName, // Используем ID пока нет имени
                    currentUserId = this.username // Передаем наш ID
                )
                try {
                    startActivity(intent)
                    Timber.tag(TAG).d("ChatActivity launched.")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to launch ChatActivity")
                    Toast.makeText(this, "Не удалось открыть чат", Toast.LENGTH_SHORT).show()
                    // Сбрасываем флаг, если запуск не удался
                    chatLaunchedForTarget = null
                }
            }
        } else {
            Timber.tag(TAG)
                .w("DataChannel open for unexpected target '$target' (expected '$targetUser') or chat already launched ($chatLaunchedForTarget). Ignoring.")
        }
    }
}