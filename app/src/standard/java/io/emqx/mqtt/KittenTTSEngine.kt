package io.emqx.mqtt

import android.content.Context
import android.util.Log

/**
 * KittenTTS 封装类（标准版 - 空实现）
 * 当 BuildConfig.INCLUDE_KITTENTTS = false 时使用此实现
 */
class KittenTTSEngine {
    
    companion object {
        private const val TAG = "KittenTTS"
        
        val AVAILABLE_VOICES = emptyList<String>()
    }
    
    fun initialize(context: Context): Boolean {
        Log.w(TAG, "KittenTTS not included in this build")
        return false
    }
    
    fun isReady(): Boolean {
        return false
    }
    
    fun speak(text: String, voice: String, speed: Float): Boolean {
        Log.w(TAG, "KittenTTS not available - cannot speak: $text")
        return false
    }
    
    fun stop() {
        // No-op
    }
    
    fun release() {
        // No-op
    }
    
    fun getAvailableVoices(): List<String> {
        return emptyList()
    }
}
