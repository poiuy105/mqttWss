package io.emqx.mqtt

import android.media.MediaPlayer
import android.util.Log
import java.io.IOException
import java.net.URLEncoder

/**
 * 免费云端TTS播放器（无需APIKey、无需注册）
 * 内置3套接口自动降级：讯飞xiaoai.plus -> 阿里duckarmy -> oioweb兜底
 * 使用MediaPlayer播放音频流，适配车机环境
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
        const val API_XIAOAI = 0   // 讯飞 xiaoai.plus（首选，车载最优）
        const val API_DUCKARMY = 1  // 阿里 duckarmy（备用1）
        const val API_OIOWEB = 2    // oioweb（兜底）

        // 音色名称映射（用于UI展示）
        val VOICE_NAMES_XIAOAI = mapOf(
            "xiaoyan" to "讯飞温柔女声",
            "xiaofeng" to "成熟男声",
            "xiaoyu" to "甜美少女音",
            "xiaoyun" to "知性御姐音"
        )

        // 讯飞可用音色列表
        val XIAOAI_VOICES = listOf("xiaoyan", "xiaofeng", "xiaoyu", "xiaoyun")

        // oioweb音色列表
        val OIOWEB_TYPES = listOf("1", "2", "3", "4")
        val OIOWEB_TYPE_NAMES = mapOf(
            "1" to "默认女声",
            "2" to "男声",
            "3" to "甜美女声",
            "4" to "机械合成音"
        )
    }

    private var mediaPlayer: MediaPlayer? = null
    /** 播报防抖间隔：5秒内不重复播报（防止车况轮询刷屏） */
    private var lastSpeakTime = 0L
    private val speakIntervalMs = 5000L

    // ========== 可配置参数（从Setting页面设置） ==========
    var currentApiIndex: Int = API_XIAOAI
    var voice: String = "xiaoyan"
    var speed: Float = 0.92f
    var pitch: Float = 1.0f
    var volume: Float = 0.9f
    // oioweb专用
    var oiowebType: String = "1"
    var oiowebSpeed: Int = 4
    // duckarmy专用
    var duckarmySpd: Int = -1
    var duckarmyPit: Int = 0
    var duckarmyVol: Int = 2

    // ========== 固定接口地址 ==========
    private val API_MAIN = "https://api.xiaoai.plus/tts"
    private val API_BACK1 = "https://tts.duckarmy.com/tts"
    private val API_BACK2 = "https://api.oioweb.cn/api/tts"

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
            API_XIAOAI -> speakXiaoAi(text, 0)
            API_DUCKARMY -> speakDuckArmy(text, 0)
            else -> speakOioweb(text, 0)
        }
    }

    /**
     * 使用指定接口播报（用于测试按钮）
     */
    fun speakWithApi(apiIndex: Int, text: String) {
        if (text.isBlank()) return
        lastSpeakTime = System.currentTimeMillis()
        when (apiIndex) {
            API_XIAOAI -> speakXiaoAi(text, 0)
            API_DUCKARMY -> speakDuckArmy(text, 0)
            API_OIOWEB -> speakOioweb(text, 0)
            else -> speakXiaoAi(text, 0)
        }
    }

    // ========== 私有方法：各接口实现 ==========

    private fun speakXiaoAi(text: String, fallbackCount: Int) {
        val url = buildUrl(
            API_MAIN,
            "voice=$voice&speed=$speed&pitch=$pitch&volume=$volume",
            text
        )
        playUrl(url) { failed ->
            if (failed && fallbackCount < 2) {
                Log.w(TAG, "xiaoai.plus failed, switching to duckarmy ($fallbackCount)")
                speakDuckArmy(text, fallbackCount + 1)
            } else if (failed) {
                Log.e(TAG, "All APIs failed for text: $text")
            }
        }
    }

    private fun speakDuckArmy(text: String, fallbackCount: Int) {
        val url = buildUrl(
            API_BACK1,
            "spd=$duckarmySpd&pit=$duckarmyPit&vol=$duckarmyVol",
            text
        )
        playUrl(url) { failed ->
            if (failed && fallbackCount < 2) {
                Log.w(TAG, "duckarmy failed, switching to oioweb ($fallbackCount)")
                speakOioweb(text, fallbackCount + 1)
            } else if (failed) {
                Log.e(TAG, "All APIs failed for text: $text")
            }
        }
    }

    private fun speakOioweb(text: String, fallbackCount: Int) {
        val url = buildUrl(
            API_BACK2,
            "type=$oiowebType&speed=$oiowebSpeed",
            text
        )
        playUrl(url) { failed ->
            if (failed) {
                Log.e(TAG, "oioweb also failed ($fallbackCount), giving up")
            }
        }
    }

    /**
     * 拼接完整请求URL + 中文编码
     */
    private fun buildUrl(api: String, param: String, text: String): String {
        return try {
            val encodeText = URLEncoder.encode(text, "UTF-8")
            "$api?$param&text=$encodeText"
        } catch (e: Exception) {
            Log.e(TAG, "URL encode failed: ${e.message}")
            "$api?$param&text=$text"
        }
    }

    /**
     * MediaPlayer播放音频流
     * @param onFailed 播放失败回调（用于自动切换备用接口）
     */
    private fun playUrl(url: String, onFailed: ((Boolean) -> Unit)? = null) {
        try {
            stop()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                        Log.d(TAG, "Playback started")
                    } catch (e: Exception) {
                        Log.e(TAG, "start() exception: ${e.message}")
                    }
                }
                setOnCompletionListener { mp ->
                    try { mp.reset() } catch (e: Exception) {}
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
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
            API_XIAOAI -> "讯飞 (${VOICE_NAMES_XIAOAI[voice] ?: voice})"
            API_DUCKARMY -> "阿里DuckArmy"
            else -> "OIOWEB (${OIOWEB_TYPE_NAMES[oiowebType] ?: "女声"})"
        }
    }

    /**
     * 重置为默认车载最优配置
     */
    fun resetToDefaults() {
        currentApiIndex = API_XIAOAI
        voice = "xiaoyan"
        speed = 0.92f
        pitch = 1.0f
        volume = 0.9f
        oiowebType = "1"
        oiowebSpeed = 4
        duckarmySpd = -1
        duckarmyPit = 0
        duckarmyVol = 2
    }
}
