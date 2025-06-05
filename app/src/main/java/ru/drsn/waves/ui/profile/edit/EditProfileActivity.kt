package ru.drsn.waves.ui.profile.edit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.drsn.waves.R
import ru.drsn.waves.databinding.ActivityEditProfileBinding
import timber.log.Timber

@AndroidEntryPoint
class EditProfileActivity : AppCompatActivity() {

    private val viewModel: EditProfileViewModel by viewModels()
    private lateinit var binding: ActivityEditProfileBinding

    // ActivityResultLauncher для выбора изображения
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedImageUri: Uri? = result.data?.data
            if (selectedImageUri != null) {
                Timber.d("Выбрано изображение для аватара: $selectedImageUri")
                // TODO: viewModel.onAvatarSelected(selectedImageUri)
                // Пока просто отобразим его
                viewModel.onAvatarUriChanged(selectedImageUri, this.contentResolver, this.filesDir)
                Glide.with(this)
                    .load(selectedImageUri)
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(binding.editProfileAvatarImageView)
                 // Пример обновления URI в ViewModel
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Timber.d("EditProfileActivity создана")

        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarEditProfile)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Заголовок уже установлен в XML
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Можно добавить кнопку "Сохранить" в меню тулбара
        // menuInflater.inflate(R.menu.edit_profile_toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            // R.id.action_save_profile -> {
            //    viewModel.saveProfile()
            //    true
            // }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupListeners() {
        binding.displayNameEditText.addTextChangedListener { text ->
            viewModel.onDisplayNameChanged(text.toString())
        }
        binding.statusMessageEditText.addTextChangedListener { text ->
            viewModel.onStatusMessageChanged(text.toString())
        }
        binding.changeAvatarButton.setOnClickListener {
            viewModel.onChangeAvatarClicked()
            openImagePicker()
        }
        binding.saveProfileButton.setOnClickListener {
            viewModel.saveProfile()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Timber.d("EditProfileActivity: Новое состояние UI: $state")
                    binding.editProfileProgressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.saveProfileButton.isEnabled = !state.isLoading

                    if (binding.displayNameEditText.text.toString() != state.displayName) {
                        binding.displayNameEditText.setText(state.displayName)
                        binding.displayNameEditText.setSelection(state.displayName.length)
                    }
                    if (binding.statusMessageEditText.text.toString() != state.statusMessage) {
                        binding.statusMessageEditText.setText(state.statusMessage)
                        // binding.statusMessageEditText.setSelection(state.statusMessage.length) // Для многострочного может быть не всегда удобно
                    }

                    // Отображение аватара (если URI изменился во ViewModel)
                    if (state.avatarUri != null) {
                        Glide.with(this@EditProfileActivity)
                            .load(state.avatarUri) // Может быть локальный URI или URL
                            .circleCrop()
                            .placeholder(R.drawable.ic_default_avatar)
                            .into(binding.editProfileAvatarImageView)
                    } else {
                        binding.editProfileAvatarImageView.setImageResource(R.drawable.ic_default_avatar)
                    }


                    state.error?.let { errorMsg ->
                        Toast.makeText(this@EditProfileActivity, errorMsg, Toast.LENGTH_LONG).show()
                        // TODO: Сбросить ошибку во ViewModel после показа, чтобы не показывать снова при повороте
                    }

                    if (state.saveSuccess) {
                        Toast.makeText(this@EditProfileActivity, "Профиль успешно сохранен!", Toast.LENGTH_SHORT).show()
                        // Можно закрыть Activity или перейти назад
                        // finish()
                    }
                }
            }
        }
    }
}