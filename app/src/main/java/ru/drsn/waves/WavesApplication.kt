package ru.drsn.waves

import android.app.Application
import ru.drsn.waves.signaling.SignalingServiceImpl
import timber.log.Timber

class WavesApplication : Application() {

    lateinit var signalingService: SignalingServiceImpl
        private set

    override fun onCreate() {

        // Инициализация Timber в зависимости от типа сборки
        if (!BuildConfig.RELEASE) {
            // В релизной версии логирование отключено
            Timber.plant(Timber.DebugTree())
        }

        super.onCreate()

        signalingService = SignalingServiceImpl()
    }
}