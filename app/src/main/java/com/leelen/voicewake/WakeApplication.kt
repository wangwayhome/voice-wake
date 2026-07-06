package com.leelen.voicewake

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class WakeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            WAKE_CHANNEL_ID,
            "语音唤醒",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "小立管家语音唤醒服务"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val WAKE_CHANNEL_ID = "wake_service_channel"
    }
}
