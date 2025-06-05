package ru.drsn.waves.ui.registration.host


import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.drsn.waves.R
import ru.drsn.waves.databinding.ActivityRegistrationFlowHostBinding // Нужен новый layout для хоста
import ru.drsn.waves.ui.chatlist.ChatListActivity
import ru.drsn.waves.ui.login.LoginActivity
import ru.drsn.waves.ui.registration.RegistrationFlowEvent
import ru.drsn.waves.ui.registration.RegistrationFlowUiState
import ru.drsn.waves.ui.registration.RegistrationFlowViewModel
import ru.drsn.waves.ui.registration.mnemonic.MnemonicDisplayFragment
import ru.drsn.waves.ui.registration.mnemonic.MnemonicVerificationFragment
import ru.drsn.waves.ui.registration.nickname.NicknameEntryFragment
import ru.drsn.waves.ui.registration.success.RegistrationSuccessFragment
import timber.log.Timber

@AndroidEntryPoint
class RegistrationFlowHostActivity : AppCompatActivity() {

    private val viewModel: RegistrationFlowViewModel by viewModels() // ViewModel привязана к этой Activity
    private lateinit var binding: ActivityRegistrationFlowHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationFlowHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Timber.d("RegistrationFlowHostActivity создана")

        if (savedInstanceState == null) { // Показываем первый фрагмент только при первом создании
            // Начальное состояние ViewModel должно быть NicknameEntryStep
            // viewModel.resetToNicknameEntry() // Если нужно явно сбросить
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        Timber.d("RegistrationFlowHostActivity: Новое состояние UI: $state")
                        when (state) {
                            is RegistrationFlowUiState.NicknameEntryStep -> {
                                replaceFragment(NicknameEntryFragment.newInstance(), NicknameEntryFragment.TAG)
                            }
                            is RegistrationFlowUiState.MnemonicDisplayStep -> {
                                // Мнемоника уже во ViewModel, фрагмент ее получит
                                replaceFragment(MnemonicDisplayFragment.newInstance(), MnemonicDisplayFragment.TAG)
                            }
                            is RegistrationFlowUiState.MnemonicVerificationStep -> {
                                // Слова для проверки уже во ViewModel
                                replaceFragment(MnemonicVerificationFragment.newInstance(), MnemonicVerificationFragment.TAG)
                            }
                            is RegistrationFlowUiState.RegistrationSuccessStep -> {
                                replaceFragment(RegistrationSuccessFragment.newInstance(), RegistrationSuccessFragment.TAG)
                            }
                            is RegistrationFlowUiState.Loading -> {
                                // Можно показать общий ProgressBar на уровне Activity или делегировать фрагментам
                            }
                            is RegistrationFlowUiState.Error -> {
                                // Общие ошибки можно обрабатывать здесь или делегировать фрагментам
                                // Toast.makeText(this@RegistrationFlowHostActivity, "Ошибка: ${state.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.event.collectLatest { event ->
                        when (event) {
                            is RegistrationFlowEvent.NavigateToChatList -> {
                                Timber.i("Навигация на ChatListActivity из RegistrationFlowHostActivity")
                                val intent = Intent(this@RegistrationFlowHostActivity, ChatListActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                                finish()
                            }
                            is RegistrationFlowEvent.NavigateToLogin -> {
                                Timber.i("Навигация на LoginActivity из RegistrationFlowHostActivity")
                                val intent = Intent(this@RegistrationFlowHostActivity, LoginActivity::class.java).apply {
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

    private fun replaceFragment(fragment: Fragment, tag: String) {
        // Проверяем, не добавлен ли уже такой фрагмент, чтобы избежать лишних транзакций
        if (supportFragmentManager.findFragmentByTag(tag)?.isVisible == true) {
            Timber.d("Фрагмент $tag уже видим, замена не требуется.")
            return
        }
        Timber.d("Замена фрагмента на: $tag")
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container_registration, fragment, tag) // R.id.fragment_container_registration - ID контейнера в XML
            // .addToBackStack(null) // Добавляем в back stack, если нужна навигация назад между фрагментами
            .commit()
    }

    // Вы можете захотеть переопределить onBackPressed для навигации между фрагментами
    // или для вызова методов ViewModel (например, viewModel.onBackPressedOnMnemonicScreen())
}