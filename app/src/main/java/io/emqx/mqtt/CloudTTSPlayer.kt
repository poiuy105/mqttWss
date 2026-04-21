package io.emqx.mqtt

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 免费云端TTS播放器（无需APIKey、无需注册）
 * 内置3套接口自动降级：Edge-TTS -> 百度翻译 -> 有道词典
 * 采用"先下载到临时文件，再用MediaPlayer播放"策略，适配车机环境
 *
 * API来源（2026-04-18实测全部可用）：
 *   1. Edge-TTS (tts.mzzsfy.eu.org) - 微软Edge语音，音质最好，支持多种中文音色
 *   2. 百度翻译 (fanyi.baidu.com) - 百度翻译内置TTS，稳定可靠
 *   3. 有道词典 (dict.youdao.com) - 有道词典发音接口，轻量快速（需Referer头）
 */
class CloudTTSPlayer private constructor() {

    companion object {
        private const val TAG = "CloudTTS"

        @Volatile
        private var instance: CloudTTSPlayer? = null

        fun getInstance(): CloudTTSPlayer {
            return instance ?: synchronized(this) {
                instance ?: CloudTTSPlayer().also { instance = it }
            }
        }

        // 接口索引常量
        const val API_EDGETTS = 0    // Edge-TTS（首选，车载最优，音质最佳）
        const val API_BAIDU = 1      // 百度翻译（备用1）
        const val API_YOUDAO = 2     // 有道词典（兜底）

        // Edge-TTS 中文音色列表
        val EDGETTS_VOICES = listOf(
            "zh-CN-XiaoxiaoNeural",   // 晓晓（甜美女声，推荐默认）
            "zh-CN-YunyangNeural",    // 云扬（成熟男声）
            "zh-CN-XiaoyiNeural",     // 晓依（温柔女声）
            "zh-CN-YunjianNeural"     // 云健（磁性男声）
        )
        val EDGETTS_VOICE_NAMES = mapOf(
            "zh-CN-XiaoxiaoNeural" to "晓晓-甜美女声",
            "zh-CN-YunyangNeural" to "云扬-成熟男声",
            "zh-CN-XiaoyiNeural" to "晓依-温柔女声",
            "zh-CN-YunjianNeural" to "云健-磁性男声"
        )
    }

    private var mediaPlayer: MediaPlayer? = null
    /** 播报防抖间隔：5秒内不重复播报（防止车况轮询刷屏） */
    private var lastSpeakTime = 0L
    private val speakIntervalMs = 5000L
    /** 线程池用于异步下载音频 */
    private val downloadExecutor = Executors.newSingleThreadExecutor()
    /** 当前正在执行的下载任务（用于取消） */
    private var currentDownload: Future<*>? = null
    /** 临时文件缓存目录 */
    private var cacheDir: File? = null
    /** Context引用，用于显示Toast */
    private var appContext: Context? = null

    // ========== 可配置参数（从Setting页面设置） ==========
    var currentApiIndex: Int = API_EDGETTS
    var voice: String = "zh-CN-XiaoxiaoNeural"
    var speed: Float = 1.0f       // 语速倍率 (Edge-TTS用rate格式)
    var pitch: String = "+0Hz"    // 音调偏移 (Edge-TTS用pitch格式, 如"+0Hz","-5Hz","+10Hz")
    var volume: Float = 1.0f      // 音量 (0.0~1.0, 仅作记录参考)

    /**
     * 设置缓存目录（必须在首次使用前调用，通常在MainActivity.onCreate中）
     */
    fun setCacheDir(dir: File) {
        cacheDir = dir
        if (!cacheDir!!.exists()) cacheDir!!.mkdirs()
    }

    /**
     * 设置Context（用于显示Toast）
     */
    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    // ========== 固定接口地址（已验证可用 2026-04-18）==========
    private val API_MAIN = "https://tts.mzzsfy.eu.org/api/tts"
    private val API_BACK1 = "https://fanyi.baidu.com/gettts"
    private val API_BACK2 = "https://dict.youdao.com/dictvoice"

    /**
     * 播报文本（主入口，自动防抖+自动降级）
     * @param text 要朗读的文本
     * @param force 是否强制播放（忽略防抖间隔）
     */
    fun speak(text: String, force: Boolean = false) {
        if (text.isBlank()) return

        if (!force) {
            val now = System.currentTimeMillis()
            if (now - lastSpeakTime < speakIntervalMs) {
                Log.d(TAG, "Skipped by debounce ($speakIntervalMs ms)")
                return
            }
            lastSpeakTime = now
        } else {
            lastSpeakTime = System.currentTimeMillis()
        }

        when (currentApiIndex) {
            API_EDGETTS -> playWithFallback(text, ::buildEdgeTtsUrl)
            API_BAIDU -> playWithFallback(text, ::buildBaiduUrl)
            else -> playWithFallback(text, ::buildYoudaoUrl)
        }
    }

    /**
     * 使用指定接口播报（用于测试按钮）
     */
    fun speakWithApi(apiIndex: Int, text: String) {
        if (text.isBlank()) return
        lastSpeakTime = System.currentTimeMillis()
        when (apiIndex) {
            API_EDGETTS -> playWithFallback(text, ::buildEdgeTtsUrl)
            API_BAIDU -> playWithFallback(text, ::buildBaiduUrl)
            API_YOUDAO -> playWithFallback(text, ::buildYoudaoUrl)
            else -> playWithFallback(text, ::buildEdgeTtsUrl)
        }
    }

    // ========== URL构建方法 ==========

    private fun buildEdgeTtsUrl(text: String): TtsRequestInfo {
        val encodeText = URLEncoder.encode(text, "UTF-8")
        val ratePercent = ((speed - 1.0f) * 100).toInt()
        val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
        val url = "$API_MAIN?text=$encodeText&lang=zh-CN&voice=$voice&rate=$rateStr&pitch=$pitch"
        return TtsRequestInfo(url, emptyMap())
    }

    private fun buildBaiduUrl(text: String): TtsRequestInfo {
        val encodeText = URLEncoder.encode(text, "UTF-8")
        val spd = (speed.coerceIn(0.5f, 2.0f) * 4.5f).toInt().coerceIn(1, 9)
        val url = "$API_BACK1?lan=zh&text=$encodeText&spd=$spd&source=web"
        return TtsRequestInfo(url, emptyMap())
    }

    private fun buildYoudaoUrl(text: String): TtsRequestInfo {
        val encodeText = URLEncoder.encode(text, "UTF-8")
        val url = "$API_BACK2?type=2&audio=$encodeText"
        // 有道需要Referer头才能返回正确音频
        val headers = mapOf("Referer" to "https://fanyi.youdao.com/")
        return TtsRequestInfo(url, headers)
    }

    // ========== 核心播放逻辑：先下载再播放 ==========

    /**
     * 使用指定的URL构建器播放，失败时自动降级
     */
    private fun playWithFallback(text: String, urlBuilder: (String) -> TtsRequestInfo, fallbackCount: Int = 0) {
        // 取消之前的下载任务
        currentDownload?.cancel(false)

        val info = urlBuilder(text)
        Log.d(TAG, "Downloading [fallback=$fallbackCount]: ${info.url.take(100)}")

        currentDownload = downloadExecutor.submit {
            try {
                val audioFile = downloadAudio(info)
                if (audioFile == null || !audioFile.exists() || audioFile.length() < 256L) {
                    Log.w(TAG, "Download failed or file too small (${audioFile?.length()}B), fallback")
                    handleFallback(text, fallbackCount)
                    return@submit
                }

                // 在主线程播放本地文件
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        stop()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(audioFile.absolutePath)
                            setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                            setOnPreparedListener { mp ->
                                try {
                                    mp.start()
                                    Log.d(TAG, "Playback started: ${audioFile.name} (${audioFile.length()}B)")
                                } catch (e: Exception) {
                                    Log.e(TAG, "start() exception: ${e.message}")
                                    handleFallbackOnMainThread(text, fallbackCount)
                                }
                            }
                            setOnCompletionListener { mp ->
                                try { mp.reset() } catch (e: Exception) {}
                                // 删除临时文件
                                try { audioFile.delete() } catch (e: Exception) {}
                            }
                            setOnErrorListener { _, what, extra ->
                                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                                handleFallbackOnMainThread(text, fallbackCount)
                                true
                            }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Play from file error: ${e.message}")
                        handleFallbackOnMainThread(text, fallbackCount)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download exception: ${e.message}", e)
                handleFallback(text, fallbackCount)
            }
        }
    }

    /**
     * 下载音频到临时文件
     * @return 下载成功返回File对象，失败返回null
     */
    private fun downloadAudio(info: TtsRequestInfo): File? {
        // 在主线程显示Toast提示
        appContext?.let { ctx ->
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, "正在下载语音...", Toast.LENGTH_SHORT).show()
            }
        }
        
        var conn: HttpURLConnection? = null
        try {
            val url = URL(info.url)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000   // 连接超时15秒
            conn.readTimeout = 20000      // 读取超时20秒
            conn.doInput = true
            // 设置自定义HTTP头（如有道需要的Referer）
            for ((key, value) in info.headers) {
                conn.setRequestProperty(key, value)
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP error: $responseCode ${conn.responseMessage}")
                return null
            }

            val contentType = conn.contentType ?: ""
            Log.d(TAG, "Response: $responseCode type=$contentType size=${conn.contentLength}")

            // 验证是否为音频内容
            if (!contentType.contains("audio", ignoreCase = true) &&
                !contentType.contains("mpeg", ignoreCase = true) &&
                !contentType.contains("mp3", ignoreCase = true)) {
                // 有些API不返回正确的Content-Type，通过大小判断
                if (conn.contentLength > 0 && conn.contentLength < 512) {
                    Log.w(TAG, "Non-audio response ($contentType), too small: ${conn.contentLength}B")
                    return null
                }
            }

            // 创建临时文件保存音频
            val dir = cacheDir ?: File(System.getProperty("java.io.tmpdir"), "cloudtts").also {
                if (!it.exists()) it.mkdirs()
            }
            val outFile = File(dir, "tts_${System.currentTimeMillis()}.mp3")

            val inputStream = conn.inputStream
            val fos = FileOutputStream(outFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fos.write(buffer, 0, bytesRead)
            }
            fos.close()
            inputStream.close()

            Log.d(TAG, "Downloaded: ${outFile.absolutePath} (${outFile.length()}B)")
            return outFile

        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Connection/Read timeout: ${e.message}")
            return null
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "DNS resolution failed: ${e.message}")
            return null
        } catch (e: javax.net.ssl.SSLException) {
            Log.w(TAG, "SSL/TLS error: ${e.message}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            return null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 处理降级（在后台线程调用）
     */
    private fun handleFallback(text: String, fallbackCount: Int) {
        if (fallbackCount == 0) when (currentApiIndex) {
            API_EDGETTS -> {
                Log.w(TAG, "EdgeTTS failed, switching to Baidu")
                playWithFallback(text, ::buildBaiduUrl, 1)
            }
            API_BAIDU -> {
                Log.w(TAG, "Baidu failed, switching to Youdao")
                playWithFallback(text, ::buildYoudaoUrl, 1)
            }
            API_YOUDAO -> {
                Log.w(TAG, "Youdao failed, trying Baidu as last resort")
                playWithFallback(text, ::buildBaiduUrl, 1)
            }
            else -> {
                Log.w(TAG, "EdgeTTS failed, switching to Baidu")
                playWithFallback(text, ::buildBaiduUrl, 1)
            }
        } else if (fallbackCount == 1) {
            Log.w(TAG, "Backup also failed, trying final API")
            when (currentApiIndex) {
                API_EDGETTS, API_YOUDAO -> playWithFallback(text, ::buildYoudaoUrl, 2)
                else -> playWithFallback(text, ::buildEdgeTtsUrl, 2)
            }
        } else {
            Log.e(TAG, "All APIs exhausted for text: $text")
        }
    }

    /**
     * 处理降级（在主线程回调中调用）
     */
    private fun handleFallbackOnMainThread(text: String, fallbackCount: Int) {
        // 延迟一点让当前错误处理完成
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            handleFallback(text, fallbackCount)
        }, 300)
    }

    /**
     * 停止当前播报
     */
    fun stop() {
        currentDownload?.cancel(false)
        currentDownload = null
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.reset()
                mp.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stop exception: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * 释放资源（在onDestroy中调用）
     */
    fun release() {
        stop()
        downloadExecutor.shutdownNow()
        // 清理缓存文件
        try {
            cacheDir?.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {}
    }

    /**
     * 获取当前API的显示名称
     */
    fun getCurrentApiName(): String {
        return when (currentApiIndex) {
            API_EDGETTS -> "微软Edge-TTS (${EDGETTS_VOICE_NAMES[voice] ?: voice})"
            API_BAIDU -> "百度翻译TTS"
            else -> "有道词典TTS"
        }
    }

    /**
     * 重置为默认车载最优配置
     */
    fun resetToDefaults() {
        currentApiIndex = API_EDGETTS
        voice = "zh-CN-XiaoxiaoNeural"
        speed = 1.0f
        pitch = "+0Hz"
        volume = 1.0f
    }

    /**
     * TTS请求数据类：URL和自定义HTTP头
     */
    private data class TtsRequestInfo(val url: String, val headers: Map<String, String>)
}
