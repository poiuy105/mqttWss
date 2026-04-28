package io.emqx.mqtt

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "mqtt_config"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_PATH = "path"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_CLEAN_SESSION = "clean_session"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val KEY_CONNECTION_TIMEOUT = "connection_timeout"
        private const val KEY_LAST_CONNECTED = "last_connected"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_PERSISTENT_NOTIFICATION = "persistent_notification"
        private const val KEY_SUBSCRIPTIONS = "subscriptions"
        private const val KEY_PUBLISH_TOPIC = "publish_topic"
        private const val KEY_PUBLISH_PAYLOAD = "publish_payload"
        private const val KEY_PUBLISH_QOS = "publish_qos"
        private const val KEY_PUBLISH_RETAINED = "publish_retained"
        private const val KEY_SUBSCRIPTION_HISTORY = "subscription_history"
        private const val KEY_PUBLISH_HISTORY = "publish_history"
        private const val KEY_ALLOW_UNTRUSTED = "allow_untrusted"
        private const val KEY_HA_ADDRESS = "ha_address"
        private const val KEY_HA_TOKEN = "ha_token"
        private const val KEY_HA_LANGUAGE = "ha_language"
        private const val KEY_HA_HTTPS = "ha_https"
        private const val KEY_HA_RESPONSE_DELAY = "ha_response_delay"
        private const val KEY_HA_CLICK_BACK = "ha_click_back"
        private const val KEY_HA_CLICK_COUNT = "ha_click_count"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_FLOAT_WINDOW_ENABLED = "float_window_enabled"
        private const val KEY_VOICE_CAPTURE_ENABLED = "voice_capture_enabled"
        private const val KEY_SHOW_DEBUG_LOG = "show_debug_log"
        // ========== 云端TTS配置 ==========
        private const val KEY_CLOUD_TTS_API_INDEX = "cloud_tts_api_index"
        private const val KEY_CLOUD_TTS_VOICE = "cloud_tts_voice"
        private const val KEY_CLOUD_TTS_SPEED = "cloud_tts_speed"
        private const val KEY_CLOUD_TTS_PITCH = "cloud_tts_pitch"
        private const val KEY_CLOUD_TTS_VOLUME = "cloud_tts_volume"
        private const val KEY_CLOUD_TTS_OIOWEB_TYPE = "cloud_tts_oioweb_type"
        private const val KEY_CLOUD_TTS_OIOWEB_SPEED = "cloud_tts_oioweb_speed"
        private const val KEY_CLOUD_TTS_DUCKARMY_SPD = "cloud_tts_duckarmy_spd"
        private const val KEY_CLOUD_TTS_DUCKARMY_PIT = "cloud_tts_duckarmy_pit"
        private const val KEY_CLOUD_TTS_DUCKARMY_VOL = "cloud_tts_duckarmy_vol"

        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }

    var host: String
        get() = prefs.getString(KEY_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, 1883)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var path: String
        get() = prefs.getString(KEY_PATH, "/mqtt") ?: "/mqtt"
        set(value) = prefs.edit().putString(KEY_PATH, value).apply()

    var clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var protocol: String
        get() = prefs.getString(KEY_PROTOCOL, "TCP") ?: "TCP"
        set(value) = prefs.edit().putString(KEY_PROTOCOL, value).apply()

    var cleanSession: Boolean
        get() = prefs.getBoolean(KEY_CLEAN_SESSION, false)
        set(value) = prefs.edit().putBoolean(KEY_CLEAN_SESSION, value).apply()

    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()

    var autoReconnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECONNECT, value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var persistentNotification: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_NOTIFICATION, value).apply()

    var subscriptions: String
        get() = prefs.getString(KEY_SUBSCRIPTIONS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUBSCRIPTIONS, value).apply()

    var publishTopic: String
        get() = prefs.getString(KEY_PUBLISH_TOPIC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PUBLISH_TOPIC, value).apply()

    var publishPayload: String
        get() = prefs.getString(KEY_PUBLISH_PAYLOAD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PUBLISH_PAYLOAD, value).apply()

    var publishQos: Int
        get() = prefs.getInt(KEY_PUBLISH_QOS, 0)
        set(value) = prefs.edit().putInt(KEY_PUBLISH_QOS, value).apply()

    var publishRetained: Boolean
        get() = prefs.getBoolean(KEY_PUBLISH_RETAINED, false)
        set(value) = prefs.edit().putBoolean(KEY_PUBLISH_RETAINED, value).apply()

    var subscriptionHistory: String
        get() = prefs.getString(KEY_SUBSCRIPTION_HISTORY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUBSCRIPTION_HISTORY, value).apply()

    var publishHistory: String
        get() = prefs.getString(KEY_PUBLISH_HISTORY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PUBLISH_HISTORY, value).apply()

    var keepAlive: Int
        get() = prefs.getInt(KEY_KEEP_ALIVE, 60)
        set(value) = prefs.edit().putInt(KEY_KEEP_ALIVE, value).apply()

    var connectionTimeout: Int
        get() = prefs.getInt(KEY_CONNECTION_TIMEOUT, 30)
        set(value) = prefs.edit().putInt(KEY_CONNECTION_TIMEOUT, value).apply()

    var lastConnected: Long
        get() = prefs.getLong(KEY_LAST_CONNECTED, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_CONNECTED, value).apply()

    var allowUntrusted: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_UNTRUSTED, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_UNTRUSTED, value).apply()

    var haAddress: String
        get() = prefs.getString(KEY_HA_ADDRESS, "homeassistant.local") ?: "homeassistant.local"
        set(value) = prefs.edit().putString(KEY_HA_ADDRESS, value).apply()

    var haToken: String
        get() = prefs.getString(KEY_HA_TOKEN, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI0NWMyZTBiODlmMGI0MTYwYWVhZWU2YTYyYWZjNjNkNiIsImlhdCI6MTc3NjU1NzY3NSwiZXhwIjoyMDkxOTE3Njc1fQ.0xzF8z1cz6xi4ajpS2zgjoxMDu4EfqoNP0mrIvYVSGE") ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI0NWMyZTBiODlmMGI0MTYwYWVhZWU2YTYyYWZjNjNkNiIsImlhdCI6MTc3NjU1NzY3NSwiZXhwIjoyMDkxOTE3Njc1fQ.0xzF8z1cz6xi4ajpS2zgjoxMDu4EfqoNP0mrIvYVSGE"
        set(value) = prefs.edit().putString(KEY_HA_TOKEN, value).apply()

    var haLanguage: String
        get() = prefs.getString(KEY_HA_LANGUAGE, "zh") ?: "zh"
        set(value) = prefs.edit().putString(KEY_HA_LANGUAGE, value).apply()

    var haHttps: Boolean
        get() = prefs.getBoolean(KEY_HA_HTTPS, true)
        set(value) = prefs.edit().putBoolean(KEY_HA_HTTPS, value).apply()

    var haResponseDelay: Int
        get() = prefs.getInt(KEY_HA_RESPONSE_DELAY, 50)
        set(value) = prefs.edit().putInt(KEY_HA_RESPONSE_DELAY, value).apply()

    var haClickBackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HA_CLICK_BACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HA_CLICK_BACK, value).apply()

    var haClickCount: Int
        get() = prefs.getInt(KEY_HA_CLICK_COUNT, 3)
        set(value) = prefs.edit().putInt(KEY_HA_CLICK_COUNT, value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    var floatWindowEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOAT_WINDOW_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOAT_WINDOW_ENABLED, value).apply()

    var voiceCaptureEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_CAPTURE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_CAPTURE_ENABLED, value).apply()

    var showDebugLog: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DEBUG_LOG, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DEBUG_LOG, value).apply()

    // ========== 云端TTS配置 ==========
    var cloudTtsApiIndex: Int
        get() = prefs.getInt(KEY_CLOUD_TTS_API_INDEX, CloudTTSPlayer.API_EDGETTS)
        set(value) = prefs.edit().putInt(KEY_CLOUD_TTS_API_INDEX, value).apply()

    var cloudTtsVoice: String
        get() = prefs.getString(KEY_CLOUD_TTS_VOICE, "zh-CN-XiaoxiaoNeural") ?: "zh-CN-XiaoxiaoNeural"
        set(value) = prefs.edit().putString(KEY_CLOUD_TTS_VOICE, value).apply()

    var cloudTtsSpeed: Float
        get() = prefs.getFloat(KEY_CLOUD_TTS_SPEED, 1.0f)  // ⭐ 默认1.0x
        set(value) = prefs.edit().putFloat(KEY_CLOUD_TTS_SPEED, value).apply()

    var cloudTtsPitch: Float
        get() = prefs.getFloat(KEY_CLOUD_TTS_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_CLOUD_TTS_PITCH, value).apply()

    var cloudTtsVolume: Float
        get() = prefs.getFloat(KEY_CLOUD_TTS_VOLUME, 1.0f)  // ⭐ 默认1.0
        set(value) = prefs.edit().putFloat(KEY_CLOUD_TTS_VOLUME, value).apply()

    var cloudTtsOiowebType: String
        get() = prefs.getString(KEY_CLOUD_TTS_OIOWEB_TYPE, "1") ?: "1"
        set(value) = prefs.edit().putString(KEY_CLOUD_TTS_OIOWEB_TYPE, value).apply()

    var cloudTtsOiowebSpeed: Int
        get() = prefs.getInt(KEY_CLOUD_TTS_OIOWEB_SPEED, 4)
        set(value) = prefs.edit().putInt(KEY_CLOUD_TTS_OIOWEB_SPEED, value).apply()

    var cloudTtsDuckarmySpd: Int
        get() = prefs.getInt(KEY_CLOUD_TTS_DUCKARMY_SPD, -1)
        set(value) = prefs.edit().putInt(KEY_CLOUD_TTS_DUCKARMY_SPD, value).apply()

    var cloudTtsDuckarmyPit: Int
        get() = prefs.getInt(KEY_CLOUD_TTS_DUCKARMY_PIT, 0)
        set(value) = prefs.edit().putInt(KEY_CLOUD_TTS_DUCKARMY_PIT, value).apply()

    var cloudTtsDuckarmyVol: Int
        get() = prefs.getInt(KEY_CLOUD_TTS_DUCKARMY_VOL, 2)
        set(value) = prefs.edit().putInt(KEY_CLOUD_TTS_DUCKARMY_VOL, value).apply()

    fun saveConnectionConfig(
        host: String,
        port: Int,
        path: String,
        clientId: String,
        username: String,
        password: String,
        protocol: String,
        allowUntrusted: Boolean,
        haAddress: String,
        haToken: String,
        haLanguage: String,
        haHttps: Boolean,
        haResponseDelay: Int,
        haClickBackEnabled: Boolean,
        haClickCount: Int
    ) {
        prefs.edit().apply {
            putString(KEY_HOST, host)
            putInt(KEY_PORT, port)
            putString(KEY_PATH, path)
            putString(KEY_CLIENT_ID, clientId)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putString(KEY_PROTOCOL, protocol)
            putBoolean(KEY_ALLOW_UNTRUSTED, allowUntrusted)
            putString(KEY_HA_ADDRESS, haAddress)
            putString(KEY_HA_TOKEN, haToken)
            putString(KEY_HA_LANGUAGE, haLanguage)
            putBoolean(KEY_HA_HTTPS, haHttps)
            putInt(KEY_HA_RESPONSE_DELAY, haResponseDelay)
            putBoolean(KEY_HA_CLICK_BACK, haClickBackEnabled)
            putInt(KEY_HA_CLICK_COUNT, haClickCount)
            putLong(KEY_LAST_CONNECTED, System.currentTimeMillis())
            apply()
        }
    }

    fun hasSavedConfig(): Boolean {
        return host.isNotEmpty() && clientId.isNotEmpty()
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }
}
