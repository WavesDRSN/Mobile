package ru.drsn.waves

import android.app.Application
import timber.log.Timber

class WavesApplication : Application() {
    override fun onCreate() {

        // Инициализация Timber в зависимости от типа сборки
        if (!BuildConfig.RELEASE) {
            // В релизной версии логирование отключено
            Timber.plant(Timber.DebugTree())
        }
        super.onCreate()
    }
}