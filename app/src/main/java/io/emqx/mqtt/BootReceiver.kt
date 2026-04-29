package io.emqx.mqtt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 比亚迪车机开机/上电广播接收器
 *
 * 功能：
 * 1. 原有：检查autoStart配置后启动MainActivity进行MQTT自动连接
 * 2. 新增：检测无障碍服务状态，如果被系统重置则拉起MainActivity引导用户
 * 3. 新增：尝试直接启动无障碍前台服务（保活）
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "========================================")
        Log.d("BootReceiver", "Received broadcast: $action")
        Log.d("BootReceiver", "Package: ${context.packageName}")
        Log.d("BootReceiver", "Time: ${System.currentTimeMillis()}")
        Log.d("BootReceiver", "========================================")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED, 
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {  // ⭐ 新增：支持加密设备开机
                handleBootOrPowerOn(context)
            }
            else -> {
                Log.w("BootReceiver", "Unknown action: $action")
            }
        }
    }

    private fun handleBootOrPowerOn(context: Context) {
        Log.d("BootReceiver", "Handling boot/power on event")

        // ========== 1. 无障碍状态自检 ==========
        checkAndRestoreAccessibility(context)
        
        // ⭐ 新增：延迟5秒后再次检查并启用（等待无障碍服务完全启动）
        Thread {
            Thread.sleep(5000)
            
            val enabledServices = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (e: Exception) {
                null
            }
            
            val isCurrentlyEnabled = enabledServices?.contains(VoiceAccessibilityService.SERVICE_COMPONENT) == true
            
            if (isCurrentlyEnabled) {
                CapturedTextManager.isEnabled = true
                CapturedTextManager.init(context)
                Log.d("BootReceiver", "Delayed check: Accessibility confirmed, CapturedTextManager enabled")
            } else {
                Log.w("BootReceiver", "Delayed check: Accessibility still not ready")
            }
        }.start()

        // ========== 2. MQTT自动连接（原有逻辑）==========
        val configManager = ConfigManager.getInstance(context)
        
        // 检查是否有保存的配置，不再依赖 autoStart 标志
        if (configManager.hasSavedConfig()) {
            Log.d("BootReceiver", "Auto-start enabled, launching MainActivity...")
            
            // ⭐ 修改：启动后台MQTT连接服务
            startMqttBackgroundService(context)
        } else {
            Log.d("BootReceiver", "No saved config found, skipping auto-start")
        }
    }

    /**
     * 启动后台MQTT连接服务（不弹界面）
     */
    private fun startMqttBackgroundService(context: Context) {
        try {
            // 启动MqttService作为前台服务
            val serviceIntent = Intent(context, MqttService::class.java).apply {
                action = MqttService.ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d("BootReceiver", "MqttService started in background")
            
            // ⭐ 修复：不再启动MainActivity，由MqttService直接处理MQTT连接（参考GPSLogger模式）
            // Thread { ... }.start()
            
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to start background service: ${e.message}", e)
            // 降级方案：显示通知让用户手动点击
            showAutoStartNotification(context)
        }
    }

    /**
     * 检测无障碍服务是否被系统重置，并尝试恢复
     */
    private fun checkAndRestoreAccessibility(context: Context) {
        val sp = context.getSharedPreferences("a11y_status", Context.MODE_PRIVATE)
        val wasEnabled = sp.getBoolean("a11y_was_enabled", false)
        val wasReset = sp.getBoolean("a11y_was_reset", false)
    
        val enabledServices = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        } catch (e: Exception) {
            Log.e("BootReceiver", "Cannot read accessibility setting: ${e.message}")
            null
        }
    
        val isCurrentlyEnabled = enabledServices?.contains(VoiceAccessibilityService.SERVICE_COMPONENT) == true
    
        Log.d("BootReceiver", "A11y check: wasEnabled=$wasEnabled, wasReset=$wasReset, currentlyEnabled=$isCurrentlyEnabled")
    
        when {
            // 系统抹除了无障碍权限 → 记录标记，并在通知栏显示引导
            wasEnabled && !isCurrentlyEnabled -> {
                Log.w("BootReceiver", "⚠️ Accessibility was RESET by system! Showing notification.")
                sp.edit().putBoolean("a11y_needs_fix", true).apply()
                    
                // ⭐ 在通知栏显示无障碍重置引导（替代原来的“申请成功”位置）
                showAccessibilityResetNotification(context)
            }
            isCurrentlyEnabled -> {
                Log.i("BootReceiver", "✅ Accessibility service still enabled")
                // ⭐ 新增：如果无障碍已启用，确保CapturedTextManager也被启用
                CapturedTextManager.isEnabled = true
                CapturedTextManager.init(context)
                Log.d("BootReceiver", "Auto-enabled CapturedTextManager")
                // AccessibilityService由系统管理，无需手动启动
            }
            else -> {
                // 无障碍从未启用过，不处理
                Log.d("BootReceiver", "Accessibility never enabled, skipping")
            }
        }
    }

    /**
     * 显示无障碍被重置的通知（在通知栏原位置展示困境）
     */
    private fun showAccessibilityResetNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "accessibility_channel",
                    "无障碍服务提醒",
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "需要开启无障碍服务以支持语音捕获功能"
                notificationManager.createNotificationChannel(channel)
            }
            
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, "accessibility_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("⚠️ 无障碍服务已关闭")
                    .setContentText("点击此处重新开启MQTT Assistant无障碍服务")
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("⚠️ 无障碍服务已关闭")
                    .setContentText("点击此处重新开启MQTT Assistant无障碍服务")
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
            }
            
            // ⭐ 使用固定ID 2001，与MainActivity中的通知ID一致
            notificationManager.notify(2001, notification)
            Log.d("BootReceiver", "Accessibility reset notification shown (ID=2001)")
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to show accessibility notification: ${e.message}", e)
        }
    }

    /**
     * 显示自动启动通知（当无法直接启动 Activity 时）
     */
    private fun showAutoStartNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 创建通知渠道（Android 8.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "auto_start_channel",
                    "自启动提醒",
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "应用开机自启动提醒"
                notificationManager.createNotificationChannel(channel)
            }
            
            // 创建点击通知后启动的 Intent
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("auto_connect", true)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 构建通知（使用原生 Builder）
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, "auto_start_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("MQTT 客户端")
                    .setContentText("点击启动并自动连接 MQTT")
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("MQTT 客户端")
                    .setContentText("点击启动并自动连接 MQTT")
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
            }
            
            notificationManager.notify(1001, notification)
            Log.d("BootReceiver", "Auto-start notification shown")
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to show notification: ${e.message}", e)
        }
    }
}
