package io.emqx.mqtt

import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import java.util.ArrayList

class SubscriptionFragment : BaseFragment() {
    private var mAdapter: SubscriptionRecyclerViewAdapter? = null
    private val mSubscriptionList: ArrayList<Subscription> = ArrayList()
    private lateinit var mConfigManager: ConfigManager

    override val layoutResId: Int
        get() = R.layout.fragment_subscription_list

    private fun appendLog(message: String) {
        (fragmentActivity as? MainActivity)?.let { main ->
            main.appendLog("[Subscribe] $message")
        }
    }

    override fun setUpView(view: View) {
        mConfigManager = ConfigManager.getInstance(fragmentActivity!!)

        val recyclerView = view.findViewById<RecyclerView>(R.id.subscription_list)
        mAdapter = SubscriptionRecyclerViewAdapter(
            mSubscriptionList,
            onDeleteClick = { subscription -> deleteSubscription(subscription) },
            onSpeakClick = { subscription -> speakSubscription(subscription) }
        )
        recyclerView.adapter = mAdapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                fragmentActivity,
                DividerItemDecoration.VERTICAL
            )
        )

        view.findViewById<Button>(R.id.btn_add_subscription).setOnClickListener {
            showAddSubscriptionDialog()
        }

        loadSubscriptions()
        
        // ⭐ 规则9修复：监听MQTT消息事件并更新订阅列表
        observeMqttEvents()
    }

    private fun showAddSubscriptionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_subscription, null)
        val topicInput = dialogView.findViewById<EditText>(R.id.dialog_topic)
        val qosGroup = dialogView.findViewById<RadioGroup>(R.id.dialog_qos)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Subscription")
            .setView(dialogView)
            .setPositiveButton("Subscribe") { _, _ ->
                val topic = topicInput.text.toString()
                if (topic.isEmpty()) {
                    Toast.makeText(fragmentActivity, "Please enter topic", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val qos = when (qosGroup.checkedRadioButtonId) {
                    R.id.dialog_qos0 -> 0
                    R.id.dialog_qos1 -> 1
                    R.id.dialog_qos2 -> 2
                    else -> 0
                }
                subscribeToTopic(topic, qos)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun subscribeToTopic(topic: String, qos: Int) {
        val subscription = Subscription(topic, qos)
        appendLog("Subscribing to: $topic with QoS $qos")
        (fragmentActivity as MainActivity).subscribe(
            subscription,
            object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    appendLog("Subscribe SUCCESS: $topic")
                    activity?.runOnUiThread {
                        mSubscriptionList.add(0, subscription)
                        mAdapter?.notifyItemInserted(0)
                        saveSubscriptions()
                        Toast.makeText(fragmentActivity, "Subscribed: $topic", Toast.LENGTH_SHORT).show()
                        (fragmentActivity as? MainActivity)?.speakText("已订阅: $topic")
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    appendLog("Subscribe FAILED: $exception")
                    activity?.runOnUiThread {
                        Toast.makeText(fragmentActivity, "Failed to subscribe: $exception", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    private fun deleteSubscription(subscription: Subscription) {
        val index = mSubscriptionList.indexOf(subscription)
        if (index != -1) {
            mSubscriptionList.removeAt(index)
            mAdapter?.notifyItemRemoved(index)
            saveSubscriptions()
            appendLog("Deleted subscription: ${subscription.topic}")
            Toast.makeText(fragmentActivity, "Deleted: ${subscription.topic}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speakSubscription(subscription: Subscription) {
        val message = if (subscription.lastMessage.isNotEmpty()) {
            "Topic: ${subscription.topic}. Last message: ${subscription.lastMessage}"
        } else {
            "Topic: ${subscription.topic}. No messages yet."
        }
        (fragmentActivity as? MainActivity)?.speakText(message)
        appendLog("Speaking: $message")
    }

    private fun saveSubscriptions() {
        // ⭐ 修复：使用Gson序列化包含消息历史的完整Subscription对象
        val gson = Gson()
        val json = gson.toJson(mSubscriptionList)
        mConfigManager.subscriptionHistory = json
        Log.d("SubscriptionFragment", "Saved ${mSubscriptionList.size} subscriptions with history")
    }

    private fun loadSubscriptions() {
        val saved = mConfigManager.subscriptionHistory
        if (saved.isNotEmpty()) {
            try {
                // ⭐ 修复：使用Gson反序列化包含消息历史的完整Subscription对象
                val gson = Gson()
                val type = object : TypeToken<ArrayList<Subscription>>() {}.type
                val loadedList: ArrayList<Subscription> = gson.fromJson(saved, type)
                
                mSubscriptionList.clear()
                mSubscriptionList.addAll(loadedList)
                mAdapter?.notifyDataSetChanged()
                
                Log.d("SubscriptionFragment", "Loaded ${mSubscriptionList.size} subscriptions with history")
            } catch (e: Exception) {
                Log.e("SubscriptionFragment", "Failed to load subscriptions: ${e.message}", e)
                // 降级方案：尝试旧格式解析
                loadSubscriptionsLegacy(saved)
            }
        }
    }
    
    /**
     * ⭐ 降级方案：兼容旧版本的简单格式（topic,qos,lastMessage）
     */
    private fun loadSubscriptionsLegacy(saved: String) {
        mSubscriptionList.clear()
        saved.split(";").forEach { item ->
            val parts = item.split(",")
            if (parts.size >= 2) {
                val topic = parts[0]
                val qos = parts[1].toIntOrNull() ?: 0
                val lastMessage = if (parts.size >= 3) parts[2] else ""
                mSubscriptionList.add(Subscription(topic, qos, lastMessage))
            }
        }
        mAdapter?.notifyDataSetChanged()
        Log.d("SubscriptionFragment", "Loaded ${mSubscriptionList.size} subscriptions (legacy format)")
    }
    
    /**
     * ⭐ 修复：每次恢复时重新注册MQTT事件观察者
     */
    override fun onResume() {
        super.onResume()
        
        // ⭐ 修复：每次恢复时重新注册MQTT事件观察者
        // 因为viewLifecycleOwner可能在Fragment重建后发生变化
        observeMqttEvents()
        
        Log.d("SubscriptionFragment", "onResume: MQTT event observer registered")
    }

    fun updateSubscriptionMessage(topic: String, message: String) {
        activity?.runOnUiThread {
            // 防御性检查: recreate()/方向切换后Fragment可能尚未完成布局初始化
            if (mAdapter == null) {
                Log.w("SubscriptionFragment", "updateSubscriptionMessage called but mAdapter is null, skipping")
                return@runOnUiThread
            }
            
            // ⭐ 修复：改进主题匹配逻辑，支持trim和忽略大小写
            val normalizedTopic = topic.trim()
            val subscription = mSubscriptionList.find { 
                it.topic.trim().equals(normalizedTopic, ignoreCase = true) 
            }
            
            if (subscription != null) {
                // ⭐ 新增：添加到历史记录（自动维护最多5条，包含时间戳）
                subscription.addMessageToHistory(message)
                
                val index = mSubscriptionList.indexOf(subscription)
                if (index != -1) {
                    mAdapter?.notifyItemChanged(index)
                    Log.d("SubscriptionFragment", "✅ Updated subscription for topic: $normalizedTopic")
                    Log.d("SubscriptionFragment", "History size: ${subscription.messageHistory.size}")
                }
                saveSubscriptions()
            } else {
                Log.w("SubscriptionFragment", "❌ No matching subscription found for topic: '$normalizedTopic'")
                Log.d("SubscriptionFragment", "Available topics: ${mSubscriptionList.map { "'${it.topic}'" }}")
            }
        }
    }
    
    /**
     * ⭐ 规则9修复：观察MQTT消息事件并更新订阅列表
     */
    private fun observeMqttEvents() {
        MqttEventBus.messageArrived.observe(viewLifecycleOwner) { event ->
            Log.d("SubscriptionFragment", "===== MQTT Message Received =====")
            Log.d("SubscriptionFragment", "Topic: '${event.topic}'")
            Log.d("SubscriptionFragment", "Payload length: ${event.payload.length}")
            Log.d("SubscriptionFragment", "Subscription count: ${mSubscriptionList.size}")
            Log.d("SubscriptionFragment", "Subscribed topics: ${mSubscriptionList.map { it.topic }}")
            
            // ⭐ 修复：Fragment只负责UI更新，TTS和浮动窗口由MainActivity统一处理
            // Deleted:// ⭐ 修复Bug 2：触发TTS播报和浮动窗口
            // Deleted:(activity as? MainActivity)?.let { mainActivity ->
            // Deleted:    Log.d("SubscriptionFragment", "Triggering TTS and float window")
            // Deleted:    mainActivity.triggerTTS(event.payload, force = true)
            // Deleted:    mainActivity.triggerFloatWindow(event.topic, event.payload)
            // Deleted:} ?: run {
            // Deleted:    Log.w("SubscriptionFragment", "⚠️ MainActivity is null, cannot trigger TTS/float window")
            // Deleted:}
            
            // 更新订阅消息列表
            updateSubscriptionMessage(event.topic, event.payload)
        }
        
        Log.d("SubscriptionFragment", "Observer registered with viewLifecycleOwner: $viewLifecycleOwner")
    }

    companion object {
        fun newInstance(): SubscriptionFragment {
            return SubscriptionFragment()
        }
    }
}