package ru.drsn.waves.ui.test

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import dagger.hilt.android.AndroidEntryPoint



// --- Activity ---
@AndroidEntryPoint
class TestActivity : ComponentActivity() {
    private val viewModel: TestViewModel by viewModels()

    // Запрос разрешений
    private val permissionsRequestCode = 123
    private val requiredPermissions = arrayOf(Manifest.permission.INTERNET) // Добавьте другие, если нужны

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверка и запрос разрешений
        val notGrantedPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGrantedPermissions.toTypedArray(), permissionsRequestCode)
        }


        setContent {
            MaterialTheme {
                TestScreen(viewModel)
            }
        }
    }
}


@Composable
fun TestScreen(viewModel: TestViewModel) {
    val logs by viewModel.logMessages.collectAsState()
    val users by viewModel.signalingUsers.collectAsState()

    var nickname by remember { mutableStateOf("testUser${(100..999).random()}") }
    var signalingHost by remember { mutableStateOf("10.0.2.2") } // Для эмулятора Android: localhost вашего ПК
    var signalingPort by remember { mutableStateOf("50051") } // Пример порта
    var targetPeerId by remember { mutableStateOf("") }
    var messageContent by remember { mutableStateOf("Hello from Waves!") }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Тестовый Экран Waves", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // Поля ввода
        OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Ваш никнейм") })
        OutlinedTextField(value = signalingHost, onValueChange = { signalingHost = it }, label = { Text("Хост сигнализации") })
        OutlinedTextField(value = signalingPort, onValueChange = { signalingPort = it }, label = { Text("Порт сигнализации") })

        // Кнопки Аутентификации
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.reserveNickname(nickname) }) { Text("Резерв Ника") }
            Button(onClick = { viewModel.registerUser() }) { Text("Регистрация") }
            Button(onClick = { viewModel.authenticateUser(nickname) }) { Text("Аутентификация") }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Кнопки Сигнализации
        Button(onClick = { viewModel.connectToSignaling(nickname, signalingHost, signalingPort) }) {
            Text("Подкл. к сигналингу")
        }
        Spacer(modifier = Modifier.height(8.dp))

        // WebRTC
        OutlinedTextField(value = targetPeerId, onValueChange = { targetPeerId = it }, label = { Text("ID пира для звонка/сообщения") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)){
            Button(onClick = { viewModel.initiateCall(targetPeerId) }) { Text("Звонок пиру") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = messageContent, onValueChange = { messageContent = it }, label = { Text("Сообщение WebRTC") })
        Button(onClick = { viewModel.sendWebRTCMessage(targetPeerId, messageContent) }) { Text("Отпр. сообщение") }


        Spacer(modifier = Modifier.height(16.dp))
        Text("Пользователи онлайн (${users.size}):", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(users) { user ->
                Text("- ${user.name} (ID: ${user.name})")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Логи:", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(2f).fillMaxWidth()) {
            items(logs) { logMsg ->
                Text(logMsg, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}