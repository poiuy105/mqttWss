package io.emqx.mqtt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MqttService : Service() {
    companion object {
        const val CHANNEL_ID = "mqtt_service_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "io.emqx.mqtt.START_SERVICE"
        const val ACTION_STOP = "io.emqx.mqtt.STOP_SERVICE"
        const val ACTION_UPDATE_STATUS = "io.emqx.mqtt.UPDATE_STATUS"

        private var instance: MqttService? = null

        fun startService(context: Context) {
            val intent = Intent(context, MqttService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MqttService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateStatus(context: Context, title: String, message: String) {
            val intent = Intent(context, MqttService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                putExtra("title", title)
                putExtra("message", message)
            }
            context.startService(intent)
        }

        fun isPersistentNotificationEnabled(context: Context): Boolean {
            return ConfigManager.getInstance(context).persistentNotification
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                instance = null
            }
            ACTION_UPDATE_STATUS -> {
                if (isPersistentNotificationEnabled(this)) {
                    val title = intent.getStringExtra("title") ?: "MQTT"
                    val message = intent.getStringExtra("message") ?: ""
                    updateNotification(title, message)
                }
            }
            else -> {
                if (isPersistentNotificationEnabled(this)) {
                    startForeground(NOTIFICATION_ID, createNotification())
                } else {
                    stopSelf()
                    instance = null
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MQTT Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MQTT connection status"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT Connected")
            .setContentText("MQTT client is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateNotification(title: String, message: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
