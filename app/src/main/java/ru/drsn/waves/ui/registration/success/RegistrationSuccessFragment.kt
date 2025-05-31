package ru.drsn.waves.ui.registration.success

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.drsn.waves.databinding.FragmentRegistrationSuccessBinding // Нужен layout для фрагмента
import ru.drsn.waves.ui.chatlist.ChatListActivity
import ru.drsn.waves.ui.registration.RegistrationFlowEvent
import ru.drsn.waves.ui.registration.RegistrationFlowViewModel
import timber.log.Timber

@AndroidEntryPoint
class RegistrationSuccessFragment : Fragment() {
    private val viewModel: RegistrationFlowViewModel by activityViewModels()
    private var _binding: FragmentRegistrationSuccessBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "RegistrationSuccessFragment"
        fun newInstance() = RegistrationSuccessFragment()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegistrationSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toAppButton.setOnClickListener {
            viewModel.onRegistrationCompleteAndNavigate()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.event.collectLatest { event ->
                if (event is RegistrationFlowEvent.NavigateToChatList) {
                    Timber.i("Навигация на ChatListActivity из RegistrationSuccessFragment")
                    val intent = Intent(requireContext(), ChatListActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}