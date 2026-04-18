package io.emqx.mqtt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * 比亚迪DiLink车机权限工具类
 *
 * 提供3个关键跳转入口，用于将App加入车机白名单，防止后台被杀、无障碍失效：
 * 1. 自启动管理（关闭"禁止自启动"）
 * 2. 极速模式白名单（最高级后台保活，最重要！）
 * 3. 电池优化设置（设为"不优化"）
 */
object BydPermitUtils {

    /**
     * 跳转到比亚迪【自启动管理】页面
     * 关闭本应用的"禁止自启动"开关，确保开机广播能正常接收
     */
    fun jumpToAutoStart(context: Context) {
        try {
            // 比亚迪DiLink 自启动管理页面（已知ComponentName）
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.byd.settings",
                    "com.byd.settings.appmanagement.AuthManagmentActivity"
                )
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 兜底：跳转应用详情页
            jumpToAppDetail(context)
            Toast.makeText(context, context.getString(R.string.byd_autostart_fallback), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 跳转到比亚迪【极速模式白名单】页面（⭐ 最重要！）
     * 将App加入白名单后，车机不会在后台斩杀进程
     */
    fun jumpToSpeedWhiteList(context: Context) {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.byd.setting",
                    "com.byd.setting.activities.SpeedModeWhiteListActivity"
                )
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.byd_speed_whitelist_fallback), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 跳转到电池优化设置页面
     * 将本应用设置为"不优化"，避免系统因省电杀死无障碍服务
     */
    fun jumpToBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            } catch (e: Exception) {
                jumpToAppDetail(context)
            }
        } else {
            Toast.makeText(context, context.getString(R.string.byd_battery_not_needed), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 一键跳转所有3项设置（依次打开）
     * 建议在SettingFragment中提供按钮调用此方法
     */
    fun jumpAllSettings(context: Context) {
        // 先提示用户操作流程
        Toast.makeText(context, context.getString(R.string.byd_all_settings_hint), Toast.LENGTH_LONG).show()

        // 依次跳转（每次只打开一个，用户完成后再点下一个）
        // 这里按重要性排序：极速模式 > 电池优化 > 自启动
        jumpToSpeedWhiteList(context)
    }

    /** 备用：跳转应用详情页 */
    fun jumpToAppDetail(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    /**
     * 检测无障碍服务当前是否已开启
     * @param serviceFullName 服务完整组件名，如 VoiceAccessibilityService.SERVICE_COMPONENT
     */
    fun isAccessibilityEnabled(context: Context, serviceFullName: String): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabled != null && enabled.contains(serviceFullName)
        } catch (e: Exception) {
            false
        }
    }
}
