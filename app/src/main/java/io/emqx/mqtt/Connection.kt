package io.emqx.mqtt

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

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
                        // ⭐ allowUntrusted=true：允许自签名证书（不验证证书链）
                        Log.d("Connection", "Using insecure socket factory (allowUntrusted=true)")
                        options.socketFactory = SSLUtils.getInsecureSocketFactory()
                    } else {
                        // ⭐ allowUntrusted=false：使用系统默认CA证书库（信任标准CA）
                        Log.d("Connection", "Using system default CA certificates (allowUntrusted=false)")
                        
                        val trustManagerFactory = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm()
                        )
                        trustManagerFactory.init(null as KeyStore?)  // null表示使用系统默认CA证书库
                        
                        val sslContext = SSLContext.getInstance("TLSv1.2")
                        sslContext.init(null, trustManagerFactory.trustManagers, null)
                        options.socketFactory = sslContext.socketFactory
                    }
                } catch (e: Exception) {
                    Log.e("Connection", "Failed to create SSL socket factory: ${e.message}", e)
                    e.printStackTrace()
                    
                    // ⭐ 降级处理：如果创建SSL失败，且allowUntrusted=true，尝试使用不安全模式
                    if (allowUntrusted) {
                        try {
                            Log.w("Connection", "Fallback to insecure socket factory")
                            options.socketFactory = SSLUtils.getInsecureSocketFactory()
                        } catch (e2: Exception) {
                            Log.e("Connection", "Fallback also failed: ${e2.message}", e2)
                        }
                    }
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
