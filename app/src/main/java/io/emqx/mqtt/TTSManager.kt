package io.emqx.mqtt

import android.content.Context
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
                Log.d("TTSManager", "initTTS: SUCCESS, isInitialized=true")

                val result = tts?.setLanguage(Locale.CHINA)
                Log.d("TTSManager", "initTTS: setLanguage(Locale.CHINA) result: $result")
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.d("TTSManager", "initTTS: CHINA not supported, trying US")
                    val fallbackResult = tts?.setLanguage(Locale.US)
                    Log.d("TTSManager", "initTTS: setLanguage(Locale.US) result: $fallbackResult")
                    if (fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
                        fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.d("TTSManager", "initTTS: US also not supported")
                        isInitialized = false
                    }
                }

                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
            } else {
                Log.e("TTSManager", "initTTS: FAILED with status: $status")
                isInitialized = false
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("TTSManager", "onStart: $utteranceId")
                ttsListener?.onSpeakStart()
            }

            override fun onDone(utteranceId: String?) {
                Log.d("TTSManager", "onDone: $utteranceId")
                ttsListener?.onSpeakDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("TTSManager", "onError: $utteranceId")
                ttsListener?.onSpeakError()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("TTSManager", "onError: $utteranceId, errorCode: $errorCode")
                ttsListener?.onSpeakError()
            }
        })
    }

    fun getInitStatus(): Int = initStatus

    fun speak(text: String) {
        Log.d("TTSManager", "speak: text='$text', isInitialized=$isInitialized")
        if (!isInitialized) {
            Log.w("TTSManager", "speak: Not initialized, cannot speak")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
    }

    fun speakWithChinaLocale(text: String) {
        Log.d("TTSManager", "speakWithChinaLocale: text='$text', isInitialized=$isInitialized")
        if (!isInitialized) return
        tts?.setLanguage(Locale.CHINA)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_china")
    }

    fun speakWithDefaultLocale(text: String) {
        Log.d("TTSManager", "speakWithDefaultLocale: text='$text', isInitialized=$isInitialized")
        if (!isInitialized) return
        tts?.setLanguage(Locale.getDefault())
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_default")
    }

    fun speakWithUSLocale(text: String) {
        Log.d("TTSManager", "speakWithUSLocale: text='$text', isInitialized=$isInitialized")
        if (!isInitialized) return
        tts?.setLanguage(Locale.US)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_us")
    }

    fun speakWithUKLocale(text: String) {
        Log.d("TTSManager", "speakWithUKLocale: text='$text', isInitialized=$isInitialized")
        if (!isInitialized) return
        tts?.setLanguage(Locale.UK)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_uk")
    }

    fun speakAdd(text: String) {
        Log.d("TTSManager", "speakAdd: text='$text', isInitialized=$isInitialized")
        if (!isInitialized) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "utteranceId_add")
    }

    fun speakWithSlowRate(text: String) {
        Log.d("TTSManager", "speakWithSlowRate: text='$text', isInitialized=$isInitialized")
        if (!isInitialized) return
        tts?.setSpeechRate(0.5f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_slow")
        tts?.setSpeechRate(1.0f)
    }

    fun speakWithFastRate(text: String) {
        Log.d("TTSManager", "speakWithFastRate: text='$text', isInitialized=$isInitialized")
        if (!isInitialized) return
        tts?.setSpeechRate(2.0f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_fast")
        tts?.setSpeechRate(1.0f)
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

    fun getAvailableLanguages(): List<Locale>? {
        return tts?.availableLanguages?.toList()
    }

    fun getEngineInfo(): String {
        val engine = tts?.currentEngine
        Log.d("TTSManager", "Current engine: $engine")
        return engine ?: "unknown"
    }
}