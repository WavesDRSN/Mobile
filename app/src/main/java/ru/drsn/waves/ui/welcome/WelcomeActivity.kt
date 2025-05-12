package ru.drsn.waves.ui.welcome


import android.content.Intent // Для примера навигации
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ru.drsn.waves.databinding.ActivityWelcomeBinding // Сгенерированный класс ViewBinding
// import com.yourcompany.yourapp.features.login.LoginActivity // Импорт следующего экрана
// import com.yourcompany.yourapp.features.registration.RegistrationActivity // Импорт следующего экрана
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint // Обязательная аннотация для Hilt
class WelcomeActivity : AppCompatActivity() {

    // Получаем экземпляр ViewModel с помощью Hilt и KTX делегата
    private val viewModel: WelcomeViewModel by viewModels()

    // ViewBinding для безопасного доступа к View
    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener { // ID кнопки "Вход" из вашего layout
            viewModel.onLoginClicked()
        }
        binding.registerButton.setOnClickListener { // ID кнопки "Регистрация" из вашего layout
            viewModel.onRegistrationClicked()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            // repeatOnLifecycle гарантирует, что подписка активна только когда Activity
            // находится как минимум в состоянии STARTED и автоматически отменяется при STOPPED.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Запускаем корутину для наблюдения за состоянием UI
                launch {
                    viewModel.uiState.collect { state ->
                        handleState(state)
                    }
                }

                // Запускаем корутину для наблюдения за событиями
                launch {
                    viewModel.event.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    // Обработка состояний UI
    private fun handleState(state: WelcomeUiState) {
        when (state) {
            is WelcomeUiState.Initial -> {
                // Установить начальное состояние UI, если нужно
                // Например, скрыть ProgressBar, показать кнопки
            }
            // Добавить обработку других состояний (Loading, Success, Error)
            // is WelcomeUiState.Loading -> { binding.progressBar.visibility = View.VISIBLE }
            // is WelcomeUiState.Error -> { showToast(state.message) }
        }
    }

    // Обработка одноразовых событий
    private fun handleEvent(event: WelcomeEvent) {
        when (event) {
            is WelcomeEvent.NavigateToLogin -> {
                // Переход на экран входа
                // val intent = Intent(this, LoginActivity::class.java)
                // startActivity(intent)
                // Toast.makeText(this, "Переход на Вход", Toast.LENGTH_SHORT).show() // Заглушка
            }
            is WelcomeEvent.NavigateToRegistration -> {
                // Переход на экран регистрации
                // val intent = Intent(this, RegistrationActivity::class.java)
                // startActivity(intent)
                // Toast.makeText(this, "Переход на Регистрацию", Toast.LENGTH_SHORT).show() // Заглушка
            }
            // Добавить обработку других событий (показ Toast, Snackbar и т.д.)
            // is WelcomeEvent.ShowError -> { showToast(event.message) }
        }
    }

    // Пример функции для показа Toast (можно вынести в базовый класс или утилиты)
    // private fun showToast(message: String) {
    //     Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    // }
}