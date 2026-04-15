package io.emqx.mqtt

import android.content.Context
import android.os.Bundle
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
        fun onInitFailed()
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
        Log.d("TTSManager", "initTTS: Starting initialization...")
        tts = TextToSpeech(context) { status ->
            initStatus = status
            Log.d("TTSManager", "initTTS: onInit callback with status: $status")
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                Log.d("TTSManager", "initTTS: SUCCESS")
                setupTTSListener()
                initListener?.onInitSuccess()
            } else {
                Log.e("TTSManager", "initTTS: FAILED with status: $status")
                isInitialized = false
                initListener?.onInitFailed()
            }
        }
    }

    private fun setupTTSListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("TTSManager", "onStart: $utteranceId")
                ttsListener?.onSpeakStart()
            }

            override fun onDone(utteranceId: String?) {
                Log.d("TTSManager", "onDone: $utteranceId")
                ttsListener?.onSpeakDone()
            }

            override fun onError(utteranceId: String?) {
                Log.e("TTSManager", "onError: $utteranceId")
                ttsListener?.onSpeakError()
            }
        })
    }

    fun getInitStatus(): Int = initStatus
    fun isReady(): Boolean = isInitialized

    fun speak(text: String) {
        speakWithParams(text, 1.0f, 0.0f)
    }

    fun speakWithParams(text: String, volume: Float = 1.0f, pan: Float = 0.0f, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        Log.d("TTSManager", "speakWithParams: text='$text', isInitialized=$isInitialized")
        if (!isInitialized) {
            Log.w("TTSManager", "TTS not initialized, cannot speak")
            return
        }

        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, pan)

        val utteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(text, queueMode, params, utteranceId)
    }

    fun speakChinese(text: String) {
        if (!isInitialized) return
        tts?.setLanguage(Locale.CHINA)
        speak(text)
    }

    fun speakEnglish(text: String) {
        if (!isInitialized) return
        tts?.setLanguage(Locale.US)
        speak(text)
    }

    fun speakAdd(text: String) {
        speakWithParams(text, queueMode = TextToSpeech.QUEUE_ADD)
    }

    fun stop() {
        tts?.stop()
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    fun setLanguage(locale: Locale): Int {
        return tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}