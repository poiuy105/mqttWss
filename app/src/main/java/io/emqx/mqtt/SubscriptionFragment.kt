package io.emqx.mqtt

import android.util.Log
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import java.util.ArrayList

class SubscriptionFragment : BaseFragment() {
    private var mTopic: EditText? = null
    private var mRadioGroup: RadioGroup? = null
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
        mConfigManager = ConfigManager.getInstance(fragmentActivity)

        val recyclerView = view.findViewById<RecyclerView>(R.id.subscription_list)
        mAdapter = SubscriptionRecyclerViewAdapter(mSubscriptionList)
        recyclerView.adapter = mAdapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                fragmentActivity,
                DividerItemDecoration.VERTICAL
            )
        )
        mTopic = view.findViewById(R.id.topic)
        mRadioGroup = view.findViewById(R.id.qos)
        val subBtn = view.findViewById<Button>(R.id.subscribe)
        subBtn.setOnClickListener {
            val topic = mTopic?.text.toString() ?: ""
            if (topic.isEmpty()) {
                Toast.makeText(fragmentActivity, "Please enter topic", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val subscription = subscription
            appendLog("Subscribing to: $topic with QoS ${subscription.qos}")
            (fragmentActivity as MainActivity).subscribe(
                subscription,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken) {
                        appendLog("Subscribe SUCCESS: $topic")
                        mSubscriptionList.add(0, subscription)
                        mAdapter?.notifyItemInserted(0)
                        saveSubscriptions()
                        Toast.makeText(fragmentActivity, "Subscribed: $topic", Toast.LENGTH_SHORT).show()
                        (fragmentActivity as? MainActivity)?.speakText("已订阅: $topic")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                        appendLog("Subscribe FAILED: $exception")
                        Toast.makeText(fragmentActivity, "Failed to subscribe: $exception", Toast.LENGTH_SHORT)
                            .show()
                    }
                })
        }

        loadSubscriptions()
    }

    private fun saveSubscriptions() {
        val subs = mSubscriptionList.joinToString(";") { "${it.topic},${it.qos}" }
        mConfigManager.subscriptions = subs
    }

    private fun loadSubscriptions() {
        val saved = mConfigManager.subscriptions
        if (saved.isNotEmpty()) {
            mSubscriptionList.clear()
            saved.split(";").forEach { item ->
                val parts = item.split(",")
                if (parts.size == 2) {
                    val topic = parts[0]
                    val qos = parts[1].toIntOrNull() ?: 0
                    mSubscriptionList.add(Subscription(topic, qos))
                }
            }
            mAdapter?.notifyDataSetChanged()
        }
    }

    private val subscription: Subscription
        get() {
            val topic = mTopic?.text.toString() ?: ""
            var qos = 0
            when (mRadioGroup?.checkedRadioButtonId) {
                R.id.qos0 -> qos = 0
                R.id.qos1 -> qos = 1
                R.id.qos2 -> qos = 2
            }
            return Subscription(topic, qos)
        }

    companion object {
        fun newInstance(): SubscriptionFragment {
            return SubscriptionFragment()
        }
    }
}
