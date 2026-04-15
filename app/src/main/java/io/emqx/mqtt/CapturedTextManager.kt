package io.emqx.mqtt

import android.util.Log

object CapturedTextManager {
    private val listeners = mutableListOf<(String, String) -> Unit>()
    private val capturedTexts = ArrayList<CapturedText>()

    fun addListener(listener: (String, String) -> Unit) {
        listeners.add(listener)
        Log.d("CapturedTextManager", "Listener added, total: ${listeners.size}")
    }

    fun removeListener(listener: (String, String) -> Unit) {
        listeners.remove(listener)
    }

    fun onTextCaptured(text: String, packageName: String) {
        Log.d("CapturedTextManager", "Text captured: $packageName -> $text")
        val captured = CapturedText(text, packageName, System.currentTimeMillis())
        capturedTexts.add(0, captured)
        listeners.forEach { it(text, packageName) }
    }

    fun getAllCaptured(): List<CapturedText> = capturedTexts.toList()

    fun clearAll() {
        capturedTexts.clear()
    }
}
