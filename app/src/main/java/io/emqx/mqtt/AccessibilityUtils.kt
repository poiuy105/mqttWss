package io.emqx.mqtt

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * ⭐ 无障碍服务工具类（统一检测和管理）
 * 
 * 功能：
 * 1. 检测无障碍服务是否已启用
 * 2. 跳转到无障碍设置页面
 * 3. 获取服务组件名
 * 
 * 参考：GKD的AccessibilityUtils设计
 */
object AccessibilityUtils {
    private const val TAG = "AccessibilityUtils"
    
    /** 无障碍服务完整组件名 */
    const val SERVICE_COMPONENT = "io.emqx.mqtt/.VoiceAccessibilityService"
    
    /**
     * 检测无障碍服务是否已启用
     * 
     * @param context 上下文
     * @return true=已启用，false=未启用
     */
    fun isServiceEnabled(context: Context): Boolean {
        // 1. 检查全局无障碍开关
        val enabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ACCESSIBILITY_ENABLED: ${e.message}")
            0
        }
        
        if (enabled == 0) {
            Log.d(TAG, "Accessibility globally disabled")
            return false
        }
        
        // 2. 检查已启用的服务列表
        val services = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ENABLED_ACCESSIBILITY_SERVICES: ${e.message}")
            null
        }
        
        if (services.isNullOrEmpty()) {
            Log.d(TAG, "No accessibility services enabled")
            return false
        }
        
        // 3. 精确匹配本服务的组件名
        val isEnabled = services.contains(SERVICE_COMPONENT)
        Log.d(TAG, "Service enabled: $isEnabled (component=$SERVICE_COMPONENT)")
        
        return isEnabled
    }
    
    /**
     * 跳转到无障碍设置页面
     * 
     * @param context 上下文
     */
    fun jumpToSetting(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Jumped to accessibility settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to jump to settings: ${e.message}", e)
        }
    }
    
    /**
     * 检查并自动启用CapturedTextManager
     * 
     * @param context 上下文
     * @return true=已启用，false=未启用
     */
    fun checkAndEnableCapturedText(context: Context): Boolean {
        if (isServiceEnabled(context)) {
            CapturedTextManager.isEnabled = true
            CapturedTextManager.init(context)
            Log.d(TAG, "Auto-enabled CapturedTextManager")
            return true
        } else {
            Log.w(TAG, "Cannot enable CapturedTextManager: service not enabled")
            return false
        }
    }
    
    /**
     * 获取无障碍服务状态描述
     * 
     * @param context 上下文
     * @return 状态描述字符串
     */
    fun getServiceStatusDescription(context: Context): String {
        return if (isServiceEnabled(context)) {
            "✅ 无障碍服务已启用"
        } else {
            "❌ 无障碍服务未启用，请点击\"去开启\"按钮"
        }
    }
}
