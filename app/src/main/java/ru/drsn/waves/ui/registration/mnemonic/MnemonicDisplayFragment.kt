package ru.drsn.waves.ui.registration.mnemonic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.drsn.waves.databinding.FragmentMnemonicDisplayBinding // Нужен layout для фрагмента
import ru.drsn.waves.ui.registration.RegistrationFlowUiState
import ru.drsn.waves.ui.registration.RegistrationFlowViewModel
import timber.log.Timber

@AndroidEntryPoint
class MnemonicDisplayFragment : Fragment() {
    private val viewModel: RegistrationFlowViewModel by activityViewModels()
    private var _binding: FragmentMnemonicDisplayBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "MnemonicDisplayFragment"
        fun newInstance() = MnemonicDisplayFragment()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMnemonicDisplayBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("MnemonicDisplayFragment: onViewCreated")

        binding.backButtonMnemonic.setOnClickListener { viewModel.returnToNicknameEntry() }
        binding.nextButtonText.setOnClickListener { viewModel.onProceedToMnemonicVerification() }
        binding.copyMnemonicButton.setOnClickListener {
            val mnemonicText = binding.mnemonicTextView.text.toString()
            if (mnemonicText.isNotBlank()) {
                val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("MnemonicPhrase", mnemonicText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Мнемоника скопирована!", Toast.LENGTH_SHORT).show()
            }
        }
        observeViewModelState()
    }
    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    Timber.d("MnemonicDisplayFragment: Состояние UI: $state")
                    if (state is RegistrationFlowUiState.MnemonicDisplayStep) {
                        val words = state.mnemonic.value.split(" ")
                        val formattedMnemonic = words.chunked(3).joinToString(separator = "\n") { it.joinToString(" ") }
                        binding.mnemonicTextView.text = formattedMnemonic
                    } else if (state is RegistrationFlowUiState.Error) {
                        Toast.makeText(requireContext(), "Ошибка: ${state.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}