package ru.drsn.waves.ui.chat.info

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import ru.drsn.waves.databinding.ActivityChatInfoBinding
import ru.drsn.waves.domain.model.utils.Result // Общий Result
import ru.drsn.waves.domain.model.chat.*
import timber.log.Timber

@AndroidEntryPoint
class ChatInfoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_CHAT_TYPE = "chat_type" // "PEER_TO_PEER" или "GROUP"

        fun newIntent(context: Context, sessionId: String,  chatType: ChatType): Intent {
            return Intent(context, ChatInfoActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_CHAT_TYPE, chatType.name)
            }
        }
    }

    private val viewModel: ChatInfoViewModel by viewModels()
    private lateinit var binding: ActivityChatInfoBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Timber.d("ChatInfoActivity создана для сессии: ${viewModel.sessionId}")

        setupToolbar()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarChat)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Заголовок будет установлен из ViewModel
        supportActionBar?.title = ""
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        Timber.d("Новое состояние UI в ChatActivity: $state")
                        binding.progressBarChat.visibility =
                            if (state is ChatInfoUiState.Loading) View.VISIBLE else View.GONE

                        when (state) {
                            is ChatInfoUiState.Success -> {
                                val profile = state.profile
                                binding.toolbarTitle.text = profile.userId

                                if (profile.avatarUri != null) {
                                    val imageUri = Uri.parse(profile.avatarUri)
                                    if (imageUri != null) {
                                        binding.profileInfoLayout.profileAvatarView.setImageURI(imageUri)
                                    }
                                }

                                binding.profileInfoLayout.nameTextView.text = profile.displayName
                                binding.aboutChatLayout.userIdValue.text = "@" + profile.userId
                                binding.aboutChatLayout.bioValue.text = profile.statusMessage
                                if (profile.statusMessage.isNullOrBlank()) {
                                    binding.aboutChatLayout.bioLabel.visibility = View.GONE
                                    binding.aboutChatLayout.bioValue.visibility = View.GONE
                                }

                            }
                            is ChatInfoUiState.Error -> {
                                Toast.makeText(this@ChatInfoActivity, state.message, Toast.LENGTH_LONG).show()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}