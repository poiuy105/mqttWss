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
        const val ACTION_UPDATE_CONNECTION = "io.emqx.mqtt.UPDATE_CONNECTION"

        private var instance: MqttService? = null
        private var isConnected = false

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

        fun updateConnectionStatus(context: Context, connected: Boolean) {
            isConnected = connected
            val intent = Intent(context, MqttService::class.java).apply {
                action = ACTION_UPDATE_CONNECTION
                putExtra("connected", connected)
            }
            try {
                // Android 12+ 限制后台启动服务，如果 App 在后台可能会抛出异常
                // 这种情况下，我们依赖已有的前台服务继续运行即可
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // 如果服务已经在运行且 App 在后台，更新通知可能会失败
                // 这不影响 MQTT 连接的正常运行
                android.util.Log.w("MqttService", "Failed to update connection status (App in background): ${e.message}")
            }
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
                isConnected = false
            }
            ACTION_UPDATE_STATUS -> {
                if (isPersistentNotificationEnabled(this)) {
                    val title = intent.getStringExtra("title") ?: "MQTT"
                    val message = intent.getStringExtra("message") ?: ""
                    updateNotification(title, message)
                }
            }
            ACTION_UPDATE_CONNECTION -> {
                val connected = intent.getBooleanExtra("connected", false)
                isConnected = connected
                if (isPersistentNotificationEnabled(this)) {
                    if (connected) {
                        updateNotification("MQTT 已连接", "MQTT client is running")
                    } else {
                        updateNotification("MQTT 未连接", "MQTT client disconnected")
                    }
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
            Intent(this, MainActivity::class.java).apply {
                // 重要：添加 FLAG_ACTIVITY_NEW_TASK 以支持从通知栏启动
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
            Intent(this, MainActivity::class.java).apply {
                // 重要：添加 FLAG_ACTIVITY_NEW_TASK 以支持从通知栏启动
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
