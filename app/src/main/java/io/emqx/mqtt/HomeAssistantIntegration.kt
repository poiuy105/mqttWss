package io.emqx.mqtt

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.google.gson.Gson
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.Timer
import java.util.TimerTask

/**
 * Home Assistant 集成管理器
 * 负责设备自动发现、电池电量上报、Last Will 配置
 */
object HomeAssistantIntegration {
    private const val TAG = "HA_Integration"
    
    // Home Assistant 自动发现主题前缀
    private const val HA_DISCOVERY_PREFIX = "homeassistant"
    
    // 设备唯一标识（基于 Android ID 或固定值）
    private var deviceId: String = "byd2ha_bridge"
    
    // 定时器用于定期上报电池
    private var batteryTimer: Timer? = null
    private val BATTERY_REPORT_INTERVAL = 60000L // 60秒上报一次
    
    /**
     * 初始化设备ID
     */
    fun init(context: Context) {
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            if (!androidId.isNullOrEmpty()) {
                deviceId = "byd2ha_${androidId.take(8)}"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Android ID, using default", e)
        }
    }
    
    /**
     * 生成 Home Assistant 自动发现配置
     */
    fun generateDiscoveryConfig(context: Context): Map<String, Any> {
        val configManager = ConfigManager.getInstance(context)
        
        return mapOf(
            "device" to mapOf(
                "identifiers" to listOf(deviceId),
                "name" to "BYD2HA Bridge",
                "manufacturer" to "BYD",
                "model" to "MQTT Bridge",
                "sw_version" to "1.0"
            ),
            "battery" to mapOf(
                "name" to "Battery Level",
                "state_topic" to "byd2ha/$deviceId/sensor/battery/state",
                "unit_of_measurement" to "%",
                "device_class" to "battery",
                "unique_id" to "${deviceId}_battery",
                "value_template" to "{{ value_json.level }}",
                "json_attributes_topic" to "byd2ha/$deviceId/sensor/battery/attributes"
            ),
            "availability" to mapOf(
                "topic" to "byd2ha/$deviceId/status",
                "payload_available" to "online",
                "payload_not_available" to "offline"
            )
        )
    }
    
    /**
     * 发布 Home Assistant 自动发现配置
     */
    fun publishDiscoveryConfig(context: Context, mqttClient: org.eclipse.paho.client.mqttv3.MqttAsyncClient?) {
        if (mqttClient == null || !mqttClient.isConnected) {
            Log.w(TAG, "MQTT not connected, skip discovery")
            return
        }
        
        try {
            val config = generateDiscoveryConfig(context)
            val gson = Gson()
            
            // 发布电池传感器配置
            val batteryTopic = "$HA_DISCOVERY_PREFIX/sensor/$deviceId/battery/config"
            val batteryConfig = mapOf(
                "name" to "Battery Level",
                "state_topic" to "byd2ha/$deviceId/sensor/battery/state",
                "unit_of_measurement" to "%",
                "device_class" to "battery",
                "unique_id" to "${deviceId}_battery",
                "json_attributes_topic" to "byd2ha/$deviceId/sensor/battery/attributes",
                "device" to mapOf(
                    "identifiers" to listOf(deviceId),
                    "name" to "BYD2HA Bridge",
                    "manufacturer" to "BYD",
                    "model" to "MQTT Bridge",
                    "sw_version" to "1.0"
                ),
                "availability" to mapOf(
                    "topic" to "byd2ha/$deviceId/status",
                    "payload_available" to "online",
                    "payload_not_available" to "offline"
                )
            )
            
            val message = MqttMessage(gson.toJson(batteryConfig).toByteArray(Charsets.UTF_8))
            message.qos = 1
            message.isRetained = true
            
            mqttClient.publish(batteryTopic, message)
            Log.d(TAG, "Published discovery config to $batteryTopic")
            
            // 发布在线状态
            val statusTopic = "byd2ha/$deviceId/status"
            val statusMessage = MqttMessage("online".toByteArray(Charsets.UTF_8))
            statusMessage.qos = 1
            statusMessage.isRetained = true
            mqttClient.publish(statusTopic, statusMessage)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish discovery config", e)
        }
    }
    
    /**
     * 获取当前电池信息
     */
    fun getBatteryInfo(context: Context): Map<String, Any>? {
        return try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            
            if (level == -1 || scale == -1) {
                return null
            }
            
            val batteryPct = ((level * 100) / scale.toFloat()).toInt()
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                           status == BatteryManager.BATTERY_STATUS_FULL
            
            mapOf<String, Any>(
                "level" to batteryPct,
                "status" to when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                    else -> "unknown"
                },
                "health" to when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                    BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                    else -> "unknown"
                },
                "temperature" to (if (temperature > 0) temperature / 10.0 else 0.0),
                "voltage" to (if (voltage > 0) voltage else 0),
                "is_charging" to isCharging,
                "timestamp" to System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery info", e)
            null
        }
    }
    
    /**
     * 发布电池电量到 MQTT
     */
    fun publishBatteryLevel(context: Context, mqttClient: org.eclipse.paho.client.mqttv3.MqttAsyncClient?, mainActivity: MainActivity?) {
        if (mqttClient == null || !mqttClient.isConnected) {
            Log.w(TAG, "MQTT not connected, skip battery report")
            return
        }
        
        val batteryInfo = getBatteryInfo(context) ?: return
        
        try {
            val gson = Gson()
            
            // 发布电池状态 - 直接发送数字值（不使用JSON）
            val stateTopic = "byd2ha/$deviceId/sensor/battery/state"
            val levelValue = batteryInfo["level"].toString()
            val stateMessage = MqttMessage(levelValue.toByteArray(Charsets.UTF_8))
            stateMessage.qos = 1
            stateMessage.isRetained = false
            mqttClient.publish(stateTopic, stateMessage)
            
            // 发布电池属性
            val attrsTopic = "byd2ha/$deviceId/sensor/battery/attributes"
            val attrsMessage = MqttMessage(gson.toJson(batteryInfo).toByteArray(Charsets.UTF_8))
            attrsMessage.qos = 1
            attrsMessage.isRetained = false
            mqttClient.publish(attrsTopic, attrsMessage)
            
            // 记录到 publish 历史
            mainActivity?.let { activity ->
                val level = batteryInfo["level"]
                val status = batteryInfo["status"]
                val logMessage = "[Auto] Battery: ${level}% (${status})"
                activity.appendLog(logMessage)
                
                // 添加到 publish 历史记录
                val publish = Publish(
                    topic = stateTopic,
                    payload = levelValue,
                    qos = 1,
                    isRetained = false
                )
                activity.addPublishHistory(publish)
            }
            
            Log.d(TAG, "Published battery level: $levelValue%")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish battery level", e)
        }
    }
    
    /**
     * 启动定期电池上报
     */
    fun startBatteryReporting(context: Context, mqttClient: org.eclipse.paho.client.mqttv3.MqttAsyncClient?, mainActivity: MainActivity?) {
        stopBatteryReporting()
        
        batteryTimer = Timer("BatteryReporter")
        batteryTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                publishBatteryLevel(context, mqttClient, mainActivity)
            }
        }, 0, BATTERY_REPORT_INTERVAL)
        
        Log.d(TAG, "Started battery reporting (interval: ${BATTERY_REPORT_INTERVAL}ms)")
    }
    
    /**
     * 停止定期电池上报
     */
    fun stopBatteryReporting() {
        batteryTimer?.cancel()
        batteryTimer = null
        Log.d(TAG, "Stopped battery reporting")
    }
    
    /**
     * 生成 Last Will 配置
     */
    fun configureLastWill(options: org.eclipse.paho.client.mqttv3.MqttConnectOptions) {
        val willTopic = "byd2ha/$deviceId/status"
        val willMessage = "offline"
        
        options.setWill(willTopic, willMessage.toByteArray(Charsets.UTF_8), 1, true)
        Log.d(TAG, "Configured Last Will: topic=$willTopic, message=$willMessage")
    }
}
