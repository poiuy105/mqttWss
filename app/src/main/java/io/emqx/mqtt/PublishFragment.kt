package io.emqx.mqtt

import android.util.Log
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken

class PublishFragment : BaseFragment() {
    private var mTopic: EditText? = null
    private var mPayload: EditText? = null
    private var mQosRadioGroup: RadioGroup? = null
    private var mRetainedRadioGroup: RadioGroup? = null
    var mAdapter: PublishRecyclerViewAdapter? = null
    var mPublishList: ArrayList<Publish> = ArrayList()
    private var mTtsLog: TextView? = null
    private val ttsLogs = StringBuilder()

    override val layoutResId: Int
        get() = R.layout.fragment_publish_list

    private fun appendTtsLog(message: String) {
        ttsLogs.insert(0, "${message}\n")
        if (ttsLogs.length > 500) {
            ttsLogs.setLength(500)
        }
        activity?.runOnUiThread {
            mTtsLog?.text = "TTS日志:\n${ttsLogs.toString()}"
        }
    }

    override fun setUpView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.publication_list)
        mAdapter = PublishRecyclerViewAdapter(mPublishList)
        recyclerView.adapter = mAdapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                fragmentActivity,
                DividerItemDecoration.VERTICAL
            )
        )
        mTopic = view.findViewById(R.id.topic)
        mPayload = view.findViewById(R.id.payload)
        mQosRadioGroup = view.findViewById(R.id.qos)
        mRetainedRadioGroup = view.findViewById(R.id.retained)
        mTtsLog = view.findViewById(R.id.tts_log)

        val testTtsBtn = view.findViewById<Button>(R.id.test_tts)
        testTtsBtn.setOnClickListener {
            appendTtsLog("=== TTS测试开始 ===")
            testTts()
        }

        val pubBtn = view.findViewById<Button>(R.id.publish)
        pubBtn.setOnClickListener {
            val publish = publish
            appendTtsLog("发布消息: topic=${publish.topic}, payload=${publish.payload}")
            (fragmentActivity as MainActivity).publish(publish, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    mPublishList.add(0, publish)
                    mAdapter!!.notifyItemInserted(0)
                    appendTtsLog("发布成功")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Toast.makeText(fragmentActivity, "Failed to publish", Toast.LENGTH_SHORT).show()
                    appendTtsLog("发布失败: ${exception?.message}")
                }
            })
        }
    }

    private fun testTts() {
        val mainActivity = fragmentActivity as? MainActivity
        val ttsManager = mainActivity?.ttsManager

        appendTtsLog("1. 检查MainActivity: ${if (mainActivity != null) "OK" else "NULL"}")
        appendTtsLog("2. 检查ttsManager: ${if (ttsManager != null) "OK" else "NULL"}")
        appendTtsLog("3. isReady: ${ttsManager?.isReady()}")
        appendTtsLog("4. isSpeaking: ${ttsManager?.isSpeaking()}")

        if (ttsManager?.isReady() == true) {
            appendTtsLog("5. 调用speak(\"TTS准备就绪\")")
            ttsManager.speak("TTS准备就绪")
            appendTtsLog("6. speak()调用完成")
        } else {
            appendTtsLog("TTS未就绪，无法测试")
        }
        appendTtsLog("=== TTS测试结束 ===")
    }

    private val publish: Publish
        get() {
            val topic = mTopic!!.text.toString()
            val message = mPayload!!.text.toString()
            var qos = 0
            when (mQosRadioGroup!!.checkedRadioButtonId) {
                R.id.qos0 -> qos = 0
                R.id.qos1 -> qos = 1
                R.id.qos2 -> qos = 2
            }
            var retained = false
            when (mRetainedRadioGroup!!.checkedRadioButtonId) {
                R.id.retained_true -> retained = true
                R.id.retained_false -> retained = false
            }
            return Publish(topic, message, qos, retained)
        }

    companion object {
        fun newInstance(): PublishFragment {
            return PublishFragment()
        }
    }
}