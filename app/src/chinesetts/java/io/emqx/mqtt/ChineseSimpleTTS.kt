package io.emqx.mqtt

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

/**
 * ChineseSimpleTTS - 基于 TensorFlow Lite 的离线中文语音合成引擎
 * 使用 FastSpeech2 + MB-MelGAN 模型
 * 完全离线运行，无需网络连接
 */
class ChineseSimpleTTS(private val context: Context) {

    companion object {
        private const val TAG = "ChineseSimpleTTS"
        
        // 模型文件名
        private const val MODEL_FASTSPEECH2 = "fastspeech2_quan.tflite"
        private const val MODEL_MB_MELGAN = "mb_melgan.tflite"
        private const val MAPPER_FILE = "baker_mapper.json"
        
        // 音频参数
        private const val SAMPLE_RATE = 24000
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_OUT_MONO
    }

    private var fastSpeech2Interpreter: Interpreter? = null
    private var mbMelGanInterpreter: Interpreter? = null
    private var isInitialized = false
    
    // 音频播放器
    private var audioTrack: android.media.AudioTrack? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // 异步执行器
    private val ttsExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ChineseSimpleTTS-Thread").apply { 
            priority = Thread.MIN_PRIORITY 
        }
    }
    
    // 回调接口
    interface TTSListener {
        fun onSpeakStart()
        fun onSpeakDone()
        fun onSpeakError(error: String)
    }
    
    private var listener: TTSListener? = null

    /**
     * 初始化 TTS 引擎（加载模型）
     */
    fun initialize(listener: TTSListener? = null): Boolean {
        this.listener = listener
        
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return true
        }

        return try {
            Log.d(TAG, "Loading models from assets...")
            
            // 从 assets 加载模型到缓存目录
            val cacheDir = File(context.cacheDir, "chinese_simple_tts")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val fastSpeech2File = loadModelFromAssets(MODEL_FASTSPEECH2, cacheDir)
            val mbMelGanFile = loadModelFromAssets(MODEL_MB_MELGAN, cacheDir)
            
            // 加载 FastSpeech2 模型
            Log.d(TAG, "Loading FastSpeech2 model...")
            fastSpeech2Interpreter = Interpreter(loadModelFile(fastSpeech2File))
            Log.d(TAG, "FastSpeech2 model loaded successfully")
            
            // 加载 MB-MelGAN 声码器模型
            Log.d(TAG, "Loading MB-MelGAN model...")
            mbMelGanInterpreter = Interpreter(loadModelFile(mbMelGanFile))
            Log.d(TAG, "MB-MelGAN model loaded successfully")
            
            // 初始化音频管理器
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            isInitialized = true
            Log.d(TAG, "✅ ChineseSimpleTTS initialized successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize ChineseSimpleTTS", e)
            listener?.onSpeakError("初始化失败: ${e.message}")
            false
        }
    }

    /**
     * 从 assets 加载模型文件到缓存目录
     */
    private fun loadModelFromAssets(fileName: String, cacheDir: File): File {
        val outputFile = File(cacheDir, fileName)
        if (outputFile.exists()) {
            Log.d(TAG, "Model already cached: $fileName")
            return outputFile
        }
        
        Log.d(TAG, "Extracting model from assets: $fileName")
        context.assets.open(fileName).use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
        Log.d(TAG, "Model extracted: ${outputFile.absolutePath}")
        return outputFile
    }

    /**
     * 加载 TFLite 模型文件
     */
    private fun loadModelFile(file: File): MappedByteBuffer {
        val fileChannel = FileChannel.open(file.toPath(), 
            java.nio.file.StandardOpenOption.READ)
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
    }

    /**
     * 合成并播放文本（异步）
     */
    fun speak(text: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, initializing now...")
            if (!initialize()) {
                listener?.onSpeakError("TTS 未初始化")
                return
            }
        }

        if (text.isBlank()) {
            Log.w(TAG, "Empty text")
            return
        }

        ttsExecutor.execute {
            try {
                listener?.onSpeakStart()
                
                // 请求音频焦点
                requestAudioFocus()
                
                // 文本预处理：转换为拼音序列
                Log.d(TAG, "Processing text: $text")
                val phonemeIds = textToPhonemeIds(text)
                
                if (phonemeIds.isEmpty()) {
                    listener?.onSpeakError("文本转换失败")
                    return@execute
                }
                
                // Step 1: FastSpeech2 推理 - 生成梅尔频谱
                Log.d(TAG, "Running FastSpeech2 inference...")
                val melSpectrogram = runFastSpeech2(phonemeIds)
                
                // Step 2: MB-MelGAN 推理 - 生成音频波形
                Log.d(TAG, "Running MB-MelGAN vocoder...")
                val audioWaveform = runMBMelGan(melSpectrogram)
                
                // Step 3: 播放音频
                Log.d(TAG, "Playing audio...")
                playAudio(audioWaveform)
                
                listener?.onSpeakDone()
                Log.d(TAG, "✅ TTS completed")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ TTS error", e)
                listener?.onSpeakError("合成失败: ${e.message}")
            } finally {
                abandonAudioFocus()
            }
        }
    }

    /**
     * 文本转拼音 ID 序列（简化版）
     * 实际项目中需要完整的拼音映射表
     */
    private fun textToPhonemeIds(text: String): IntArray {
        // TODO: 实现完整的汉字转拼音逻辑
        // 这里使用简化的占位实现
        // 实际需要：
        // 1. 加载 baker_mapper.json
        // 2. 汉字转拼音（使用 pinyin4j 或内置映射）
        // 3. 拼音转 ID
        
        Log.w(TAG, "Using placeholder phoneme conversion")
        
        // 返回一个简单的测试序列（实际应该根据文本生成）
        // 这里仅为演示流程，实际需要完整实现
        return intArrayOf(1, 2, 3, 4, 5)
    }

    /**
     * FastSpeech2 推理
     */
    private fun runFastSpeech2(phonemeIds: IntArray): Array<FloatArray> {
        val interpreter = fastSpeech2Interpreter ?: throw IllegalStateException("FastSpeech2 not loaded")
        
        // 准备输入
        val inputShape = interpreter.getInputTensor(0).shape()
        val batchSize = 1
        val sequenceLength = phonemeIds.size
        
        val inputBuffer = Array(batchSize) { 
            FloatArray(inputShape[1]) { index ->
                if (index < sequenceLength) phonemeIds[index].toFloat() else 0f
            }
        }
        
        // 准备输出缓冲区
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputBuffer = Array(outputShape[0]) { FloatArray(outputShape[1]) }
        
        // 运行推理
        interpreter.run(inputBuffer, outputBuffer)
        
        return outputBuffer
    }

    /**
     * MB-MelGAN 声码器推理
     */
    private fun runMBMelGan(melSpectrogram: Array<FloatArray>): FloatArray {
        val interpreter = mbMelGanInterpreter ?: throw IllegalStateException("MB-MelGAN not loaded")
        
        // 准备输入
        val inputShape = interpreter.getInputTensor(0).shape()
        val melFrames = melSpectrogram.size
        val melDim = melSpectrogram[0].size
        
        val inputBuffer = Array(1) { 
            Array(melFrames) { frame ->
                FloatArray(melDim) { dim ->
                    melSpectrogram[frame][dim]
                }
            }
        }
        
        // 准备输出缓冲区
        val outputShape = interpreter.getOutputTensor(0).shape()
        val audioLength = outputShape[1]
        val outputBuffer = Array(1) { FloatArray(audioLength) }
        
        // 运行推理
        interpreter.run(inputBuffer, outputBuffer)
        
        return outputBuffer[0]
    }

    /**
     * 播放音频波形
     */
    private fun playAudio(audioData: FloatArray) {
        // 转换为 PCM16
        val pcmData = ShortArray(audioData.size)
        for (i in audioData.indices) {
            pcmData[i] = (audioData[i] * Short.MAX_VALUE).toInt().toShort()
        }
        
        // 创建 AudioTrack
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        this.audioTrack = audioTrack
        
        try {
            audioTrack.play()
            audioTrack.write(pcmData, 0, pcmData.size)
            
            // 等待播放完成
            while (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                Thread.sleep(10)
            }
        } finally {
            audioTrack.stop()
            audioTrack.release()
        }
    }

    /**
     * 请求音频焦点
     */
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            
            val result = audioManager?.requestAudioFocus(audioFocusRequest!!)
            Log.d(TAG, "Audio focus request result: $result")
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            Log.d(TAG, "Audio focus request result (legacy): $result")
        }
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    /**
     * 停止当前播报
     */
    fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "TTS stopped")
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        stop()
        fastSpeech2Interpreter?.close()
        mbMelGanInterpreter?.close()
        fastSpeech2Interpreter = null
        mbMelGanInterpreter = null
        isInitialized = false
        Log.d(TAG, "ChineseSimpleTTS shut down")
    }

    /**
     * 检查是否就绪
     */
    fun isReady(): Boolean = isInitialized
}
