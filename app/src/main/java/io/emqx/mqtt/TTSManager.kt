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

    private var ttsListener: TTSListener? = null

    fun setTTSListener(listener: TTSListener?) {
        this.ttsListener = listener
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
            } else {
                Log.e("TTSManager", "initTTS: FAILED with status: $status")
                isInitialized = false
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

    fun speak(text: String) {
        speakWithParams(text, 1.0f, 0.0f)
    }

    fun speakWithParams(text: String, volume: Float = 1.0f, pan: Float = 0.0f, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        Log.d("TTSManager", "speakWithParams: text='$text', isInitialized=$isInitialized, volume=$volume, pan=$pan")
        if (!isInitialized) {
            Log.w("TTSManager", "TTS not initialized, cannot speak")
            return
        }

        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, pan)

        val utteranceId = "tts_${System.currentTimeMillis()}"
        Log.d("TTSManager", "speakWithParams: speaking with utteranceId=$utteranceId")

        tts?.speak(text, queueMode, params, utteranceId)
    }

    fun speakChinese(text: String) {
        Log.d("TTSManager", "speakChinese: text='$text'")
        if (!isInitialized) return

        val result = tts?.setLanguage(Locale.CHINA)
        Log.d("TTSManager", "setLanguage(CHINA) result: $result")
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TTSManager", "Chinese not supported")
            return
        }
        speak(text)
    }

    fun speakEnglish(text: String) {
        Log.d("TTSManager", "speakEnglish: text='$text'")
        if (!isInitialized) return

        val result = tts?.setLanguage(Locale.US)
        Log.d("TTSManager", "setLanguage(US) result: $result")
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TTSManager", "English not supported")
            return
        }
        speak(text)
    }

    fun speakAdd(text: String) {
        speakWithParams(text, queueMode = TextToSpeech.QUEUE_ADD)
    }

    fun stop() {
        Log.d("TTSManager", "stop: called")
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

    fun isReady(): Boolean = isInitialized
}