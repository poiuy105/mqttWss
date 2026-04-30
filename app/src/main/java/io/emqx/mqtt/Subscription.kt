package io.emqx.mqtt

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

/**
 * 消息记录数据类
 */
data class MessageRecord(
    @SerializedName("message")
    val message: String = "",
    @SerializedName("timestamp")
    val timestamp: Long = 0L
)

/**
 * 订阅主题数据类
 * ⭐ 新增：支持最近5条消息历史记录和时间戳
 * ⭐ 修复：添加无参构造函数支持Gson反序列化
 */
class Subscription() {
    @SerializedName("topic")
    var topic: String = ""
    
    @SerializedName("qos")
    var qos: Int = 0
    
    @SerializedName("lastMessage")
    var lastMessage: String = ""
    
    @SerializedName("messageHistory")
    var messageHistory: MutableList<MessageRecord> = mutableListOf()
    
    constructor(topic: String, qos: Int, lastMessage: String = "") : this() {
        this.topic = topic
        this.qos = qos
        this.lastMessage = lastMessage
    }
    
    companion object {
        const val MAX_HISTORY_SIZE = 5
    }
    
    /**
     * 添加消息到历史记录（自动维护最多5条）
     */
    fun addMessageToHistory(message: String) {
        val timestamp = System.currentTimeMillis()
        messageHistory.add(0, MessageRecord(message, timestamp))
        
        // 保持最多5条记录
        if (messageHistory.size > MAX_HISTORY_SIZE) {
            messageHistory.removeAt(messageHistory.lastIndex)
        }
        
        // 更新lastMessage为最新消息
        lastMessage = message
    }
    
    /**
     * 获取格式化的历史记录文本（包含时间戳）
     */
    fun getFormattedHistory(): String {
        if (messageHistory.isEmpty()) {
            return "No messages yet"
        }
        
        val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        return messageHistory.joinToString("\n") { record ->
            "[${sdf.format(Date(record.timestamp))}] ${record.message}"
        }
    }
}