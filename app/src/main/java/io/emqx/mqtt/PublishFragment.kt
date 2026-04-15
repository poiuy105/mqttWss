package io.emqx.mqtt

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
        if (ttsLogs.length > 1000) {
            ttsLogs.setLength(1000)
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

        view.findViewById<Button>(R.id.test_tts).setOnClickListener {
            appendTtsLog("=== 测试TTS ===")
            testTtsBasic()
        }

        view.findViewById<Button>(R.id.test_tts_china).setOnClickListener {
            appendTtsLog("=== 测试中文TTS ===")
            testTtsBasic()
        }

        view.findViewById<Button>(R.id.test_tts_us).setOnClickListener {
            appendTtsLog("=== 测试英文TTS ===")
            testTtsBasic()
        }

        view.findViewById<Button>(R.id.test_tts_uk).setOnClickListener {
            appendTtsLog("=== 测试英文UK TTS ===")
            testTtsBasic()
        }

        view.findViewById<Button>(R.id.test_tts_slow).setOnClickListener {
            appendTtsLog("=== 测试慢速TTS ===")
            testTtsSlow()
        }

        view.findViewById<Button>(R.id.test_tts_fast).setOnClickListener {
            appendTtsLog("=== 测试快速TTS ===")
            testTtsFast()
        }

        view.findViewById<Button>(R.id.test_tts_add).setOnClickListener {
            appendTtsLog("=== 测试TTS ===")
            testTtsBasic()
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

    private fun getTtsManager(): TTSManager? {
        return (fragmentActivity as? MainActivity)?.ttsManager
    }

    private fun testTtsBasic() {
        val tts = getTtsManager()
        appendTtsLog("ttsManager: ${if (tts != null) "OK" else "NULL"}")
        appendTtsLog("initStatus: ${tts?.getInitStatus()}")
        appendTtsLog("isReady: ${tts?.isReady()}")

        if (tts?.isReady() == true) {
            appendTtsLog("调用speak(TTS准备就绪)")
            tts.speak("TTS准备就绪")
            appendTtsLog("speak()调用完成")
        } else {
            appendTtsLog("TTS未就绪!")
            appendTtsLog("提示: 请检查手机TTS设置")
        }
    }

    private fun testTtsSlow() {
        val tts = getTtsManager()
        appendTtsLog("isReady: ${tts?.isReady()}")
        if (tts?.isReady() == true) {
            appendTtsLog("设置语速0.5 + speak(慢速朗读)")
            tts.setSpeechRate(0.5f)
            tts.speak("慢速朗读")
            tts.setSpeechRate(1.0f)
        } else {
            appendTtsLog("TTS未就绪!")
        }
    }

    private fun testTtsFast() {
        val tts = getTtsManager()
        appendTtsLog("isReady: ${tts?.isReady()}")
        if (tts?.isReady() == true) {
            appendTtsLog("设置语速2.0 + speak(快速朗读)")
            tts.setSpeechRate(2.0f)
            tts.speak("快速朗读")
            tts.setSpeechRate(1.0f)
        } else {
            appendTtsLog("TTS未就绪!")
        }
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