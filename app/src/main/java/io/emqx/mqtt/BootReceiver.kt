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
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_POWER_CONNECTED -> {
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

        // ========== 2. MQTT自动连接（原有逻辑）==========
        val configManager = ConfigManager.getInstance(context)
        
        // 检查是否有保存的配置，不再依赖 autoStart 标志
        if (configManager.hasSavedConfig()) {
            Log.d("BootReceiver", "Auto-start enabled, launching MainActivity...")
            
            // 尝试直接启动 Activity
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    // 重要：添加 FLAG_ACTIVITY_NEW_TASK 以从后台启动
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // 传递 auto_connect 参数，触发自动重连
                    putExtra("auto_connect", true)
                }
                context.startActivity(launchIntent)
                Log.d("BootReceiver", "MainActivity launched successfully with auto_connect=true")
            } catch (e: Exception) {
                // Android 10+ 可能阻止后台启动 Activity，显示通知作为备选方案
                Log.w("BootReceiver", "Failed to start Activity directly: ${e.message}, showing notification instead")
                showAutoStartNotification(context)
            }
        } else {
            Log.d("BootReceiver", "No saved config found, skipping auto-start")
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
            // 系统抹除了无障碍权限 → 记录标记，等MainActivity弹出引导
            wasEnabled && !isCurrentlyEnabled -> {
                Log.w("BootReceiver", "⚠️ Accessibility was RESET by system! Marking for user guidance.")
                sp.edit().putBoolean("a11y_needs_fix", true).apply()
            }
            isCurrentlyEnabled -> {
                Log.i("BootReceiver", "✅ Accessibility service still enabled, trying to start foreground service...")
                // 尝试直接拉起前台服务（保活）
                tryStartForegroundService(context)
            }
        }
    }

    /** 尝试启动无障碍前台服务（API26+必须用startForegroundService） */
    private fun tryStartForegroundService(context: Context) {
        try {
            val serviceIntent = Intent(context, VoiceAccessibilityService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d("BootReceiver", "Foreground service start requested")
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to start foreground service: ${e.message}")
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
