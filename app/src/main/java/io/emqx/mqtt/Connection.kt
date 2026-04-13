package io.emqx.mqtt

import android.content.Context
import org.eclipse.paho.client.mqttv3.IMqttClient
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

class Connection(
    private val context: Context,
    var host: String,
    var port: Int,
    var clientId: String,
    var username: String,
    var password: String,
    private val tls: Boolean
) {
    fun getMqttClient(): IMqttClient {
        val uri: String = if (tls) {
            "ssl://$host:$port"
        } else {
            "tcp://$host:$port"
        }
        return MqttAsyncClient(uri, clientId, context)
    }

    val mqttConnectOptions: MqttConnectOptions
        get() {
            val options = MqttConnectOptions()
            options.isCleanSession = false
            options.isAutomaticReconnect = true
            options.connectionTimeout = 30
            options.keepAliveInterval = 60
            if (tls) {
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
