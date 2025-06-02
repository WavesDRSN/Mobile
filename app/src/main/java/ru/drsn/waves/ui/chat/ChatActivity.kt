package ru.drsn.waves.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.drsn.waves.R
import ru.drsn.waves.databinding.ActivityChatBinding
import ru.drsn.waves.domain.model.utils.Result // Общий Result
import ru.drsn.waves.domain.model.chat.*
import ru.drsn.waves.ui.chat.info.ChatInfoActivity
import timber.log.Timber

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PEER_NAME = "peer_name" // Имя собеседника или название чата
        const val EXTRA_CHAT_TYPE = "chat_type" // "PEER_TO_PEER" или "GROUP"

        fun newIntent(context: Context, sessionId: String, peerName: String, chatType: ChatType): Intent {
            return Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_CHAT_TYPE, chatType.name)
            }
        }
    }

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var binding: ActivityChatBinding
    private lateinit var messageListAdapter: MessageListAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Timber.d("ChatActivity создана для сессии: ${viewModel.sessionId}")

        setupToolbar()
        setupRecyclerView()
        setupInputControls()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarChat)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Заголовок будет установлен из ViewModel
        supportActionBar?.title = ""
    }

    private fun setupRecyclerView() {
        // ID текущего пользователя нужен для адаптера, чтобы различать свои и чужие сообщения.
        // ViewModel его получает асинхронно, поэтому адаптер создаем здесь,
        // но currentUserId передаем позже или делаем адаптер способным его обновлять.
        // Простой вариант: передать его при обновлении списка.
        // Более сложный: ViewModel предоставляет Flow<String?> currentUserId.
        // Пока что оставим так, предполагая, что ViewModel его предоставит при первом Success.

        linearLayoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Новые сообщения добавляются снизу и прокручивают список вверх
        }
        binding.messagesRecyclerView.layoutManager = linearLayoutManager
    }

    private fun setupInputControls() {
        binding.messageEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onMessageInputChanged(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.sendButton.setOnClickListener {
            viewModel.sendMessage()
        }
        // TODO: Обработка attachButton
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        Timber.d("Новое состояние UI в ChatActivity: $state")
                        binding.progressBarChat.visibility = if (state is ChatUiState.Loading) View.VISIBLE else View.GONE

                        when (state) {
                            is ChatUiState.Success -> {
                                binding.toolbarTitle.text = state.chatSessionDetails?.peerName ?: viewModel.sessionId

                                // Инициализируем или обновляем адаптер, если currentUserId доступен
                                val currentUserId = (viewModel.getUserNicknameUseCase() as? Result.Success)?.value
                                if (currentUserId != null) {
                                    if (!this@ChatActivity::messageListAdapter.isInitialized) {
                                        messageListAdapter = MessageListAdapter(currentUserId, viewModel.chatTypeFromArgs)
                                        binding.messagesRecyclerView.adapter = messageListAdapter
                                    }
                                    messageListAdapter.submitList(state.messages) {
                                        // Прокрутка к последнему сообщению после обновления списка
                                        if (state.messages.isNotEmpty()) {
                                            binding.messagesRecyclerView.smoothScrollToPosition(state.messages.size - 1)
                                        }
                                    }
                                } else {
                                    Timber.w("currentUserId еще не доступен, адаптер не обновлен/не создан.")
                                }
                            }
                            is ChatUiState.Error -> {
                                Toast.makeText(this@ChatActivity, state.message, Toast.LENGTH_LONG).show()
                            }
                            is ChatUiState.Loading -> { /* Уже обработано ProgressBar */ }
                        }
                    }
                }
                launch {
                    viewModel.currentMessageInput.collect { inputText ->
                        if (binding.messageEditText.text.toString() != inputText) {
                            binding.messageEditText.setText(inputText)
                            binding.messageEditText.setSelection(inputText.length) // Перемещаем курсор в конец
                        }
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                Toast.makeText(this, getString(R.string.profile_clicked_toast), Toast.LENGTH_SHORT).show()
                val intent = ChatInfoActivity.newIntent(this@ChatActivity, viewModel.sessionId, viewModel.chatTypeFromArgs)
                startActivity(intent)
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_chat_menu, menu)
        return true
    }
}