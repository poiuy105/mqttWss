package io.emqx.mqtt

import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
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
        val subs = mSubscriptionList.joinToString(";") { "${it.topic},${it.qos},${it.lastMessage}" }
        mConfigManager.subscriptionHistory = subs
    }

    private fun loadSubscriptions() {
        val saved = mConfigManager.subscriptionHistory
        if (saved.isNotEmpty()) {
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
        }
    }

    fun updateSubscriptionMessage(topic: String, message: String) {
        activity?.runOnUiThread {
            val subscription = mSubscriptionList.find { it.topic == topic }
            if (subscription != null) {
                subscription.lastMessage = message
                val index = mSubscriptionList.indexOf(subscription)
                if (index != -1) {
                    mAdapter?.notifyItemChanged(index)
                }
                saveSubscriptions()
            }
        }
    }

    companion object {
        fun newInstance(): SubscriptionFragment {
            return SubscriptionFragment()
        }
    }
}