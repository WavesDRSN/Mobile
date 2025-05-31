package ru.drsn.waves.ui.registration.nickname

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.drsn.waves.databinding.FragmentNicknameEntryBinding
import ru.drsn.waves.ui.registration.RegistrationFlowUiState
import ru.drsn.waves.ui.registration.RegistrationFlowViewModel
import ru.drsn.waves.ui.registration.RegistrationStep
import timber.log.Timber


@AndroidEntryPoint
class NicknameEntryFragment : Fragment() {

    private val viewModel: RegistrationFlowViewModel by activityViewModels() // Общая ViewModel
    private var _binding: FragmentNicknameEntryBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView.

    companion object {
        const val TAG = "NicknameEntryFragment"
        fun newInstance() = NicknameEntryFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNicknameEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("NicknameEntryFragment: onViewCreated")

        binding.termsCheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.continueButton.isEnabled = isChecked && binding.nicknameEditText.text.toString().isNotBlank()
        }

        binding.nicknameEditText.addTextChangedListener {
            binding.continueButton.isEnabled = binding.termsCheckBox.isChecked && it.toString().isNotBlank()
            binding.nicknameErrorTextView.visibility = View.GONE
        }

        binding.continueButton.setOnClickListener {
            val nickname = binding.nicknameEditText.text.toString().trim()
            viewModel.onNicknameEntered(nickname)
        }

        binding.loginLinkTextView.setOnClickListener {
            // TODO: Навигация на экран входа (SeedPhraseEntryActivity)
            // Лучше через событие ViewModel, чтобы хост-активити выполнила навигацию
            // viewModel.onNavigateToLoginScreenRequested()
            Toast.makeText(requireContext(), "Переход на Вход (TODO)", Toast.LENGTH_SHORT).show()
        }

        observeViewModelState()
    }

    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    Timber.d("NicknameEntryFragment: Состояние UI: $state")
                    binding.progressBarNickname.visibility = if (state is RegistrationFlowUiState.Loading &&
                        (viewModel.uiState.value as? RegistrationFlowUiState.Error)?.step != RegistrationStep.NICKNAME_RESERVATION) View.VISIBLE else View.GONE

                    binding.continueButton.isEnabled = !(state is RegistrationFlowUiState.Loading) &&
                            binding.termsCheckBox.isChecked &&
                            binding.nicknameEditText.text.toString().isNotBlank()


                    if (state is RegistrationFlowUiState.Error && (state.step == RegistrationStep.NICKNAME_RESERVATION || state.step == null)) {
                        binding.nicknameErrorTextView.text = state.message
                        binding.nicknameErrorTextView.visibility = View.VISIBLE
                    } else if (state !is RegistrationFlowUiState.Loading) { // Скрываем ошибку, если не загрузка и не ошибка ника
                        binding.nicknameErrorTextView.visibility = View.GONE
                    }

                    // Навигация на следующий шаг (MnemonicDisplayStep) будет обработана в HostActivity
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Очищаем ссылку на биндинг
    }
}