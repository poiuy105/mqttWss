package io.emqx.mqtt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object CapturedTextManager {
    private val listeners = mutableListOf<(CapturedText) -> Unit>()
    private val capturedTexts = ArrayList<CapturedText>()
    var isEnabled = false

    private var excludedApps = hashSetOf<String>()
    private var whitelistApp: String? = null
    private var prefs: SharedPreferences? = null
    private var context: Context? = null

    // Home Assistant & Only Capture 功能 (from kotlin_temp)
    private var onlyCaptureFrames = ArrayList<CaptureFrame>()
    private var onlyCapturePrefix = ""
    private var onlyCaptureSuffix = ""
    private var isOnlyCaptureEnabled = false
    private var sendToHomeAssistant = false
    private var lastCommandTime = 0L
    private const val DEBOUNCE_DELAY = 1000L

    fun init(context: Context) {
        this.context = context
        prefs = context.getSharedPreferences("capture_settings", Context.MODE_PRIVATE)
        loadSettings()
    }

    fun addListener(listener: (CapturedText) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (CapturedText) -> Unit) {
        listeners.remove(listener)
    }

    fun onTextCaptured(
        text: String,
        packageName: String,
        boundsLeft: Int = -1,
        boundsTop: Int = -1,
        boundsRight: Int = -1,
        boundsBottom: Int = -1,
        viewDepth: Int = -1,
        viewClass: String = ""
    ) {
        if (!isEnabled) return

        if (whitelistApp != null) {
            if (packageName != whitelistApp) return
        } else if (excludedApps.contains(packageName)) {
            return
        }

        // Apply text prefix/suffix restrictions (HA feature)
        val prefix = onlyCapturePrefix
        val suffix = onlyCaptureSuffix
        
        if (prefix.isNotEmpty() && !text.startsWith(prefix)) {
            return
        }
        
        if (suffix.isNotEmpty() && !text.endsWith(suffix)) {
            return
        }

        // Selective monitoring: only process text if it matches frames in Only Capture Frame
        if (onlyCaptureFrames.isNotEmpty()) {
            val matchesFrame = onlyCaptureFrames.any { frame ->
                frame.packageName == packageName &&
                (frame.viewDepth == -1 || frame.viewDepth == viewDepth) &&
                (frame.viewClass.isEmpty() || frame.viewClass == viewClass) &&
                (frame.boundsLeft == -1 || Math.abs(frame.boundsLeft - boundsLeft) < 10) &&
                (frame.boundsTop == -1 || Math.abs(frame.boundsTop - boundsTop) < 10) &&
                (frame.boundsRight == -1 || Math.abs(frame.boundsRight - boundsRight) < 10) &&
                (frame.boundsBottom == -1 || Math.abs(frame.boundsBottom - boundsBottom) < 10)
            }
            
            if (!matchesFrame) {
                return
            }
        }

        val captured = CapturedText(
            text = text,
            packageName = packageName,
            timestamp = System.currentTimeMillis(),
            boundsLeft = boundsLeft,
            boundsTop = boundsTop,
            boundsRight = boundsRight,
            boundsBottom = boundsBottom,
            viewDepth = viewDepth,
            viewClass = viewClass
        )
        capturedTexts.add(0, captured)
        listeners.forEach { it(captured) }

        // Send to Home Assistant if enabled and not in debounce period
        if (sendToHomeAssistant) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCommandTime > DEBOUNCE_DELAY) {
                lastCommandTime = currentTime
                
                var validText = text
                if (onlyCapturePrefix.isNotEmpty() && validText.startsWith(onlyCapturePrefix)) {
                    validText = validText.substring(onlyCapturePrefix.length)
                }
                if (onlyCaptureSuffix.isNotEmpty() && validText.endsWith(onlyCaptureSuffix)) {
                    validText = validText.substring(0, validText.length - onlyCaptureSuffix.length)
                }
                validText = validText.trim()
                
                if (validText.isNotEmpty()) {
                    context?.let { ctx ->
                        HomeAssistantService.sendCommand(ctx, validText) { success, speech ->
                            if (success && speech != null) {
                                (ctx as? MainActivity)?.let { activity ->
                                    // 必须在主线程执行UI操作（OkHttp回调在子线程）
                                    activity.runOnUiThread {
                                        activity.onBackPressed()
                                        activity.showFloatMessage("Home Assistant", speech)
                                        activity.ttsManager?.speak(speech)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun getAllCaptured(): List<CapturedText> = capturedTexts.toList()

    fun clearCaptured() {
        capturedTexts.clear()
    }

    fun setExcludedApps(apps: Set<String>) {
        excludedApps.clear()
        excludedApps.addAll(apps)
        whitelistApp = null
        saveSettings()
    }

    fun getExcludedApps(): Set<String> = excludedApps.toSet()

    fun setWhitelistApp(packageName: String?) {
        whitelistApp = packageName
        if (packageName != null) {
            excludedApps.clear()
        }
        saveSettings()
    }

    fun getWhitelistApp(): String? = whitelistApp

    fun shouldIgnorePackage(packageName: String): Boolean {
        return if (whitelistApp != null) {
            packageName != whitelistApp
        } else {
            excludedApps.contains(packageName)
        }
    }

    // === Home Assistant & Only Capture 方法 ===

    fun addOnlyCaptureFrame(text: String, packageName: String, viewClass: String = "", boundsLeft: Int = -1, boundsTop: Int = -1, boundsRight: Int = -1, boundsBottom: Int = -1, viewDepth: Int = -1) {
        val frame = CaptureFrame(text, packageName, viewClass, System.currentTimeMillis(), boundsLeft, boundsTop, boundsRight, boundsBottom, viewDepth)
        onlyCaptureFrames.add(frame)
        saveOnlyCaptureFrames()
    }

    fun removeOnlyCaptureFrame(index: Int) {
        if (index >= 0 && index < onlyCaptureFrames.size) {
            onlyCaptureFrames.removeAt(index)
            saveOnlyCaptureFrames()
        }
    }

    fun getOnlyCaptureFrames(): List<CaptureFrame> = onlyCaptureFrames.toList()

    fun setOnlyCapturePrefix(prefix: String) {
        onlyCapturePrefix = prefix
        saveOnlyCaptureSettings()
    }

    fun getOnlyCapturePrefix(): String = onlyCapturePrefix

    fun setOnlyCaptureSuffix(suffix: String) {
        onlyCaptureSuffix = suffix
        saveOnlyCaptureSettings()
    }

    fun getOnlyCaptureSuffix(): String = onlyCaptureSuffix

    fun setOnlyCaptureEnabled(enabled: Boolean) {
        if (prefs == null) {
            Log.w("CapturedTextManager", "init() must be called before setOnlyCaptureEnabled()")
            return
        }
        isOnlyCaptureEnabled = enabled
        prefs?.edit()?.putBoolean("only_capture_enabled", enabled)?.apply()
    }

    fun getOnlyCaptureEnabled(): Boolean = isOnlyCaptureEnabled

    fun setSendToHomeAssistant(enabled: Boolean) {
        sendToHomeAssistant = enabled
        saveOnlyCaptureSettings()
    }

    fun getSendToHomeAssistant(): Boolean = sendToHomeAssistant

    // === 持久化方法 ===

    fun saveSettings() {
        prefs?.edit()?.apply {
            putStringSet("excluded", excludedApps)
            putString("whitelist", whitelistApp)
            apply()
        }
    }

    private fun saveOnlyCaptureFrames() {
        if (prefs == null) {
            Log.w("CapturedTextManager", "init() must be called before saveOnlyCaptureFrames()")
            return
        }
        val data = onlyCaptureFrames.joinToString(";;") { "${it.text}|${it.packageName}|${it.viewClass}|${it.timestamp}|${it.boundsLeft}|${it.boundsTop}|${it.boundsRight}|${it.boundsBottom}|${it.viewDepth}" }
        prefs?.edit()?.putString("only_capture_frames", data)?.apply()
    }

    private fun saveOnlyCaptureSettings() {
        prefs?.edit()?.apply {
            putString("only_capture_prefix", onlyCapturePrefix)
            putString("only_capture_suffix", onlyCaptureSuffix)
            putBoolean("send_to_home_assistant", sendToHomeAssistant)
            apply()
        }
    }

    private fun loadSettings() {
        prefs?.let { p ->
            excludedApps.clear()
            p.getStringSet("excluded", emptySet())?.let { excludedApps.addAll(it) }
            whitelistApp = p.getString("whitelist", null)

            onlyCaptureFrames.clear()
            val framesData = p.getString("only_capture_frames", "") ?: ""
            if (framesData.isNotEmpty()) {
                framesData.split(";;").forEach { item ->
                    val parts = item.split("|")
                    if (parts.size >= 8) {
                        onlyCaptureFrames.add(CaptureFrame(
                            parts[0],
                            parts[1],
                            parts[2],
                            parts[3].toLongOrNull() ?: 0,
                            parts[4].toIntOrNull() ?: -1,
                            parts[5].toIntOrNull() ?: -1,
                            parts[6].toIntOrNull() ?: -1,
                            parts[7].toIntOrNull() ?: -1,
                            if (parts.size >= 9) parts[8].toIntOrNull() ?: -1 else -1
                        ))
                    } else if (parts.size >= 4) {
                        onlyCaptureFrames.add(CaptureFrame(
                            parts[0],
                            parts[1],
                            parts[2],
                            parts[3].toLongOrNull() ?: 0
                        ))
                    }
                }
            }

            onlyCapturePrefix = p.getString("only_capture_prefix", "") ?: ""
            onlyCaptureSuffix = p.getString("only_capture_suffix", "") ?: ""
            isOnlyCaptureEnabled = p.getBoolean("only_capture_enabled", false)
            sendToHomeAssistant = p.getBoolean("send_to_home_assistant", false)
        }
    }
}

data class CaptureFrame(
    val text: String,
    val packageName: String,
    val viewClass: String,
    val timestamp: Long,
    val boundsLeft: Int = -1,
    val boundsTop: Int = -1,
    val boundsRight: Int = -1,
    val boundsBottom: Int = -1,
    val viewDepth: Int = -1
)
