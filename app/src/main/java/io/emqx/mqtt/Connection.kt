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
    private val path: String,
    private val allowUntrusted: Boolean = false
) {
    enum class Protocol {
        TCP, SSL, WS, WSS
    }

    fun getMqttClient(): MqttAsyncClient {
        val uri = buildUri()
        return MqttAsyncClient(uri, clientId, MemoryPersistence())
    }

    fun buildUri(): String {
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
            options.connectionTimeout = 10  // 缩短连接超时到10秒，快速失败
            options.keepAliveInterval = 30  // 缩短心跳间隔到30秒，更频繁保活
            
            // 启用 MQTT 3.1.1 的增强特性
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1)
            
            // 配置 Last Will
            HomeAssistantIntegration.configureLastWill(options)
            
            // ⭐ 修复：SSL和WSS都需要配置socketFactory
            if (protocol == "SSL" || protocol == "WSS") {
                try {
                    if (allowUntrusted) {
                        options.socketFactory = SSLUtils.getInsecureSocketFactory()
                    } else {
                        options.socketFactory =
                            SSLUtils.getSingleSocketFactory(context.resources.openRawResource(R.raw.cacert))
                    }
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
