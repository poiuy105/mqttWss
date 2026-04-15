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

    fun saveSettings() {
        prefs?.edit()?.apply {
            putStringSet("excluded", excludedApps)
            putString("whitelist", whitelistApp)
            apply()
        }
    }

    private fun loadSettings() {
        prefs?.let { p ->
            excludedApps.clear()
            p.getStringSet("excluded", emptySet())?.let { excludedApps.addAll(it) }
            whitelistApp = p.getString("whitelist", null)
        }
    }
}
