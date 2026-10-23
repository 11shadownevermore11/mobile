package ru.kodnovorossii.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import java.io.File

class KodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Старый кэш тайлов osmdroid больше не нужен: карта региона встроена в приложение (assets/map)
        File(filesDir, "osmdroid").takeIf { it.exists() }?.deleteRecursively()

        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_GEO, "Места рядом", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Гео-уведомления о местах из избранного и по интересам"
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_GEO = "geo"
    }
}
