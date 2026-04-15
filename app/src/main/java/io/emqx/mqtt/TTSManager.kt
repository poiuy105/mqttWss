package io.emqx.mqtt

import android.content.Context
import android.os.Build
import android.os.Bundle
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
        tts = TextToSpeech(context) { status ->
            initStatus = status
            if (status == TextToSpeech.SUCCESS) {
                val lang = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Locale.SIMPLIFIED_CHINESE else Locale.CHINA
                val langResult = tts?.setLanguage(lang)
                if (langResult == TextToSpeech.LANG_AVAILABLE || langResult == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    isInitialized = true
                    setupListener()
                    initListener?.onInitSuccess()
                } else {
                    initListener?.onInitFailed(-3)
                }
            } else {
                initListener?.onInitFailed(status)
            }
        }

        android.os.Handler().postDelayed({
            if (initStatus == -1) initListener?.onInitFailed(-2)
        }, 8000)
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = ttsListener?.onSpeakStart()
            override fun onDone(id: String?) = ttsListener?.onSpeakDone()
            override fun onError(id: String?) = ttsListener?.onSpeakError()
        })
    }

    fun speak(text: String) {
        executor.execute {
            if (!isInitialized) return@execute
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts_${System.currentTimeMillis()}")
        }
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
}