package ru.drsn.waves

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import ru.drsn.waves.signaling.SignalingServiceImpl
import ru.drsn.waves.webrtc.SdpObserver
import ru.drsn.waves.webrtc.WebRTCManager
import ru.drsn.waves.webrtc.utils.DataModelType
import timber.log.Timber
import java.nio.ByteBuffer
import kotlin.random.Random

class WebRTCActivity : AppCompatActivity() {
    private lateinit var signalingService: SignalingServiceImpl
    private lateinit var webRTCManager: WebRTCManager
    private lateinit var username: String
    private lateinit var targetUser: String

    private lateinit var editTextTarget: EditText
    private lateinit var editTextMessage: EditText
    private lateinit var btnConnectServer: Button
    private lateinit var btnRequestConnection: Button
    private lateinit var btnSendMessage: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webrtc)

        editTextTarget = findViewById(R.id.editTextTarget)
        editTextMessage = findViewById(R.id.editTextMessage)
        btnConnectServer = findViewById(R.id.btnConnectServer)
        btnRequestConnection = findViewById(R.id.btnRequestConnection)
        btnSendMessage = findViewById(R.id.btnSendMessage)

        signalingService = (application as WavesApplication).signalingService
        username = "User_" + Random.nextInt(1000)
        webRTCManager = (application as WavesApplication).webRTCManager

        btnConnectServer.setOnClickListener {
            connectToServer()
        }

        btnRequestConnection.setOnClickListener {
            targetUser = editTextTarget.text.toString().trim()
            if (targetUser.isNotEmpty()) {
                requestConnection(targetUser)
            }
        }

        btnSendMessage.setOnClickListener {
            val message = editTextMessage.text.toString()
            if (message.isNotEmpty() && ::targetUser.isInitialized) {
                sendMessage(targetUser, message)
            }
        }
    }

    private fun connectToServer() {
        lifecycleScope.launch {
            try {
                signalingService.connect(username, "tt.vld.su", 50051)
                Toast.makeText(this@WebRTCActivity, "Подключено к серверу!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Ошибка подключения к серверу")
            }
        }
    }

    private fun requestConnection(target: String) {
        webRTCManager.call(target)
    }

    private fun sendMessage(target: String, message: String) {
        webRTCManager.sendMessage(target, username, message)
    }
}
