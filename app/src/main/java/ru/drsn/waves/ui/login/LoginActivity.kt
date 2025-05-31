package ru.drsn.waves.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.drsn.waves.databinding.ActivityLogInBinding
import ru.drsn.waves.ui.chatlist.ChatListActivity
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity @Inject constructor(

) : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()
    private lateinit var binding: ActivityLogInBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeViewModel()

        binding.loginButton.setOnClickListener {
            viewModel.authAttempt(binding.loginEditText.text.toString(), binding.mnemonicEditText.text.toString())
        }


        Timber.d("LoginActivity создана")
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->

                        binding.progressBarNickname.visibility =
                            if (state is LoginUiState.Loading) View.VISIBLE else View.GONE

                        if (state is LoginUiState.Error) {
                            Toast.makeText(
                                this@LoginActivity,
                                "Ошибка авторизации.\n${state.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                launch {
                    viewModel.navigationCommand.collect { command ->
                        if (command is NavigationCommand.ToMainApp) {
                            Timber.i("Навигация на ChatListActivity из LoginActivity")
                            val intent = Intent(this@LoginActivity, ChatListActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }
    }

}