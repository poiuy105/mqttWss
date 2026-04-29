package io.emqx.mqtt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken

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
                Log.w("MqttService", "Failed to update connection status (App in background): ${e.message}")
            }
        }
    }
    
    // ⭐ 新增：MQTT客户端实例（参考GPSLogger模式，Service中包含完整业务逻辑）
    private var mClient: org.eclipse.paho.client.mqttv3.MqttAsyncClient? = null
    private var mConnection: Connection? = null
    private var isConnecting = false
    
    // ⭐ P0修复：添加Binder以暴露Service实例给Activity
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): MqttService = this@MqttService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        
        // ⭐ 优化：立即提升为前台服务（参考GPSLogger模式，Android 8.0+必须在5秒内调用）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPersistentNotificationEnabled(this)) {
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d("MqttService", "Started as foreground service in onCreate")
        }
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
                // ⭐ 修复：不再自杀，以普通服务运行（参考GPSLogger模式）
                if (!isPersistentNotificationEnabled(this)) {
                    Log.d("MqttService", "Running without persistent notification")
                }
                // 注意：startForeground已在onCreate中调用，这里不需要重复调用
                
                // ⭐ 新增：直接在这里触发MQTT连接（参考GPSLogger模式）
                connectMqttInBackground()
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
    
    /**
     * ⭐ P0-1修复：获取MQTT客户端实例（供Activity使用）
     */
    fun getMqttClient(): org.eclipse.paho.client.mqttv3.MqttAsyncClient? {
        return mClient
    }
    
    /**
     * ⭐ P0-1修复：获取连接状态
     */
    fun isMqttConnected(): Boolean {
        return mClient?.isConnected == true
    }
    
    /**
     * ⭐ P0-1修复：公开的连接方法，供MainActivity调用
     */
    fun connect(connection: Connection, listener: IMqttActionListener?) {
        if (isConnecting) {
            Log.d("MqttService", "Already connecting, ignore")
            return
        }
        
        if (mClient != null && mClient!!.isConnected) {
            Log.d("MqttService", "Already connected, ignore duplicate connect request")
            return
        }
        
        Log.d("MqttService", "Starting MQTT connection from Activity...")
        isConnecting = true
        mConnection = connection
        mClient = connection.getMqttClient()
        
        try {
            mClient?.setCallback(object : org.eclipse.paho.client.mqttv3.MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.e("MqttService", "Connection lost: ${cause?.message}")
                    isConnected = false
                    updateConnectionStatus(this@MqttService, false)
                    
                    // ⭐ 修复：添加自动重连机制（与MainActivity保持一致）
                    val configManager = ConfigManager.getInstance(this@MqttService)
                    if (configManager.hasSavedConfig()) {
                        Log.d("MqttService", "Scheduling auto-reconnect in 5 seconds...")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!isConnected && !isConnecting) {
                                Log.d("MqttService", "Attempting auto-reconnect...")
                                connectMqttInBackground()
                            }
                        }, 5000)
                    }
                }
                
                override fun messageArrived(topic: String?, message: org.eclipse.paho.client.mqttv3.MqttMessage?) {
                    Log.d("MqttService", "Message arrived on topic: $topic")
                }
                
                override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {
                    Log.d("MqttService", "Delivery complete")
                }
            })
            
            Log.d("MqttService", "Calling connect()...")
            mClient?.connect(connection.mqttConnectOptions, null, listener)
        } catch (e: Exception) {
            isConnecting = false
            Log.e("MqttService", "Failed to connect: ${e.message}", e)
        }
    }
    
    /**
     * ⭐ P0-1修复：公开的断开连接方法，供MainActivity调用
     */
    fun disconnect() {
        try {
            if (mClient != null && mClient!!.isConnected) {
                Log.d("MqttService", "Disconnecting MQTT from Activity...")
                mClient?.disconnect()
            }
            mClient = null
            mConnection = null
            isConnected = false
            isConnecting = false
            updateConnectionStatus(this, false)
            Log.d("MqttService", "MQTT disconnected")
        } catch (e: Exception) {
            Log.e("MqttService", "Failed to disconnect: ${e.message}", e)
        }
    }
    
    /**
     * ⭐ 新增：在后台直接连接MQTT（参考GPSLogger模式）
     */
    private fun connectMqttInBackground() {
        if (isConnecting) {
            Log.d("MqttService", "Already connecting, ignore")
            return
        }
        
        if (mClient != null && mClient!!.isConnected) {
            Log.d("MqttService", "Already connected, ignore duplicate connect request")
            return
        }
        
        val configManager = ConfigManager.getInstance(this)
        
        // 只要有保存的配置就自动连接
        if (!configManager.hasSavedConfig()) {
            Log.w("MqttService", "No saved config found, skipping auto-connect")
            return
        }
        
        Log.d("MqttService", "Auto-connecting MQTT in background...")
        Log.d("MqttService", "Host: ${configManager.host}, Port: ${configManager.port}, Protocol: ${configManager.protocol}")
        
        isConnecting = true
        mConnection = Connection(
            this,
            configManager.host,
            configManager.port,
            configManager.clientId,
            configManager.username,
            configManager.password,
            configManager.protocol,
            configManager.path,
            configManager.allowUntrusted
        )
        
        try {
            mClient = mConnection!!.getMqttClient()
            mClient?.setCallback(object : org.eclipse.paho.client.mqttv3.MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.e("MqttService", "Connection lost: ${cause?.message}")
                    isConnected = false
                    updateConnectionStatus(this@MqttService, false)
                    
                    // ⭐ 修复：添加自动重连机制（与MainActivity保持一致）
                    val configManager = ConfigManager.getInstance(this@MqttService)
                    if (configManager.hasSavedConfig()) {
                        Log.d("MqttService", "Scheduling auto-reconnect in 5 seconds...")
                        // 使用Handler延迟重连
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!isConnected && !isConnecting) {
                                Log.d("MqttService", "Attempting auto-reconnect...")
                                connectMqttInBackground()
                            }
                        }, 5000)
                    }
                }
                
                override fun messageArrived(topic: String?, message: org.eclipse.paho.client.mqttv3.MqttMessage?) {
                    Log.d("MqttService", "Message arrived on topic: $topic")
                    // 这里可以处理接收到的消息
                }
                
                override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {
                    Log.d("MqttService", "Delivery complete")
                }
            })
            
            Log.d("MqttService", "Calling connect()...")
            mClient?.connect(mConnection!!.mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isConnecting = false
                    isConnected = true
                    Log.d("MqttService", "=== CONNECT SUCCESS ===")
                    updateConnectionStatus(this@MqttService, true)
                    
                    // 更新通知显示已连接
                    if (isPersistentNotificationEnabled(this@MqttService)) {
                        updateNotification("MQTT 已连接", "MQTT client is running")
                    }
                    
                    Log.d("MqttService", "MQTT connected successfully in background")
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnecting = false
                    isConnected = false
                    Log.e("MqttService", "Auto-connect failed: ${exception?.message}", exception)
                    updateConnectionStatus(this@MqttService, false)
                    
                    // 更新通知显示未连接
                    if (isPersistentNotificationEnabled(this@MqttService)) {
                        updateNotification("MQTT 未连接", "Connection failed: ${exception?.message}")
                    }
                }
            })
        } catch (e: Exception) {
            isConnecting = false
            Log.e("MqttService", "Failed to create MQTT client: ${e.message}", e)
        }
    }
}
