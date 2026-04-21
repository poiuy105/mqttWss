package io.emqx.mqtt

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
        Log.d("BootReceiver", "Received broadcast: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_POWER_CONNECTED -> {
                handleBootOrPowerOn(context)
            }
        }
    }

    private fun handleBootOrPowerOn(context: Context) {
        // ========== 1. 无障碍状态自检 ==========
        checkAndRestoreAccessibility(context)

        // ========== 2. MQTT自动连接（原有逻辑）==========
        val configManager = ConfigManager.getInstance(context)
        if (configManager.autoStart && configManager.hasSavedConfig()) {
            Log.d("BootReceiver", "Auto-start enabled, launching MainActivity...")
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                // 重要：添加 FLAG_ACTIVITY_NEW_TASK 以从后台启动
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 传递 auto_connect 参数，触发自动重连
                putExtra("auto_connect", true)
            }
            context.startActivity(launchIntent)
            Log.d("BootReceiver", "MainActivity launched with auto_connect=true")
        } else {
            Log.d("BootReceiver", "Auto-start disabled or no saved config")
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
}
