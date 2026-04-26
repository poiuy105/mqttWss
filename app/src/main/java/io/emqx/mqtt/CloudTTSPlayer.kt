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
        const val API_LOCAL_IFLYTEK = 0  // 本地讯飞TTS（离线可用，推荐首选）⭐
        const val API_EDGETTS = 1        // Edge-TTS（音质最佳）
        const val API_BAIDU = 2          // 百度翻译（备用1）
        const val API_YOUDAO = 3         // 有道词典（兜底）

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
    /** TTSManager实例（用于本地TTS） */
    private var ttsManager: TTSManager? = null
    /** 日志回调（用于将日志输出到Home页面Debug Log） */
    private var logCallback: ((String) -> Unit)? = null
    
    // ========== TTS引擎管理 ==========
    /** 存储所有可用的TTS引擎 */
    data class TtsEngineInfo(
        val index: Int,
        val name: String,
        val packageName: String?,
        val isLocal: Boolean
    )
    private var availableEngines: MutableList<TtsEngineInfo> = mutableListOf()
    private var currentEngineIndex: Int = 0

    // ========== 可配置参数（从Setting页面设置） ==========
    var currentApiIndex: Int = API_LOCAL_IFLYTEK  // 默认使用本地讯飞TTS
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

    /**
     * 设置日志回调（将日志输出到Home页面Debug Log）
     */
    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    /**
     * 内部日志方法：同时输出到logcat和Debug Log容器
     */
    private fun logToBoth(message: String, level: String = "D") {
        // 输出到logcat
        when (level) {
            "E" -> android.util.Log.e(TAG, message)
            "W" -> android.util.Log.w(TAG, message)
            else -> android.util.Log.d(TAG, message)
        }
        
        // 输出到Home页面Debug Log
        logCallback?.invoke("[CloudTTS] $message")
    }

    // ========== TTS引擎扫描和管理 ==========

    /**
     * 扫描系统所有可用的TTS引擎
     */
    fun scanAvailableEngines(context: Context): List<TtsEngineInfo> {
        availableEngines.clear()
        
        val packageManager = context.packageManager
        var localIndex = 0
        
        // 1. 扫描本地TTS引擎
        val intent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        
        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo.packageName
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                
                availableEngines.add(TtsEngineInfo(
                    index = localIndex,
                    name = "$appName (本地)",
                    packageName = packageName,
                    isLocal = true
                ))
                localIndex++
                
                logToBoth("Found local TTS: $appName ($packageName)")
            } catch (e: Exception) {
                logToBoth("Failed to get info for: $packageName", "W")
            }
        }
        
        // 2. 添加云端TTS选项
        val cloudStartIndex = if (availableEngines.isEmpty()) 0 else availableEngines.size
        
        availableEngines.add(TtsEngineInfo(
            index = cloudStartIndex,
            name = "微软 Edge-TTS (音质最佳)",
            packageName = null,
            isLocal = false
        ))
        
        availableEngines.add(TtsEngineInfo(
            index = cloudStartIndex + 1,
            name = "百度翻译 TTS",
            packageName = null,
            isLocal = false
        ))
        
        availableEngines.add(TtsEngineInfo(
            index = cloudStartIndex + 2,
            name = "有道词典 TTS (兜底)",
            packageName = null,
            isLocal = false
        ))
        
        logToBoth("Total TTS engines: ${availableEngines.size} (${localIndex} local + 3 cloud)")
        return availableEngines.toList()
    }

    /**
     * 获取所有可用引擎名称列表（用于Spinner）
     */
    fun getAvailableEngineNames(): List<String> {
        return availableEngines.map { it.name }
    }

    /**
     * 设置当前选择的引擎
     */
    fun setCurrentEngine(index: Int, context: Context) {
        if (index < 0 || index >= availableEngines.size) {
            logToBoth("Invalid engine index: $index", "E")
            return
        }
        
        currentEngineIndex = index
        val engine = availableEngines[index]
        
        if (engine.isLocal) {
            // 切换到本地TTS
            logToBoth("Switching to local TTS: ${engine.name}")
            initLocalTTSWithPackage(context, engine.packageName)
        } else {
            // 切换到云端TTS
            logToBoth("Switching to cloud TTS: ${engine.name}")
            // 计算云端API索引
            val cloudIndex = index - availableEngines.indexOfFirst { !it.isLocal }
            when (cloudIndex) {
                0 -> currentApiIndex = API_EDGETTS
                1 -> currentApiIndex = API_BAIDU
                2 -> currentApiIndex = API_YOUDAO
            }
        }
    }

    /**
     * 初始化指定包名的本地TTS
     */
    private fun initLocalTTSWithPackage(context: Context, packageName: String?) {
        if (ttsManager != null) {
            logToBoth("Local TTS already initialized")
            return
        }
        
        logToBoth("=== Starting Local TTS Initialization ===")
        logToBoth("Android version: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
        appContext = context.applicationContext
        
        // 检查引擎是否安装
        if (packageName != null) {
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                logToBoth("✅ TTS engine found: $packageName")
            } catch (e: Exception) {
                logToBoth("❌ TTS engine NOT found: $packageName", "E")
                logToBoth("Will use default system TTS", "W")
            }
        }
        
        ttsManager = TTSManager(context)
        
        ttsManager?.setTTSListener(object : TTSManager.TTSListener {
            override fun onSpeakStart() {
                logToBoth("🔊 Local TTS speaking started")
            }
            override fun onSpeakDone() {
                logToBoth("✅ Local TTS speaking completed")
            }
            override fun onSpeakError() {
                logToBoth("❌ Local TTS speaking failed", "E")
            }
        })
        
        // 设置初始化监听器
        ttsManager?.setOnInitListener(object : TTSManager.OnInitListener {
            override fun onInitSuccess() {
                logToBoth("✅✅✅ TTS INIT SUCCESS! Engine: ${ttsManager?.getCurrentEngineName()}")
            }
            
            override fun onInitFailed(status: Int) {
                logToBoth("❌❌❌ TTS INIT FAILED! Status: $status", "E")
                logToBoth("Status: ${ttsManager?.getStatusDescription()}", "E")
            }
        })
        
        logToBoth("Calling ttsManager.initWithEngine($packageName)")
        ttsManager?.initWithEngine(packageName)
        logToBoth("=== Local TTS Init Requested ===")
    }

    /**
     * 检查当前TTS是否就绪
     */
    fun isCurrentTTSReady(): Boolean {
        val engine = availableEngines.getOrNull(currentEngineIndex) ?: return false
        
        return if (engine.isLocal) {
            ttsManager?.isReady() == true
        } else {
            true  // 云端TTS始终可用
        }
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
            API_LOCAL_IFLYTEK -> speakWithLocalTTS(text)
            API_EDGETTS -> playWithFallback(text, ::buildEdgeTtsUrl)
            API_BAIDU -> playWithFallback(text, ::buildBaiduUrl)
            API_YOUDAO -> playWithFallback(text, ::buildYoudaoUrl)
            else -> playWithFallback(text, ::buildEdgeTtsUrl)
        }
    }

    /**
     * 播报文本（根据当前选择的引擎）
     */
    fun speakByCurrentEngine(text: String, force: Boolean = false) {
        if (text.isBlank()) return

        if (!force) {
            val now = System.currentTimeMillis()
            if (now - lastSpeakTime < speakIntervalMs) {
                logToBoth("Skipped by debounce")
                return
            }
            lastSpeakTime = now
        } else {
            lastSpeakTime = System.currentTimeMillis()
        }

        val engine = availableEngines.getOrNull(currentEngineIndex)
        
        when {
            engine == null -> {
                logToBoth("No engine selected, using default", "W")
                playWithFallback(text, ::buildEdgeTtsUrl)
            }
            engine.isLocal -> {
                // 本地TTS
                if (ttsManager?.isReady() == true) {
                    logToBoth("Using local TTS: ${engine.name}")
                    ttsManager?.speak(text)
                } else {
                    logToBoth("Local TTS not ready, fallback to cloud", "W")
                    playWithFallback(text, ::buildEdgeTtsUrl)
                }
            }
            else -> {
                // 云端TTS
                when (currentApiIndex) {
                    API_EDGETTS -> playWithFallback(text, ::buildEdgeTtsUrl)
                    API_BAIDU -> playWithFallback(text, ::buildBaiduUrl)
                    API_YOUDAO -> playWithFallback(text, ::buildYoudaoUrl)
                    else -> playWithFallback(text, ::buildEdgeTtsUrl)
                }
            }
        }
    }

    /**
     * 使用指定接口播报（用于测试按钮）
     */
    fun speakWithApi(apiIndex: Int, text: String) {
        if (text.isBlank()) return
        lastSpeakTime = System.currentTimeMillis()
        when (apiIndex) {
            API_LOCAL_IFLYTEK -> speakWithLocalTTS(text)
            API_EDGETTS -> playWithFallback(text, ::buildEdgeTtsUrl)
            API_BAIDU -> playWithFallback(text, ::buildBaiduUrl)
            API_YOUDAO -> playWithFallback(text, ::buildYoudaoUrl)
            else -> playWithFallback(text, ::buildEdgeTtsUrl)
        }
    }

    // ========== 本地讯飞TTS方法 ==========

    /**
     * 初始化本地TTS（异步，不阻塞UI）
     */
    fun initLocalTTS(context: Context) {
        if (ttsManager != null) {
            logToBoth("Local TTS already initialized")
            return
        }
        
        logToBoth("=== Starting Local TTS Initialization ===")
        logToBoth("Android version: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
        appContext = context.applicationContext
        
        // 检查讯飞引擎是否安装
        val iflytekPackage = "com.iflytek.speechsuite"
        try {
            context.packageManager.getPackageInfo(iflytekPackage, 0)
            logToBoth("✅ iFlytek engine found: $iflytekPackage")
        } catch (e: Exception) {
            logToBoth("❌ iFlytek engine NOT found: $iflytekPackage - ${e.message}", "E")
            logToBoth("Will use default system TTS engine", "W")
        }
        
        ttsManager = TTSManager(context)
        
        // ⭐ 设置TTSManager的日志输出到CloudTTS
        // 注意：TTSManager内部使用Log.d，我们需要通过logcat查看
        
        ttsManager?.setTTSListener(object : TTSManager.TTSListener {
            override fun onSpeakStart() {
                logToBoth("🔊 Local TTS speaking started")
            }
            override fun onSpeakDone() {
                logToBoth("✅ Local TTS speaking completed")
            }
            override fun onSpeakError() {
                logToBoth("❌ Local TTS speaking failed", "E")
            }
        })
        
        // ⭐ 设置初始化监听器
        ttsManager?.setOnInitListener(object : TTSManager.OnInitListener {
            override fun onInitSuccess() {
                logToBoth("✅✅✅ TTS INITIALIZATION SUCCESS! Engine: ${ttsManager?.getCurrentEngineName()}")
            }
            
            override fun onInitFailed(status: Int) {
                logToBoth("❌❌❌ TTS INITIALIZATION FAILED! Status: $status", "E")
                logToBoth("Status description: ${ttsManager?.getStatusDescription()}", "E")
            }
        })
        
        logToBoth("Calling ttsManager.initWithEngine($iflytekPackage)")
        ttsManager?.initWithEngine(iflytekPackage)
        logToBoth("=== Local TTS Initialization Requested ===")
    }

    /**
     * 检查本地TTS是否就绪
     */
    fun isLocalTTSReady(): Boolean {
        val ready = ttsManager?.isReady() == true
        logToBoth("isLocalTTSReady: $ready")
        return ready
    }

    /**
     * 使用本地讯飞TTS播报
     */
    private fun speakWithLocalTTS(text: String) {
        if (ttsManager == null && appContext != null) {
            logToBoth("Local TTS not initialized, initializing now...", "W")
            initLocalTTS(appContext!!)
        }
        
        if (ttsManager?.isReady() == true) {
            logToBoth("Using local iFlytek TTS: $text")
            ttsManager?.speak(text)
        } else {
            logToBoth("Local TTS not ready, falling back to Edge-TTS", "W")
            // 如果本地TTS未就绪，降级到Edge-TTS
            val originalIndex = currentApiIndex
            currentApiIndex = API_EDGETTS
            playWithFallback(text, ::buildEdgeTtsUrl)
            // 恢复原选择
            currentApiIndex = originalIndex
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

        // 在主线程立即显示Toast提示，在下载开始前就给用户反馈
        appContext?.let { ctx ->
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, "正在下载语音...", Toast.LENGTH_SHORT).show()
            }
        }

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
            API_LOCAL_IFLYTEK -> "本地讯飞TTS（离线）"
            API_EDGETTS -> "微软Edge-TTS (${EDGETTS_VOICE_NAMES[voice] ?: voice})"
            API_BAIDU -> "百度翻译TTS"
            API_YOUDAO -> "有道词典TTS"
            else -> "未知接口"
        }
    }

    /**
     * 重置为默认车载最优配置
     */
    fun resetToDefaults() {
        currentApiIndex = API_LOCAL_IFLYTEK  // 默认使用本地讯飞TTS
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
