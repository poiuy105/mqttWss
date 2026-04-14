package io.emqx.mqtt

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import java.util.Locale
import java.util.UUID

class TTSManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isChineseAvailable = false
    private var onCompleteListener: (() -> Unit)? = null

    companion object {
        @Volatile
        private var instance: TTSManager? = null

        fun getInstance(context: Context): TTSManager {
            return instance ?: synchronized(this) {
                instance ?: TTSManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        Log.d("TTSManager", "Initializing TTS...")
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        Log.d("TTSManager", "onInit called with status: $status")
        if (status == TextToSpeech.SUCCESS) {
            val chineseResult = tts?.isLanguageAvailable(Locale.CHINESE)
            Log.d("TTSManager", "Chinese language availability: $chineseResult")

            if (chineseResult != null && chineseResult > TextToSpeech.LANG_MISSING_DATA) {
                val setLangResult = tts?.setLanguage(Locale.CHINESE)
                Log.d("TTSManager", "setLanguage(CHINESE) result: $setLangResult")
                if (setLangResult == TextToSpeech.LANG_AVAILABLE || setLangResult == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    isChineseAvailable = true
                    Log.d("TTSManager", "Chinese TTS is available")
                }
            }

            if (!isChineseAvailable) {
                Log.d("TTSManager", "Chinese not available, trying default")
                val defaultResult = tts?.setLanguage(Locale.getDefault())
                Log.d("TTSManager", "setLanguage(default) result: $defaultResult")
            }

            isInitialized = true
            Log.d("TTSManager", "TTS initialized successfully, isInitialized=true")

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("TTSManager", "onStart: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("TTSManager", "onDone: $utteranceId")
                    onCompleteListener?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    Log.e("TTSManager", "onError: $utteranceId")
                }
            })

            Toast.makeText(context, "TTS initialized, Chinese available: $isChineseAvailable", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("TTSManager", "TTS initialization failed with status: $status")
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        Log.d("TTSManager", "speak() called with text: $text")
        Log.d("TTSManager", "isInitialized: $isInitialized")

        if (!isInitialized) {
            Log.e("TTSManager", "TTS not initialized, skipping speak")
            Toast.makeText(context, "TTS not ready yet", Toast.LENGTH_SHORT).show()
            onComplete?.invoke()
            return
        }

        onCompleteListener = onComplete
        val utteranceId = UUID.randomUUID().toString()

        if (isChineseAvailable) {
            tts?.setLanguage(Locale.CHINESE)
        } else {
            tts?.setLanguage(Locale.getDefault())
        }

        Log.d("TTSManager", "Calling tts.speak() with utteranceId: $utteranceId")
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Log.d("TTSManager", "speak() returned: $result")

        if (result == TextToSpeech.ERROR) {
            Log.e("TTSManager", "speak() returned ERROR")
            Toast.makeText(context, "TTS speak failed", Toast.LENGTH_SHORT).show()
        }
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

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
