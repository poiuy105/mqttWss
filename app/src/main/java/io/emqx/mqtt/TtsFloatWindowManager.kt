package io.emqx.mqtt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * ⭐ TTS和浮动窗口独立管理器
 * 
 * 特点：
 * 1. 完全独立于UI生命周期
 * 2. 由MqttService（前台服务）管理
 * 3. App在后台时也能正常工作
 */
class TtsFloatWindowManager(private val context: Context) {
    
    private var ttsPlayer: CloudTTSPlayer? = null
    private var floatWindowManager: FloatWindowManager? = null
    
    // 开关状态（可以从SharedPreferences读取）
    private var isTTSEnabled: Boolean = true
    private var isFloatWindowEnabled: Boolean = true
    
    init {
        initialize()
    }
    
    /**
     * 初始化TTS和浮动窗口
     */
    private fun initialize() {
        Log.d("TtsFloatWindowManager", "Initializing...")
        
        try {
            // 初始化CloudTTSPlayer
            ttsPlayer = CloudTTSPlayer.getInstance().apply {
                setContext(context.applicationContext)
                // 可以设置其他配置
            }
            Log.d("TtsFloatWindowManager", "CloudTTSPlayer initialized")
        } catch (e: Exception) {
            Log.e("TtsFloatWindowManager", "Failed to init TTS: ${e.message}", e)
        }
        
        try {
            // 初始化FloatWindowManager
            floatWindowManager = FloatWindowManager(context.applicationContext)
            Log.d("TtsFloatWindowManager", "FloatWindowManager initialized")
        } catch (e: Exception) {
            Log.e("TtsFloatWindowManager", "Failed to init float window: ${e.message}", e)
        }
    }
    
    /**
     * 触发TTS播报和浮动窗口
     * 
     * @param text 要播报的文本
     * @param topic MQTT主题（用于浮动窗口）
     * @param force 是否强制播报（忽略队列）
     */
    fun trigger(text: String, topic: String = "", force: Boolean = true) {
        Log.d("TtsFloatWindowManager", "Triggering TTS and float window")
        Log.d("TtsFloatWindowManager", "Text length: ${text.length}, Topic: $topic")
        
        // 触发TTS
        if (isTTSEnabled) {
            try {
                ttsPlayer?.speak(text, force = force)
                Log.d("TtsFloatWindowManager", "TTS triggered")
            } catch (e: Exception) {
                Log.e("TtsFloatWindowManager", "TTS failed: ${e.message}", e)
            }
        } else {
            Log.d("TtsFloatWindowManager", "TTS is disabled")
        }
        
        // 触发浮动窗口
        if (isFloatWindowEnabled && topic.isNotEmpty()) {
            try {
                // ⭐ 修复：浮动窗口必须在主线程中创建
                Handler(Looper.getMainLooper()).post {
                    floatWindowManager?.showMessage(topic, text)
                    Log.d("TtsFloatWindowManager", "Float window triggered on main thread")
                }
            } catch (e: Exception) {
                Log.e("TtsFloatWindowManager", "Float window failed: ${e.message}", e)
            }
        } else {
            Log.d("TtsFloatWindowManager", "Float window is disabled or topic is empty")
        }
    }
    
    /**
     * 更新TTS开关状态
     */
    fun setTTSEnabled(enabled: Boolean) {
        isTTSEnabled = enabled
        Log.d("TtsFloatWindowManager", "TTS enabled: $enabled")
    }
    
    /**
     * 更新浮动窗口开关状态
     */
    fun setFloatWindowEnabled(enabled: Boolean) {
        isFloatWindowEnabled = enabled
        Log.d("TtsFloatWindowManager", "Float window enabled: $enabled")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d("TtsFloatWindowManager", "Releasing resources...")
        
        try {
            ttsPlayer?.release()
            ttsPlayer = null
        } catch (e: Exception) {
            Log.e("TtsFloatWindowManager", "Failed to release TTS: ${e.message}", e)
        }
        
        try {
            floatWindowManager?.release()
            floatWindowManager = null
        } catch (e: Exception) {
            Log.e("TtsFloatWindowManager", "Failed to release float window: ${e.message}", e)
        }
    }
}
