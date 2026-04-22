package io.emqx.mqtt

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 开机自启动相关的工具函数
 */
object BootUtils {
    private const val TAG = "BootUtils"

    /**
     * 检查应用是否被允许自启动
     */
    fun isAutoStartAllowed(context: Context): Boolean {
        return try {
            // 检查是否在白名单中或电池优化被忽略
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE)
                val pm = powerManager as android.os.PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true // 低版本Android默认允许
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery optimization: ${e.message}")
            true // 发生错误时假设允许
        }
    }

    /**
     * 请求忽略电池优化（用于自启动）
     */
    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    /**
     * 尝试打开厂商特定的自启动管理页面
     */
    fun openAutoStartSettings(context: Context) {
        try {
            val intent = Intent()
            val manufacturer = Build.MANUFACTURER.lowercase()

            when {
                manufacturer.contains("xiaomi") -> {
                    intent.component = android.content.ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                manufacturer.contains("huawei") -> {
                    intent.component = android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
                manufacturer.contains("oppo") -> {
                    intent.component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                manufacturer.contains("vivo") -> {
                    intent.component = android.content.ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
                manufacturer.contains("samsung") -> {
                    intent.component = android.content.ComponentName(
                        "com.samsung.android.sm_cn",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                }
                manufacturer.contains("oneplus") -> {
                    intent.component = android.content.ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                }
                else -> {
                    // 对于未知制造商，尝试通用方法
                    requestIgnoreBatteryOptimization(context)
                    return
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isIntentAvailable(context, intent)) {
                context.startActivity(intent)
            } else {
                // 如果厂商特定的页面不可用，尝试通用方法
                requestIgnoreBatteryOptimization(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening auto-start settings: ${e.message}")
            // 出错时尝试通用方法
            requestIgnoreBatteryOptimization(context)
        }
    }

    private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        return context.packageManager.queryIntentActivities(intent, 0).size > 0
    }
}