package io.emqx.mqtt

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors

class TTSManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initStatus: Int = -1
    private val executor = Executors.newSingleThreadExecutor()
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelay = 1000L // 1秒
    private val isAndroid10OrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

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
    fun getInitStatus(): Int = initStatus

    init { executor.execute { initTTS() } }

    private fun initTTS() {
        // 尝试使用默认引擎初始化
        retryCount = 0
        initWithEngine(null)
    }
    
    /**
     * 重新初始化TTS
     */
    fun reinitialize() {
        executor.execute { 
            release()
            initTTS()
        }
    }

    private fun initWithEngine(enginePackageName: String?) {
        tts = TextToSpeech(context, object : TextToSpeech.OnInitListener {
            override fun onInit(status: Int) {
                initStatus = status
                if (status == TextToSpeech.SUCCESS) {
                    val lang = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Locale.SIMPLIFIED_CHINESE else Locale.CHINA
                    val langResult = tts?.setLanguage(lang)
                    if (langResult == TextToSpeech.LANG_AVAILABLE || langResult == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                        isInitialized = true
                        setupListener()
                        initListener?.onInitSuccess()
                    } else {
                        // 语言不可用，尝试使用其他可用引擎
                        tryNextEngine()
                    }
                } else {
                    // 初始化失败，尝试使用其他可用引擎
                    tryNextEngine()
                }
            }
        }, enginePackageName)

        Handler(Looper.getMainLooper()).postDelayed({ 
            if (initStatus == -1) {
                // 初始化超时，尝试重试
                if (retryCount < maxRetries) {
                    retryCount++
                    Log.d("TTSManager", "Initialization timeout, retrying ($retryCount/$maxRetries)...")
                    tts?.shutdown()
                    Handler(Looper.getMainLooper()).postDelayed({ 
                        initWithEngine(enginePackageName)
                    }, retryDelay)
                } else {
                    // 达到最大重试次数
                    initListener?.onInitFailed(-2)
                }
            }
        }, 8000)
    }

    private fun tryNextEngine() {
        // 获取所有可用的TTS引擎
        val engines = tts?.engines
        if (engines != null && engines.isNotEmpty()) {
            // 尝试使用第一个可用引擎
            for (engine in engines) {
                if (!engine.name.isNullOrEmpty()) {
                    Log.d("TTSManager", "Trying engine: ${engine.name}")
                    tts?.shutdown()
                    retryCount = 0 // 重置重试计数
                    initWithEngine(engine.name)
                    return
                }
            }
        }
        // 没有可用引擎，通知初始化失败
        initListener?.onInitFailed(-1)
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { ttsListener?.onSpeakStart() }
            override fun onDone(id: String?) { ttsListener?.onSpeakDone() }
            override fun onError(id: String?) { ttsListener?.onSpeakError() }
        })
    }

    fun speak(text: String) {
        executor.execute {
            if (!isInitialized) return@execute
            
            // 检查是否在Android 10及以上版本的后台
            if (isAndroid10OrAbove) {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val appProcessInfo = activityManager.runningAppProcesses?.find { it.pid == android.os.Process.myPid() }
                if (appProcessInfo?.importance != android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    Log.d("TTSManager", "App in background, skipping TTS on Android 10+")
                    return@execute
                }
            }
            
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts_${System.currentTimeMillis()}")
        }
    }

    fun setSpeechRate(rate: Float) {
        executor.execute { tts?.setSpeechRate(rate) }
    }

    fun release() {
        executor.execute {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
        executor.shutdown()
    }

    /**
     * 检查TTS引擎是否可用
     * @return true如果有可用的TTS引擎，false否则
     */
    fun hasAvailableEngine(): Boolean {
        val engines = tts?.engines
        return engines != null && engines.isNotEmpty()
    }

    /**
     * 获取TTS引擎检查Intent，用于引导用户安装或选择TTS引擎
     * @return 用于检查TTS数据的Intent
     */
    fun getTTSCheckIntent(): android.content.Intent {
        return android.content.Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
    }
}