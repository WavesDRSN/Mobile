package ru.drsn.waves.ui.profile

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
import ru.drsn.waves.databinding.ActivityProfileBinding
import ru.drsn.waves.ui.profile.edit.EditProfileActivity
import timber.log.Timber


@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ProfileActivity::class.java)

    }

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Timber.d("ProfileActivity создана")

        setupToolbar()
        observeViewModel()

        binding.button.setOnClickListener{
            startActivity(Intent(this, EditProfileActivity::class.java))
            finish()
        }
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
                            if (state is ProfileUiState.Loading) View.VISIBLE else View.GONE

                        when (state) {
                            is ProfileUiState.Success -> {
                                val profile = state.profile
                                binding.toolbarTitle.text = profile.userId

                                if (profile.avatarUri != null) {
                                    val imageUri = Uri.parse(profile.avatarUri)
                                    if (imageUri != null) binding.profileInfoLayout.profileAvatarView.setImageURI(imageUri)
                                }

                                binding.profileInfoLayout.nameTextView.text = profile.displayName
                                binding.aboutChatLayout.userIdValue.text = "@" + profile.userId
                                binding.aboutChatLayout.bioValue.text = profile.statusMessage
                                if (profile.statusMessage.isNullOrBlank()) {
                                    binding.aboutChatLayout.bioLabel.visibility = View.GONE
                                    binding.aboutChatLayout.bioValue.visibility = View.GONE
                                }

                            }
                            is ProfileUiState.Error -> {
                                Toast.makeText(this@ProfileActivity, state.message, Toast.LENGTH_LONG).show()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_chat_menu, menu)
        return true
    }
}