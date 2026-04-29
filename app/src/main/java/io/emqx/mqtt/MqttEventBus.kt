package io.emqx.mqtt

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * ⭐ 规则9修复：MQTT事件总线（解耦UI操作）
 * 
 * 作用：
 * 1. MQTT回调只负责接收消息和记录日志到logcat
 * 2. 通过LiveData发送事件到UI层
 * 3. Activity/Fragment监听LiveData更新UI，实现完全解耦
 */
object MqttEventBus {
    
    // ========== 连接状态事件 ==========
    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> = _connectionStatus
    
    // ========== 消息到达事件 ==========
    data class MessageEvent(val topic: String, val payload: String, val timestamp: Long = System.currentTimeMillis())
    
    private val _messageArrived = MutableLiveData<MessageEvent>()
    val messageArrived: LiveData<MessageEvent> = _messageArrived
    
    // ========== 连接丢失事件 ==========
    private val _connectionLost = MutableLiveData<Throwable?>()
    val connectionLost: LiveData<Throwable?> = _connectionLost
    
    // ========== 发布事件的方法（由MqttService/MainActivity调用）==========
    
    /**
     * 发布连接状态变化事件
     */
    fun publishConnectionStatus(connected: Boolean) {
        _connectionStatus.postValue(connected)
    }
    
    /**
     * 发布消息到达事件
     */
    fun publishMessageArrived(topic: String, payload: String) {
        _messageArrived.postValue(MessageEvent(topic, payload))
    }
    
    /**
     * 发布连接丢失事件
     */
    fun publishConnectionLost(cause: Throwable?) {
        _connectionLost.postValue(cause)
    }
}
