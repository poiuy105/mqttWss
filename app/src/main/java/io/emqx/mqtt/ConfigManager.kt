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

    var keepAlive: Int
        get() = prefs.getInt(KEY_KEEP_ALIVE, 60)
        set(value) = prefs.edit().putInt(KEY_KEEP_ALIVE, value).apply()

    var connectionTimeout: Int
        get() = prefs.getInt(KEY_CONNECTION_TIMEOUT, 30)
        set(value) = prefs.edit().putInt(KEY_CONNECTION_TIMEOUT, value).apply()

    var lastConnected: Long
        get() = prefs.getLong(KEY_LAST_CONNECTED, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_CONNECTED, value).apply()

    fun saveConnectionConfig(
        host: String,
        port: Int,
        path: String,
        clientId: String,
        username: String,
        password: String,
        protocol: String
    ) {
        prefs.edit().apply {
            putString(KEY_HOST, host)
            putInt(KEY_PORT, port)
            putString(KEY_PATH, path)
            putString(KEY_CLIENT_ID, clientId)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putString(KEY_PROTOCOL, protocol)
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
