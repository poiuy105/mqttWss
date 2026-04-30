package io.emqx.mqtt

import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ⭐ 规则9修复：MQTT事件总线（解耦UI操作）
 * 
 * 作用：
 * 1. MQTT回调只负责接收消息和记录日志到logcat
 * 2. 通过LiveData发送事件到UI层
 * 3. Activity/Fragment监听LiveData更新UI，实现完全解耦
 */
object MqttEventBus {
    
    // ========== 广播Action常量 ==========
    const val ACTION_MQTT_MESSAGE_ARRIVED = "io.emqx.mqtt.MQTT_MESSAGE_ARRIVED"
    const val EXTRA_TOPIC = "extra_topic"
    const val EXTRA_PAYLOAD = "extra_payload"
    
    // ========== 连接状态事件 ==========
    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> = _connectionStatus
    
    // ========== 消息到达事件 ==========
    data class MessageEvent(val topic: String, val payload: String, val timestamp: Long = System.currentTimeMillis())
    
    // ⭐ 修复：使用MutableLiveData代替SingleLiveEvent，确保Fragment能持续接收MQTT消息
    // LiveData的粘性特性保证新观察者会收到最新值，适合持续的消息流场景
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
     * ⭐ 修复：发送广播，确保后台时也能触发TTS和弹窗
     */
    fun sendBroadcast(context: android.content.Context, topic: String, payload: String) {
        val intent = Intent(ACTION_MQTT_MESSAGE_ARRIVED).apply {
            putExtra(EXTRA_TOPIC, topic)
            putExtra(EXTRA_PAYLOAD, payload)
        }
        context.sendBroadcast(intent)
    }
    
    /**
     * 发布连接丢失事件
     */
    fun publishConnectionLost(cause: Throwable?) {
        _connectionLost.postValue(cause)
    }
}

/**
 * ⭐ 修复Bug 3：单次事件包装器，防止LiveData粘性事件导致重复触发
 * 
 * 问题：LiveData是粘性事件，新观察者会立即收到最后一个值
 * 解决：每个事件只被消费一次，从Home返回App时不会重复触发
 */
class SingleLiveEvent<T> : MutableLiveData<T>() {
    private val pending = AtomicBoolean(false)
    
    @androidx.annotation.MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        if (hasActiveObservers()) {
            android.util.Log.w("SingleLiveEvent", "Multiple observers registered! Only the first one will be notified.")
        }
        
        super.observe(owner) { t ->
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t)
            }
        }
    }
    
    @androidx.annotation.MainThread
    override fun setValue(t: T?) {
        pending.set(true)
        super.setValue(t)
    }
    
    /**
     * Used for cases where T is Void, to make calls cleaner.
     */
    @androidx.annotation.MainThread
    fun call() {
        value = null
    }
}
