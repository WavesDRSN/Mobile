package ru.drsn.waves.ui.welcome


import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.drsn.waves.databinding.ActivityWelcomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.drsn.waves.ui.registration.host.RegistrationFlowHostActivity
import timber.log.Timber

@AndroidEntryPoint
class WelcomeActivity : AppCompatActivity() {

    private val viewModel: WelcomeViewModel by viewModels()
    private lateinit var binding: ActivityWelcomeBinding

    companion object {
        const val EXTRA_SHOW_AUTH_ERROR = "showAuthError"
        const val EXTRA_AUTH_ERROR_MESSAGE = "authErrorMessage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Timber.d("WelcomeActivity создана.")

        // Получаем данные из Intent (от LauncherActivity)
        val showError = intent.getBooleanExtra(EXTRA_SHOW_AUTH_ERROR, false)
        val errorMessage = intent.getStringExtra(EXTRA_AUTH_ERROR_MESSAGE)
        viewModel.processLaunchError(showError, errorMessage)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            Timber.d("Нажата кнопка 'Вход' (по сид-фразе)")
            viewModel.onLoginClicked()
        }
        binding.registerButton.setOnClickListener {
            Timber.d("Нажата кнопка 'Регистрация'")
            viewModel.onRegistrationClicked()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        Timber.d("Новое состояние UI: $state")
                        handleState(state)
                    }
                }
                launch {
                    viewModel.event.collect { event ->
                        Timber.d("Новое событие: $event")
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun handleState(state: WelcomeUiState) {
        when (state) {
            is WelcomeUiState.Initial -> {
                // Начальное состояние, ничего особенного не делаем
                Timber.d("Состояние UI: Initial")
            }
            is WelcomeUiState.ShowAuthFailureDialog -> {
                Timber.d("Состояние UI: Показ диалога ошибки - ${state.message}")
                showAuthErrorDialog(state.message)
            }
        }
    }

    private fun handleEvent(event: WelcomeEvent) {
        when (event) {
            is WelcomeEvent.NavigateToLogin -> {
                Timber.i("Навигация на LoginActivity")
                //TODO: Создать Login Activity
                // startActivity(Intent(this, LoginActivity::class.java))

                // finish() // Не закрываем WelcomeActivity, чтобы пользователь мог вернуться
            }
            is WelcomeEvent.NavigateToRegistration -> {
                Timber.i("Навигация на RegistrationFlowActivity")
                //TODO: Создать Registration Activity
                startActivity(Intent(this, RegistrationFlowHostActivity::class.java))

                // finish() // Аналогично, не закрываем
            }
        }
    }

    private fun showAuthErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ошибка входа")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                viewModel.onErrorDialogDismissed() // Сообщаем ViewModel, что диалог закрыт
                dialog.dismiss()
            }
            .setOnCancelListener { // Если пользователь нажал вне диалога или кнопку "Назад"
                viewModel.onErrorDialogDismissed()
            }
            .show()
    }
}
