package ru.drsn.waves

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.security.Security

@HiltAndroidApp
class WavesApplication : Application() {


    override fun onCreate() {
        super.onCreate()

        // Инициализация Timber в зависимости от типа сборки
        if (!BuildConfig.RELEASE) {
            Timber.plant(Timber.DebugTree())
        }

        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(BouncyCastleProvider())

    }
}