package com.empresa.loader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class LoaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "loader_channel",
                "System Installer",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Instalación de servicios del sistema"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
