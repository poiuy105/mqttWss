package io.emqx.mqtt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * KittenTTS 封装类（完整版实现）
 * 
 * ⚠️ 当前状态：框架代码，未完整实现
 * 
 * 完整集成需要以下步骤：
 * 1. 从 https://github.com/gyanendra-baghel/kittentts-android 获取完整实现
 * 2. 在 build.gradle 添加 ONNX Runtime 依赖：
 *    fullImplementation 'com.microsoft.onnxruntime:onnxruntime-android:1.16.3'
 * 3. 下载模型文件到 app/src/full/assets/model/：
 *    - kitten_tts.onnx (25-80MB)
 *    - voices.npz
 *    - config.json
 * 4. 准备 espeak-ng 数据到 app/src/full/assets/espeak-ng-data/
 * 5. 编译 C++ 原生库（需要 NDK 27.0.12077973 + CMake 3.22.1）
 * 6. 实现实际的 ONNX 推理逻辑
 * 
 * 参考文档：KITTENTTS_INTEGRATION.md
 */
class KittenTTSEngine {
    
    companion object {
        private const val TAG = "KittenTTS"
        
        // 可用音色列表（与 Python 版本一致）
        val AVAILABLE_VOICES = listOf(
            "Bella", "Jasper", "Luna", "Bruno",
            "Rosie", "Hugo", "Kiki", "Leo"
        )
        
        // 加载原生库
        init {
            try {
                System.loadLibrary("kittentts-native")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    @Volatile
    private var isInitialized = false
    private var audioTrack: AudioTrack? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Native methods
    private external fun nativeInitialize(modelPath: String): Boolean
    private external fun nativeSynthesize(text: String, voice: String, speed: Float): FloatArray
    private external fun nativeRelease()
    
    /**
     * 初始化 KittenTTS 引擎
     */
    fun initialize(context: Context): Boolean {
        return try {
            Log.d(TAG, "Initializing KittenTTS engine...")
            
            // 检查模型文件是否存在
            val modelFile = File(context.filesDir, "model/kitten_tts.onnx")
            if (!modelFile.exists()) {
                // 从 assets 复制模型文件
                copyAssetsToFiles(context)
            }
            
            // 调用 native 初始化
            val modelPath = File(context.filesDir, "model").absolutePath
            isInitialized = nativeInitialize(modelPath)
            
            if (isInitialized) {
                Log.d(TAG, "✅ KittenTTS initialized successfully")
            } else {
                Log.e(TAG, "❌ Native initialization failed")
            }
            
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize KittenTTS", e)
            isInitialized = false
            false
        }
    }
    
    /**
     * 从 assets 复制文件到 filesDir
     */
    private fun copyAssetsToFiles(context: Context) {
        try {
            val assetManager = context.assets
            val modelDir = File(context.filesDir, "model")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            
            // 复制模型文件
            val files = listOf("kitten_tts.onnx", "voices.npz", "config.json")
            for (fileName in files) {
                val inputStream = assetManager.open("model/$fileName")
                val outputFile = File(modelDir, fileName)
                inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied $fileName to ${outputFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy assets", e)
        }
    }
    
    /**
     * 检查引擎是否就绪
     */
    fun isReady(): Boolean {
        return isInitialized
    }
    
    /**
     * 合成语音并播放
     * @param text 要合成的文本
     * @param voice 音色名称
     * @param speed 语速（0.5-2.0）
     */
    fun speak(text: String, voice: String = "Jasper", speed: Float = 1.0f): Boolean {
        if (!isReady()) {
            Log.e(TAG, "Engine not ready")
            return false
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Empty text")
            return false
        }
        
        return try {
            Log.d(TAG, "🔊 Speaking with KittenTTS: voice=$voice, speed=$speed")
            Log.d(TAG, "Text: $text")
            
            // 调用 native 合成
            val audioData = nativeSynthesize(text, voice, speed)
            
            if (audioData.isNotEmpty()) {
                // 播放音频
                playAudio(audioData)
                true
            } else {
                Log.e(TAG, "Synthesis returned empty audio")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to speak", e)
            false
        }
    }
    
    /**
     * 播放音频数据
     */
    private fun playAudio(audioData: FloatArray) {
        try {
            val sampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
            
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            audioTrack = AudioTrack.Builder()
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack?.play()
            audioTrack?.write(audioData, 0, audioData.size, AudioTrack.WRITE_BLOCKING)
            
            Log.d(TAG, "Audio playback started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
        }
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "Stopped playback")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
        nativeRelease()
        isInitialized = false
        Log.d(TAG, "Released KittenTTS engine")
    }
    
    /**
     * 获取可用音色列表
     */
    fun getAvailableVoices(): List<String> {
        return AVAILABLE_VOICES
    }
}
