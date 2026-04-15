package io.emqx.mqtt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object CapturedTextManager {
    private val listeners = mutableListOf<(String, String) -> Unit>()
    private val capturedTexts = ArrayList<CapturedText>()
    var isEnabled = false

    private var excludedApps = hashSetOf<String>()
    private var whitelistApp: String? = null
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("capture_settings", Context.MODE_PRIVATE)
        loadSettings()
    }

    fun addListener(listener: (String, String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String, String) -> Unit) {
        listeners.remove(listener)
    }

    fun onTextCaptured(text: String, packageName: String) {
        if (!isEnabled) return

        if (whitelistApp != null) {
            if (packageName != whitelistApp) return
        } else if (excludedApps.contains(packageName)) {
            return
        }

        val captured = CapturedText(text, packageName, System.currentTimeMillis())
        capturedTexts.add(0, captured)
        listeners.forEach { it(text, packageName) }
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
