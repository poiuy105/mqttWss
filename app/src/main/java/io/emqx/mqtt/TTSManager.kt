package io.emqx.mqtt

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TTSManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initStatus: Int = -1

    interface TTSListener {
        fun onSpeakStart() {}
        fun onSpeakDone() {}
        fun onSpeakError() {}
    }

    interface OnInitListener {
        fun onInitSuccess()
        fun onInitFailed(status: Int)
    }

    private var ttsListener: TTSListener? = null
    private var initListener: OnInitListener? = null

    fun setTTSListener(listener: TTSListener?) {
        this.ttsListener = listener
    }

    fun setOnInitListener(listener: OnInitListener?) {
        this.initListener = listener
    }

    init {
        initTTS()
    }

    private fun initTTS() {
        Log.d("TTSManager", "initTTS: 开始初始化（小米适配）")
        tts = TextToSpeech(context) { status ->
            initStatus = status
            Log.d("TTSManager", "initTTS: 回调状态 status=$status")

            if (status == TextToSpeech.SUCCESS) {
                val langResult = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                Log.d("TTSManager", "setLanguage 结果=$langResult")

                when (langResult) {
                    TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_AVAILABLE -> {
                        isInitialized = true
                        setupTTSListener()
                        Log.d("TTSManager", "✅ 初始化成功 isReady=true")
                        initListener?.onInitSuccess()
                    }
                    TextToSpeech.LANG_MISSING_DATA,
                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                        Log.e("TTSManager", "❌ 中文语言包缺失/不支持")
                        isInitialized = false
                        initListener?.onInitFailed(status)
                    }
                    else -> {
                        isInitialized = true
                        setupTTSListener()
                        initListener?.onInitSuccess()
                    }
                }
            } else {
                Log.e("TTSManager", "❌ TTS 引擎初始化失败 status=$status")
                isInitialized = false
                initListener?.onInitFailed(status)
            }
        }

        Handler().postDelayed({
            if (!isInitialized && initStatus == -1) {
                Log.e("TTSManager", "❌ 初始化超时（小米引擎卡住）")
                initListener?.onInitFailed(-2)
            }
        }, 10000)
    }

    private fun setupTTSListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                ttsListener?.onSpeakStart()
            }
            override fun onDone(utteranceId: String?) {
                ttsListener?.onSpeakDone()
            }
            override fun onError(utteranceId: String?) {
                ttsListener?.onSpeakError()
            }
        })
    }

    fun speak(text: String) {
        if (!isInitialized) {
            Log.w("TTSManager", "⚠️ 未初始化，不能朗读")
            return
        }
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun isReady(): Boolean = isInitialized
    fun getInitStatus(): Int = initStatus

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}