package io.emqx.mqtt

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TTSManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

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
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                val result = tts?.setLanguage(Locale.CHINA)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    val fallbackResult = tts?.setLanguage(Locale.US)
                    if (fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
                        fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        isInitialized = false
                    }
                }
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                ttsListener?.onSpeakStart()
            }

            override fun onDone(utteranceId: String?) {
                ttsListener?.onSpeakDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                ttsListener?.onSpeakError()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                ttsListener?.onSpeakError()
            }
        })
    }

    fun speak(text: String) {
        if (!isInitialized) return
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "utteranceId"
        )
    }

    fun speakAdd(text: String) {
        if (!isInitialized) return
        tts?.speak(
            text,
            TextToSpeech.QUEUE_ADD,
            null,
            "utteranceId"
        )
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