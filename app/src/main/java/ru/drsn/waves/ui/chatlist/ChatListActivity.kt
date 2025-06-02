package ru.drsn.waves.ui.chatlist

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.get
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.drsn.waves.R
import ru.drsn.waves.databinding.ActivityChatListBinding
import ru.drsn.waves.databinding.NavHeaderMainBinding
import ru.drsn.waves.ui.chat.ChatActivity
import ru.drsn.waves.ui.profile.ProfileActivity
import timber.log.Timber

@AndroidEntryPoint
class ChatListActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val viewModel: ChatListViewModel by viewModels()
    private lateinit var binding: ActivityChatListBinding
    private lateinit var navViewHeaderBinding: NavHeaderMainBinding
    private lateinit var chatListAdapter: ChatListAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        navViewHeaderBinding = NavHeaderMainBinding.inflate(layoutInflater)
        setContentView(binding.root) // Убедись, что это корневой элемент DrawerLayout
        Timber.d("ChatListActivity создана")

        setupToolbarAndDrawer() // Объединил настройку тулбара и drawer
        setupProfileInfo()
        setupRecyclerView()
        setupBottomNavigation()
        setupSearch()
        observeViewModel()
    }

    private fun setupProfileInfo() {
        lifecycleScope.launch {
            val profile = viewModel.getProfileDomainModel() ?:
            return@launch

            Glide.with(this@ChatListActivity)
                .load(profile?.avatarUri)
                .placeholder(R.drawable.ic_default_profile)
                .error(R.drawable.ic_default_profile)
                .centerCrop()
                .into(binding.navView.getHeaderView(0).findViewById(R.id.profileAvatarView))

            binding.navView.getHeaderView(0).findViewById<TextView>(R.id.user_id_header).text = profile.userId
            binding.navView.getHeaderView(0).findViewById<TextView>(R.id.user_name_header).text = profile.displayName


        }
    }

    @Override
    override fun onResume() {
        super.onResume()
        setupProfileInfo()
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar) // Используем Toolbar из AppBarLayout
        supportActionBar?.title = ""

        drawerLayout = binding.drawerLayout // Получаем DrawerLayout из биндинга
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_chat_list_menu, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) { // Даем ActionBarDrawerToggle обработать нажатие
            return true
        }
        return when (item.itemId) {
            R.id.action_profile -> {
                val intent = ProfileActivity.newIntent(this@ChatListActivity)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        chatListAdapter = ChatListAdapter { session ->
            viewModel.onChatSessionClicked(session)
        }
        binding.chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatListActivity)
            adapter = chatListAdapter
            // Можно добавить ItemDecoration для разделителей, если нужно
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_chats -> true
                /*
                R.id.navigation_calls -> {
                    Toast.makeText(this, "Переход на Звонки", Toast.LENGTH_SHORT).show()
                    true
                }*/
                R.id.navigation_profile -> {
                    val intent = ProfileActivity.newIntent(this@ChatListActivity)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        binding.bottomNavigationView.selectedItemId = R.id.navigation_chats
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onSearchQueryChanged(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }


    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        Timber.d("Новое состояние UI в ChatListActivity: $state")
                        // TODO: Управлять видимостью ProgressBar и текста для пустого списка/ошибки
                        // binding.progressBarChatList.visibility = if (state is ChatListUiState.Loading) View.VISIBLE else View.GONE
                        // binding.emptyTextViewChatList.visibility = if (state is ChatListUiState.Empty) View.VISIBLE else View.GONE

                        when (state) {
                            is ChatListUiState.Loading -> {
                                binding.chatsRecyclerView.visibility = View.GONE
                            }
                            is ChatListUiState.Success -> {
                                binding.chatsRecyclerView.visibility = View.VISIBLE
                                chatListAdapter.submitList(state.sessions)
                            }
                            is ChatListUiState.Error -> {
                                binding.chatsRecyclerView.visibility = View.GONE
                                Toast.makeText(this@ChatListActivity, state.message, Toast.LENGTH_LONG).show()
                            }
                            is ChatListUiState.Empty -> {
                                binding.chatsRecyclerView.visibility = View.GONE
                                Toast.makeText(this@ChatListActivity, "Нет доступных чатов", Toast.LENGTH_SHORT).show()
                                chatListAdapter.submitList(emptyList())
                            }
                            is ChatListUiState.MovedFromProfile -> {

                            }
                        }
                    }
                }

                launch {
                    viewModel.navigateToChatEvent.collect { session ->
                        Timber.i("Навигация на ChatActivity для сессии: ${session.sessionId}")
                        val intent = ChatActivity.newIntent(
                            this@ChatListActivity,
                            session.sessionId,
                            session.peerName,
                            session.chatType
                        )
                        startActivity(intent)
                    }
                }
                launch {
                    viewModel.showToastEvent.collect { message ->
                        Toast.makeText(this@ChatListActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_item_settings ->
            {
                val intent = ProfileActivity.newIntent(this@ChatListActivity)
                startActivity(intent)
                return true
            }
            // R.id.nav_item_logout -> { /* TODO: viewModel.logout() */ }
            R.id.nav_item_new_chat -> {
                Timber.d("Нажат пункт меню 'Создать чат'")
                showCreateChatDialog()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }


    private fun showCreateChatDialog() {
        val editText = EditText(this).apply {
            hint = "Введите никнейм пользователя"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Создать новый чат")
            .setView(editText)
            .setPositiveButton("Создать") { dialog, _ ->
                val peerNickname = editText.text.toString().trim()
                viewModel.onCreateChatClicked(peerNickname)
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}