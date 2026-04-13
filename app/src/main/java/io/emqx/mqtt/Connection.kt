package io.emqx.mqtt

import android.content.Context
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class Connection(
    private val context: Context,
    var host: String,
    var port: Int,
    var clientId: String,
    var username: String,
    var password: String,
    private val protocol: String,
    private val path: String
) {
    enum class Protocol {
        TCP, SSL, WS, WSS
    }

    fun getMqttClient(): MqttAsyncClient {
        val uri = buildUri()
        return MqttAsyncClient(uri, clientId, MemoryPersistence())
    }

    private fun buildUri(): String {
        val actualPath = if (path.isNotEmpty() && !path.startsWith("/")) "/$path" else path
        return when (protocol) {
            "SSL" -> "ssl://$host:$port"
            "WS" -> "ws://$host:$port$actualPath"
            "WSS" -> "wss://$host:$port$actualPath"
            else -> "tcp://$host:$port"
        }
    }

    val mqttConnectOptions: MqttConnectOptions
        get() {
            val options = MqttConnectOptions()
            options.isCleanSession = false
            options.isAutomaticReconnect = true
            options.connectionTimeout = 30
            options.keepAliveInterval = 60
            if (protocol == "SSL" || protocol == "WSS") {
                try {
                    options.socketFactory =
                        SSLUtils.getSingleSocketFactory(context.resources.openRawResource(R.raw.cacert))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (username.isNotEmpty()) {
                options.userName = username
            }
            if (password.isNotEmpty()) {
                options.password = password.toCharArray()
            }
            return options
        }
}
