package io.emqx.mqtt

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TTSManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    private val initStatus = AtomicInteger(-1)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // 车机优化：重试和引擎切换机制
    private var retryCount = 0
    private val maxRetries = 5  // 增加到5次，兼容Android 10老旧车机
    private val retryDelayMs = 3000L  // 增加到3秒，给车机更多响应时间
    private var triedEngines = mutableSetOf<String>()
    private val isInitializing = AtomicBoolean(false)
    private var timeoutRunnable: Runnable? = null

    companion object {
        private const val TAG = "TTSManager"
        // 车机TTS初始化需要更长时间（Android 10车机可能需要20-40秒）
        const val INIT_TIMEOUT_MS = 40000L  // 增加到40秒以兼容老旧车机
    }

    interface TTSListener {
        fun onSpeakStart(){}
        fun onSpeakDone(){}
        fun onSpeakError(){}
    }

    interface OnInitListener {
        fun onInitSuccess()
        fun onInitFailed(status: Int)
    }

    private var ttsListener: TTSListener? = null
    private var initListener: OnInitListener? = null

    fun setTTSListener(listener: TTSListener?) { this.ttsListener = listener }
    fun setOnInitListener(listener: OnInitListener?) { this.initListener = listener }
    fun isReady(): Boolean = isInitialized
    fun getInitStatus(): Int = initStatus.get()

    /** 获取当前使用的引擎包名（仅初始化成功后有效） */
    fun getCurrentEngineName(): String? = tts?.defaultEngine

    /** 获取当前初始化状态描述文字 */
    fun getStatusDescription(): String {
        if (isInitialized) return "READY (${tts?.defaultEngine ?: "unknown"})"
        val status = initStatus.get()
        if (isInitializing.get()) return "INITIALIZING... (status=$status)"
        return when (status) {
            -1 -> "NOT_INITIALIZED"
            -2 -> "TIMEOUT"
            -3 -> "LANG_NOT_SUPPORTED"
            -4 -> "NO_AVAILABLE_LANGUAGE"
            -5 -> "EXCEPTION"
            else -> "FAILED(status=$status)"
        }
    }

    // ★★★ 不再自动初始化，改为手动调用 initWithEngine() ★★★

    /**
     * 手动初始化TTS（指定引擎）
     * @param enginePackageName null=系统默认引擎, 其他=指定引擎包名(如"com.google.android.tts")
     */
    fun initWithEngine(enginePackageName: String?) {
        Log.d(TAG, "initWithEngine: engine='$enginePackageName'")
        if (isInitializing.get()) {
            Log.w(TAG, "Already initializing, skipping")
            return
        }
        // 先释放旧实例
        releaseInternal()
        retryCount = 0
        triedEngines.clear()
        if (enginePackageName != null) triedEngines.add(enginePackageName)
        initStatus.set(-1)
        isInitialized = false
        executor.execute { initTTS(enginePackageName) }
    }

    /**
     * 使用默认引擎重新初始化（兼容旧接口）
     */
    fun reinitialize() {
        initWithEngine(null)
    }

    private fun initTTS(enginePackageName: String?) {
        if (!isInitializing.compareAndSet(false, true)) {
            Log.w(TAG, "initTTS: already initializing, skipping")
            return
        }

        val engineDesc = enginePackageName ?: "default"
        Log.d(TAG, "initTTS: starting with engine='$engineDesc', retry=$retryCount/$maxRetries")

        try {
            Log.d(TAG, "Creating TextToSpeech instance with engine: $enginePackageName")
            tts = TextToSpeech(context, object : TextToSpeech.OnInitListener {
                override fun onInit(status: Int) {
                    Log.d(TAG, "⭐ onInit callback received! status=$status")
                    val prevStatus = initStatus.getAndSet(status)
                    Log.d(TAG, "onInit: status=$status, previous=$prevStatus, engine='$engineDesc'")

                    when (status) {
                        TextToSpeech.SUCCESS -> {
                            handleInitSuccess(enginePackageName)
                        }
                        else -> {
                            handleInitFailure(status, enginePackageName)
                        }
                    }
                }
            }, enginePackageName)
            
            Log.d(TAG, "TextToSpeech instance created successfully: ${tts != null}")

            // 设置超时检测（车机需要更长时间）
            timeoutRunnable = Runnable {
                if (initStatus.get() == -1 && isInitialized.not()) {
                    Log.w(TAG, "initTTS: TIMEOUT after ${INIT_TIMEOUT_MS}ms, engine='$engineDesc'")
                    handleInitFailure(-2, enginePackageName)
                }
            }
            mainHandler.postDelayed(timeoutRunnable!!, INIT_TIMEOUT_MS)
            Log.d(TAG, "Timeout check scheduled for ${INIT_TIMEOUT_MS}ms")

        } catch (e: Exception) {
            Log.e(TAG, "initTTS: EXCEPTION during TextToSpeech creation", e)
            Log.e(TAG, "Exception type: ${e.javaClass.name}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            isInitializing.set(false)
            initStatus.set(-5)
            initListener?.onInitFailed(-5)
        }
    }

    private fun handleInitSuccess(enginePackageName: String?) {
        Log.d(TAG, "handleInitSuccess: TextToSpeech.SUCCESS received")
        Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        
        // 尝试设置中文语言
        // Android 10 (API 29) 及以下使用 Locale.CHINA，Android 11+ 使用 SIMPLIFIED_CHINESE
        val targetLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) 
            Locale.SIMPLIFIED_CHINESE else Locale.CHINA
        
        Log.d(TAG, "Attempting to set language: $targetLocale")
        val langResult = tts?.setLanguage(targetLocale)
        Log.d(TAG, "handleInitSuccess: setLanguage($targetLocale)=$langResult")

        when (langResult) {
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE -> {
                // 语言设置成功
                cancelTimeout()
                isInitialized = true
                isInitializing.set(false)
                
                setupListener()
                Log.i(TAG, "handleInitSuccess: TTS ready! Engine=${tts?.defaultEngine}")
                initListener?.onInitSuccess()
            }
            TextToSpeech.LANG_MISSING_DATA -> {
                // 缺少语言数据 - 尝试安装或降级到可用语言
                Log.w(TAG, "handleInitSuccess: LANG_MISSING_DATA for $targetLocale, trying fallback")
                tryFallbackLanguage(enginePackageName)
            }
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                // 中文不支持 - 尝试其他语言或引擎
                Log.w(TAG, "handleInitSuccess: LANG_NOT_SUPPORTED for $targetLocale")
                tryFallbackLanguage(enginePackageName)
            }
            else -> {
                Log.e(TAG, "handleInitSuccess: unknown langResult=$langResult")
                handleInitFailure(-3, enginePackageName)
            }
        }
    }

    /**
     * 尝试使用备用语言（英语等）初始化
     */
    private fun tryFallbackLanguage(enginePackageName: String?) {
        // 尝试系统默认语言
        val defaultLangResult = tts?.setLanguage(Locale.getDefault())
        Log.d(TAG, "tryFallbackLanguage: Locale.getDefault()=${Locale.getDefault()}, result=$defaultLangResult")
        
        if (defaultLangResult == TextToSpeech.LANG_AVAILABLE || defaultLangResult == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            cancelTimeout()
            isInitialized = true
            isInitializing.set(false)
            setupListener()
            Log.i(TAG, "tryFallbackSuccess: Using ${Locale.getDefault()} language")
            initListener?.onInitSuccess()
        } else {
            // 再尝试英语
            val englishResult = tts?.setLanguage(Locale.ENGLISH)
            if (englishResult == TextToSpeech.LANG_AVAILABLE || englishResult == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                cancelTimeout()
                isInitialized = true
                isInitializing.set(false)
                setupListener()
                Log.i(TAG, "tryFallbackSuccess: Using English language")
                initListener?.onInitSuccess()
            } else {
                Log.e(TAG, "tryFallbackLanguage: no available language found")
                handleInitFailure(-4, enginePackageName)
            }
        }
    }

    private fun handleInitFailure(status: Int, currentEngine: String?) {
        Log.e(TAG, "handleInitFailure: status=$status, engine='$currentEngine', retry=$retryCount/$maxRetries")

        // 清理当前失败的TTS实例
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "handleInitFailure: error shutting down tts", e)
        }
        tts = null

        // 策略：先尝试其他引擎，再重试当前引擎
        if (retryCount < maxRetries) {
            retryCount++

            // 收集所有可用的TTS引擎
            val tempTts = TextToSpeech(context, null)
            val engines = tempTts.engines?.toList()?.filter { 
                it.name.isNotEmpty() && it.name !in triedEngines 
            }
            
            if (!engines.isNullOrEmpty()) {
                // 有其他未尝试的引擎
                val nextEngine = engines.first()
                triedEngines.add(nextEngine.name!!)
                tempTts.shutdown()
                
                Log.d(TAG, "handleInitFailure: Trying alternative engine: ${nextEngine.name} (${retryCount}/$maxRetries)")
                isInitializing.set(false)
                initStatus.set(-1)
                
                // 延迟后重试下一个引擎
                executor.execute {
                    Thread.sleep(retryDelayMs)
                    initTTS(nextEngine.name)
                }
            } else {
                // 没有其他引擎了，用默认引擎重试
                tempTts.shutdown()
                
                Log.d(TAG, "handleInitFailure: Retrying with default engine (${retryCount}/$maxRetries)")
                isInitializing.set(false)
                initStatus.set(-1)
                
                executor.execute {
                    Thread.sleep(retryDelayMs)
                    initTTS(null)
                }
            }
        } else {
            // 达到最大重试次数，彻底失败
            cancelTimeout()
            isInitializing.set(false)
            Log.e(TAG, "handleInitFailure: Max retries ($maxRetries) exceeded. Final status=$status")
            initListener?.onInitFailed(status)
        }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun setupListener() {
        try {
            val listener = object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    Log.d(TAG, "⭐ UtteranceProgressListener onStart: id=$id")
                    mainHandler.post {
                        ttsListener?.onSpeakStart()
                    }
                }
                
                override fun onDone(id: String?) {
                    Log.d(TAG, "⭐ UtteranceProgressListener onDone: id=$id")
                    mainHandler.post {
                        ttsListener?.onSpeakDone()
                    }
                }
                
                override fun onError(id: String?) {
                    Log.e(TAG, "⭐ UtteranceProgressListener onError: id=$id")
                    mainHandler.post {
                        ttsListener?.onSpeakError()
                    }
                }
                
                // Android 21+ 需要实现这个方法
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "⭐ UtteranceProgressListener onError (code): id=$utteranceId, code=$errorCode")
                    mainHandler.post {
                        ttsListener?.onSpeakError()
                    }
                }
            }
            
            val setResult = tts?.setOnUtteranceProgressListener(listener)
            Log.d(TAG, "setupListener: setOnUtteranceProgressListener result=$setResult")
            
            if (setResult != TextToSpeech.SUCCESS) {
                Log.e(TAG, "setupListener: Failed to set listener, result=$setResult")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupListener: exception", e)
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "speak: text is blank, ignoring")
            return
        }
        
        // 必须在主线程执行speak，否则UtteranceProgressListener可能不工作
        mainHandler.post {
            if (!isInitialized) {
                Log.w(TAG, "speak: TTS not initialized, skipping speak('$text')")
                ttsListener?.onSpeakError()
                return@post
            }
            
            try {
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
                    // Android 21+ 支持流类型设置
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
                    }
                    // Android 22+ 支持语音质量
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        putInt(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, 1)
                    }
                }
                
                val utteranceId = "tts_${System.currentTimeMillis()}"
                Log.d(TAG, "⭐ speak: '$text' (id=$utteranceId, engine=${tts?.defaultEngine})")
                
                val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                Log.d(TAG, "⭐ speak result: $result (SUCCESS=${TextToSpeech.SUCCESS}, ERROR=${TextToSpeech.ERROR})")
                
                if (result == TextToSpeech.ERROR) {
                    Log.e(TAG, "speak: ERROR occurred!")
                    ttsListener?.onSpeakError()
                } else if (result != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "speak: unexpected result=$result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "speak: exception", e)
                ttsListener?.onSpeakError()
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        executor.execute {
            try {
                val safeRate = rate.coerceIn(0.1f, 3.0f)
                tts?.setSpeechRate(safeRate)
                Log.d(TAG, "setSpeechRate: $safeRate")
            } catch (e: Exception) {
                Log.e(TAG, "setSpeechRate: exception", e)
            }
        }
    }

    /**
     * 检查是否有可用的TTS引擎
     */
    fun hasAvailableEngine(): Boolean {
        return try {
            val tempTts = TextToSpeech(context, null)
            val engines = tempTts.engines
            val has = !engines.isNullOrEmpty()
            tempTts.shutdown()
            Log.d(TAG, "hasAvailableEngine: $has (engines=${engines?.size ?: 0})")
            has
        } catch (e: Exception) {
            Log.e(TAG, "hasAvailableEngine: exception", e)
            false
        }
    }

    /**
     * 获取所有可用TTS引擎信息
     */
    fun getEnginesInfo(): List<Pair<String, String>> {
        return try {
            val tempTts = TextToSpeech(context, null)
            val info = tempTts.engines?.map { 
                Pair(it.name ?: "", it.label ?: "") 
            }.orEmpty()
            tempTts.shutdown()
            info
        } catch (e: Exception) {
            Log.e(TAG, "getEnginesInfo: exception", e)
            emptyList()
        }
    }

    /**
     * 获取TTS引擎检查Intent，用于引导用户安装或选择TTS引擎
     */
    fun getTTSCheckIntent(): android.content.Intent {
        return android.content.Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
    }

    private fun releaseInternal() {
        cancelTimeout()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "releaseInternal: error", e)
        }
        tts = null
        isInitialized = false
    }

    fun release() {
        executor.execute {
            releaseInternal()
            // 不关闭executor，因为可能在release后被重新初始化
        }
    }
}
