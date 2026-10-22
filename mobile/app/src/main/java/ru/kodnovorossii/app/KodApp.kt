package ru.kodnovorossii.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.osmdroid.config.Configuration
import java.io.File

class KodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Карты osmdroid: кэш тайлов во внутренней памяти — карта работает офлайн после загрузки области.
        val conf = Configuration.getInstance()
        conf.userAgentValue = packageName
        conf.osmdroidBasePath = File(filesDir, "osmdroid")
        conf.osmdroidTileCache = File(filesDir, "osmdroid/tiles")
        conf.tileFileSystemCacheMaxBytes = 600L * 1024 * 1024
        conf.tileFileSystemCacheTrimBytes = 500L * 1024 * 1024

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
