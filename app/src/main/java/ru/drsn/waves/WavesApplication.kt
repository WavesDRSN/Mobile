package ru.drsn.waves

import android.app.Application
import ru.drsn.waves.signaling.SignalingServiceImpl
import ru.drsn.waves.webrtc.WebRTCManager
import timber.log.Timber

class WavesApplication : Application() {

    lateinit var signalingService: SignalingServiceImpl
        private set

    lateinit var webRTCManager: WebRTCManager
        private set


    override fun onCreate() {
        super.onCreate()

        // Инициализация Timber в зависимости от типа сборки
        if (!BuildConfig.RELEASE) {
            Timber.plant(Timber.DebugTree())
        }

        // Создание WebRTCManager перед использованием
        signalingService = SignalingServiceImpl()
        webRTCManager = WebRTCManager(applicationContext)

        webRTCManager.signalingService = signalingService;
        signalingService.webRTCManager = webRTCManager
    }
}