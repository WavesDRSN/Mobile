package ru.drsn.waves.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log // Добавлен импорт для логирования
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import org.webrtc.PeerConnection
import ru.drsn.waves.WavesApplication
import ru.drsn.waves.data.Message // Убедитесь, что этот импорт правильный
import ru.drsn.waves.databinding.ActivityChatBinding
// Импортируем интерфейсы и менеджер WebRTC
import ru.drsn.waves.webrtc.contract.IWebRTCManager
import ru.drsn.waves.webrtc.contract.WebRTCListener
import timber.log.Timber
import java.util.Date
import java.util.UUID

// Реализуем интерфейс WebRTCListener
class ChatActivity : AppCompatActivity(), WebRTCListener {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter

    // Используем интерфейс IWebRTCManager
    private lateinit var webRTCManager: IWebRTCManager

    private lateinit var currentUserId: String // ID текущего пользователя
    private lateinit var recipientUserId: String
    private lateinit var recipientName: String

    // Логгирование
    private val TAG = "ChatActivity"

    companion object {
        private const val EXTRA_RECIPIENT_ID = "recipient_id"
        private const val EXTRA_RECIPIENT_NAME = "recipient_name"
        private const val EXTRA_CURRENT_USER_ID = "current_user_id" // Рекомендуется передавать ID

        fun newIntent(context: Context, recipientId: String, recipientName: String, currentUserId: String): Intent {
            return Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_RECIPIENT_ID, recipientId)
                putExtra(EXTRA_RECIPIENT_NAME, recipientName)
                putExtra(EXTRA_CURRENT_USER_ID, currentUserId) // Передаем ID текущего пользователя
            }
        }
    }

    @SuppressLint("LogNotTimber")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем данные собеседника и текущего пользователя из Intent
        recipientUserId = intent.getStringExtra(EXTRA_RECIPIENT_ID) ?: run {
            Timber.tag(TAG).e("Recipient ID not provided in Intent!")
            finish() // Закрываем активити, если нет ID собеседника
            return // Выходим из onCreate
        }
        recipientName = intent.getStringExtra(EXTRA_RECIPIENT_NAME) ?: "Собеседник"
        currentUserId = intent.getStringExtra(EXTRA_CURRENT_USER_ID) ?: run {
            Timber.tag(TAG).e("Current User ID not provided in Intent! Using default.")
            "user123" // Запасной вариант, но лучше передавать явно
        }

        // !!! ВАЖНО: Получите ваш экземпляр WebRTCManager !!!
        // Это может быть синглтон, полученный через DI (Hilt, Koin), Service Locator или из Application класса
        // Пример:
        webRTCManager = (application as WavesApplication).webRTCManager // !!! ЗАМЕНИТЕ YourApp.webRTCManager на ваш способ получения !!!

        webRTCManager.getDataHandler(recipientUserId)?.changeListener(this)

        setupToolbar()
        setupRecyclerView()
        setupSendButton()
        loadInitialMessages() // Пока загружает только примеры
    }

    override fun onStart() {
        super.onStart()
        // Регистрируем эту Activity как слушателя событий WebRTC
        Timber.tag(TAG).d("Registering WebRTC listener for target: $recipientUserId")
        webRTCManager.listener = this
    }

    override fun onStop() {
        super.onStop()
        // ВАЖНО: Убираем слушателя, чтобы избежать утечек памяти и
        // получения сообщений, когда Activity неактивна.
        // Проверяем, что убираем именно себя (на случай, если другой слушатель был установлен)
        if (webRTCManager.listener === this) {
            Timber.tag(TAG).d("Unregistering WebRTC listener")
            webRTCManager.listener = null
        }
    }


    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarChat)
        supportActionBar?.apply {
            title = recipientName
            // subtitle = "Offline" // Начальное состояние
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(currentUserId)
        binding.recyclerViewMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true // Новые сообщения будут внизу
            }
        }
    }

    private fun setupSendButton() {
        binding.buttonSendMessage.setOnClickListener {
            val messageText = binding.editTextMessageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                binding.editTextMessageInput.text.clear()
            }
        }
    }

    private fun sendMessage(text: String) {
        // Создаем объект сообщения для немедленного отображения в UI
        val newMessage = Message(
            id = UUID.randomUUID().toString(), // Генерируем временный ID для UI
            text = text,
            senderId = currentUserId, // Отправлено текущим пользователем
            timestamp = Date().time
        )
        // Отображаем сообщение у себя
        chatAdapter.addMessage(newMessage)
        binding.recyclerViewMessages.scrollToPosition(chatAdapter.itemCount - 1)

        // Отправляем сообщение через WebRTCManager
        Timber.tag(TAG).d("Sending message to $recipientUserId via WebRTC: $text")
        webRTCManager.sendMessage(recipientUserId, text)

        // !!! Возможно, здесь также нужна логика сохранения сообщения в локальную БД !!!
    }

    private fun loadInitialMessages() {
        // !!! Замените на реальную загрузку сообщений из локальной БД или кэша !!!
        Timber.tag(TAG).w("Loading sample messages only!")
        val sampleMessages = listOf(
            Message(UUID.randomUUID().toString(), "Привет!", recipientUserId, Date().time - 50000),
            Message(UUID.randomUUID().toString(), "Привет! Как твои дела?", currentUserId, Date().time - 40000),
            Message(UUID.randomUUID().toString(), "Норм, твои как?", recipientUserId, Date().time - 30000)
        )
        chatAdapter.submitList(sampleMessages.toMutableList())
    }

    // --- Реализация методов WebRTCListener ---

    override fun onConnectionStateChanged(target: String, state: PeerConnection.IceConnectionState) {
        // Проверяем, относится ли изменение состояния к текущему собеседнику
        if (target == recipientUserId) {
            Timber.tag(TAG).i("Connection state for $target changed: $state")
            // Обновляем UI в главном потоке
            runOnUiThread {
                // Пример обновления подзаголовка в Toolbar
                supportActionBar?.subtitle = when(state) {
                    PeerConnection.IceConnectionState.CHECKING -> "Проверка..."
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> "Соединено"
                    PeerConnection.IceConnectionState.DISCONNECTED -> "Отключено"
                    PeerConnection.IceConnectionState.FAILED -> "Ошибка соединения"
                    PeerConnection.IceConnectionState.CLOSED -> "Закрыто"
                    PeerConnection.IceConnectionState.NEW -> "Новое"
                    else -> "Статус неизвестен"
                }
            }
        } else {
            Timber.tag(TAG).d("Ignoring connection state change for different target: $target")
        }
    }

    override fun onMessageReceived(sender: String, message: String) {
        // Проверяем, что сообщение пришло от текущего собеседника
        if (sender == recipientUserId) {
            Timber.tag(TAG)
                .i("Message received from $sender: $message (Callback Thread: ${Thread.currentThread().name})")
            // Обновляем UI в главном потоке
            runOnUiThread {
                Timber.tag(TAG).d("Displaying message from $sender on Main Thread")
                val receivedMessage = Message(
                    id = UUID.randomUUID().toString(), // Генерируем ID
                    text = message,
                    senderId = sender, // ID отправителя
                    timestamp = Date().time
                )
                chatAdapter.addMessage(receivedMessage)
                binding.recyclerViewMessages.scrollToPosition(chatAdapter.itemCount - 1)
            }
        } else {
            // Сообщение от другого пользователя, возможно, показать уведомление?
            Timber.tag(TAG)
                .w("Received message from unexpected sender: $sender (Current chat is with $recipientUserId)")
            // Можно реализовать логику показа уведомления о сообщении из другого чата
            // showNotificationForOtherChat(sender, message)
        }
    }

    override fun onError(target: String?, error: String) {
        // Проверяем, относится ли ошибка к текущему чату или она общая
        if (target == null || target == recipientUserId) {
            Timber.tag(TAG).e("WebRTC Error (target: $target): $error")
            // Показать ошибку пользователю (например, через Snackbar или Toast)
            runOnUiThread {
                // Пример:
                com.google.android.material.snackbar.Snackbar.make(binding.root, "Ошибка WebRTC: $error", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                // Можно также обновить статус в Toolbar
                supportActionBar?.subtitle = "Ошибка"
            }
        } else {
            Timber.tag(TAG).e("Ignoring error for different target: $target, Error: $error")
        }
    }

    override fun onDataChannelOpen(target: String) {
        Timber.d("something really hoes wrong")
    }
}