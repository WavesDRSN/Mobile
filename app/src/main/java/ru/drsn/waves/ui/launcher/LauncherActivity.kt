package ru.drsn.waves.ui.launcher

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import ru.drsn.waves.ui.chatlist.ChatListActivity
import ru.drsn.waves.ui.welcome.WelcomeActivity


@AndroidEntryPoint
@SuppressLint("CustomSplashScreen")
class LauncherActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SplashScreenContent()
                    val command by viewModel.navigationCommand.collectAsState()
                    LaunchedEffect(command) {
                        command?.let {
                            when (it) {
                                is NavigationCommand.ToMainApp -> {
                                    //TODO: CHAT LIST ACTIVITY
                                    startActivity(Intent(this@LauncherActivity, ChatListActivity::class.java))
                                    finish()
                                }
                                is NavigationCommand.ToWelcome -> {
                                    val intent = Intent(this@LauncherActivity, WelcomeActivity::class.java)
                                    intent.putExtra(WelcomeActivity.EXTRA_SHOW_AUTH_ERROR, it.showError)
                                    it.errorMessage?.let { msg -> intent.putExtra(WelcomeActivity.EXTRA_AUTH_ERROR_MESSAGE, msg) }
                                    startActivity(intent)
                                    finish()
                                }
                            }
                            viewModel.resetNavigation()
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) {
            viewModel.decideNextScreen()
        }
    }
}