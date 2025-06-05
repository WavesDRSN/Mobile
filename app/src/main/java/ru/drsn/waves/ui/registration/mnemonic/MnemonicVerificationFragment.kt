package ru.drsn.waves.ui.registration.mnemonic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.drsn.waves.databinding.FragmentMnemonicVerificationBinding // Нужен layout для фрагмента
import ru.drsn.waves.ui.registration.RegistrationFlowUiState
import ru.drsn.waves.ui.registration.RegistrationFlowViewModel
import ru.drsn.waves.ui.registration.RegistrationStep
import timber.log.Timber

@AndroidEntryPoint
class MnemonicVerificationFragment : Fragment() {
    private val viewModel: RegistrationFlowViewModel by activityViewModels()
    private var _binding: FragmentMnemonicVerificationBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "MnemonicVerificationFragment"
        fun newInstance() = MnemonicVerificationFragment()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMnemonicVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("MnemonicVerificationFragment: onViewCreated")

        binding.backButtonVerify.setOnClickListener {
            viewModel.returnToNicknameEntry()
        }

        binding.verifyButton.setOnClickListener {
            val words = binding.word3EditText.text.toString().trim().split(" ")
            if (words.size < 3) {
                binding.verificationErrorTextView.text = "Пожалуйста, введите все 3 слова."
                binding.verificationErrorTextView.visibility = View.VISIBLE
                return@setOnClickListener
            }
            binding.verificationErrorTextView.visibility = View.GONE
            viewModel.onVerifyMnemonicAndRegister(words)
        }

        observeViewModelState()
    }
    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    Timber.d("MnemonicVerificationFragment: Состояние UI: $state")
                    binding.progressBarVerify.visibility =
                        if (state is RegistrationFlowUiState.Loading) View.VISIBLE else View.GONE
                    binding.verifyButton.isEnabled = state !is RegistrationFlowUiState.Loading

                    when (state) {
                        is RegistrationFlowUiState.Error -> {
                            if (state.step == RegistrationStep.MNEMONIC_VERIFICATION || state.step == RegistrationStep.FINAL_REGISTRATION) {
                                binding.verificationErrorTextView.text = state.message
                                binding.verificationErrorTextView.visibility = View.VISIBLE
                            }
                        }

                        is RegistrationFlowUiState.MnemonicVerificationStep -> {
                            binding.verificationErrorTextView.visibility = View.GONE
                        }

                        else -> {}
                    }
                }
            }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}