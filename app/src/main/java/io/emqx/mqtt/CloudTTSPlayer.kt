package io.emqx.mqtt

import android.media.MediaPlayer
import android.util.Log
import java.io.IOException
import java.net.URLEncoder

/**
 * 免费云端TTS播放器（无需APIKey、无需注册）
 * 内置3套接口自动降级：Edge-TTS -> 百度翻译 -> 有道词典
 * 使用MediaPlayer播放音频流，适配车机环境
 *
 * API来源（2026-04-18实测全部可用）：
 *   1. Edge-TTS (tts.mzzsfy.eu.org) - 微软Edge语音，音质最好，支持多种中文音色
 *   2. 百度翻译 (fanyi.baidu.com) - 百度翻译内置TTS，稳定可靠
 *   3. 有道词典 (dict.youdao.com) - 有道词典发音接口，轻量快速
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

    // ========== 可配置参数（从Setting页面设置） ==========
    var currentApiIndex: Int = API_EDGETTS
    var voice: String = "zh-CN-XiaoxiaoNeural"
    var speed: Float = 1.0f       // 语速倍率 (Edge-TTS用rate格式)
    var pitch: String = "+0Hz"    // 音调偏移 (Edge-TTS用pitch格式, 如"+0Hz","-5Hz","+10Hz")
    var volume: Float = 1.0f      // 音量 (0.0~1.0, 仅作记录参考)

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
            if (now - lastSpeakTime < speakIntervalMs) return
            lastSpeakTime = now
        }

        when (currentApiIndex) {
            API_EDGETTS -> speakEdgeTts(text, 0)
            API_BAIDU -> speakBaidu(text, 0)
            else -> speakYoudao(text, 0)
        }
    }

    /**
     * 使用指定接口播报（用于测试按钮）
     */
    fun speakWithApi(apiIndex: Int, text: String) {
        if (text.isBlank()) return
        lastSpeakTime = System.currentTimeMillis()
        when (apiIndex) {
            API_EDGETTS -> speakEdgeTts(text, 0)
            API_BAIDU -> speakBaidu(text, 0)
            API_YOUDAO -> speakYoudao(text, 0)
            else -> speakEdgeTts(text, 0)
        }
    }

    // ========== 私有方法：各接口实现 ==========

    /**
     * Edge-TTS (微软语音合成)
     * 参数: text, lang, voice(可选), rate(可选,如"+0%"), pitch(可选,如"+0Hz")
     * 返回: audio/mp3 流
     */
    private fun speakEdgeTts(text: String, fallbackCount: Int) {
        val encodeText = URLEncoder.encode(text, "UTF-8")
        // rate: 百分比格式，speed=1.0 => "+0%", speed=1.5 => "+50%"
        val ratePercent = ((speed - 1.0f) * 100).toInt()
        val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
        val url = buildString {
            append("$API_MAIN?")
            append("text=$encodeText")
            append("&lang=zh-CN")
            append("&voice=$voice")
            append("&rate=$rateStr")
            append("&pitch=$pitch")
        }
        playUrl(url) { failed ->
            if (failed && fallbackCount < 2) {
                Log.w(TAG, "EdgeTTS failed, switching to Baidu ($fallbackCount)")
                speakBaidu(text, fallbackCount + 1)
            } else if (failed) {
                Log.e(TAG, "All APIs failed for text: $text")
            }
        }
    }

    /**
     * 百度翻译TTS
     * 参数: lan=zh, text, spd(1-9), source=web
     * 返回: audio/mpeg 流
     */
    private fun speakBaidu(text: String, fallbackCount: Int) {
        val encodeText = URLEncoder.encode(text, "UTF-8")
        // speed映射: Float(0.5~2.0) -> Int(1~9)
        val spd = (speed.coerceIn(0.5f, 2.0f) * 4.5f).toInt().coerceIn(1, 9)
        val url = "$API_BACK1?lan=zh&text=$encodeText&spd=$spd&source=web"
        playUrl(url) { failed ->
            if (failed && fallbackCount < 2) {
                Log.w(TAG, "Baidu TTS failed, switching to Youdao ($fallbackCount)")
                speakYoudao(text, fallbackCount + 1)
            } else if (failed) {
                Log.e(TAG, "All APIs failed for text: $text")
            }
        }
    }

    /**
     * 有道词典TTS
     * 参数: type=2(中文), audio
     * 返回: audio/mpeg 流
     * 注意：有道只支持固定语速音调，无额外参数
     */
    private fun speakYoudao(text: String, fallbackCount: Int) {
        val encodeText = URLEncoder.encode(text, "UTF-8")
        val url = "$API_BACK2?type=2&audio=$encodeText"
        playUrl(url) { failed ->
            if (failed) {
                Log.e(TAG, "Youdao also failed ($fallbackCount), giving up")
            }
        }
    }

    /**
     * MediaPlayer播放音频流
     * @param onFailed 播放失败回调（用于自动切换备用接口）
     */
    private fun playUrl(url: String, onFailed: ((Boolean) -> Unit)? = null) {
        try {
            stop()

            Log.d(TAG, "Playing URL: ${url.take(120)}...")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                        Log.d(TAG, "Playback started successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "start() exception: ${e.message}")
                        onFailed?.invoke(true)
                    }
                }
                setOnCompletionListener { mp ->
                    try { mp.reset() } catch (e: Exception) {}
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra url=${url.take(80)}")
                    onFailed?.invoke(true)
                    true
                }
                prepareAsync()
            }
        } catch (e: IOException) {
            Log.e(TAG, "playUrl IOException: ${e.message}", e)
            onFailed?.invoke(true)
        } catch (e: Exception) {
            Log.e(TAG, "playUrl exception: ${e.message}", e)
            onFailed?.invoke(true)
        }
    }

    /**
     * 停止当前播报
     */
    fun stop() {
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
}
