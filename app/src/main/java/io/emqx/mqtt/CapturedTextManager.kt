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

    private var onlyCaptureFrames = ArrayList<CaptureFrame>()
    private var onlyCapturePrefix = ""
    private var onlyCaptureSuffix = ""

    fun init(context: Context) {
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

        // Selective monitoring: only process text if it matches frames in Only Capture Frame
        if (onlyCaptureFrames.isNotEmpty()) {
            val matchesFrame = onlyCaptureFrames.any { frame ->
                // Match by package name
                frame.packageName == packageName &&
                // Match by layer (viewDepth)
                (frame.viewDepth == -1 || frame.viewDepth == viewDepth) &&
                // Match by layer name (viewClass)
                (frame.viewClass.isEmpty() || frame.viewClass == viewClass) &&
                // Match by approximate position (within 10px tolerance)
                (frame.boundsLeft == -1 || Math.abs(frame.boundsLeft - boundsLeft) < 10) &&
                (frame.boundsTop == -1 || Math.abs(frame.boundsTop - boundsTop) < 10) &&
                (frame.boundsRight == -1 || Math.abs(frame.boundsRight - boundsRight) < 10) &&
                (frame.boundsBottom == -1 || Math.abs(frame.boundsBottom - boundsBottom) < 10)
            }
            
            if (!matchesFrame) {
                return // Skip if no matching frame found
            }
            
            // Apply text prefix/suffix restrictions
            val prefix = onlyCapturePrefix
            val suffix = onlyCaptureSuffix
            
            if (prefix.isNotEmpty() && !text.startsWith(prefix)) {
                return // Skip if text doesn't match prefix
            }
            
            if (suffix.isNotEmpty() && !text.endsWith(suffix)) {
                return // Skip if text doesn't match suffix
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

    fun saveSettings() {
        prefs?.edit()?.apply {
            putStringSet("excluded", excludedApps)
            putString("whitelist", whitelistApp)
            apply()
        }
    }

    private fun saveOnlyCaptureFrames() {
        val data = onlyCaptureFrames.joinToString(";;") { "${it.text}|${it.packageName}|${it.viewClass}|${it.timestamp}|${it.boundsLeft}|${it.boundsTop}|${it.boundsRight}|${it.boundsBottom}|${it.viewDepth}" }
        prefs?.edit()?.putString("only_capture_frames", data)?.apply()
    }

    private fun saveOnlyCaptureSettings() {
        prefs?.edit()?.apply {
            putString("only_capture_prefix", onlyCapturePrefix)
            putString("only_capture_suffix", onlyCaptureSuffix)
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
                        // Backward compatibility for old format
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