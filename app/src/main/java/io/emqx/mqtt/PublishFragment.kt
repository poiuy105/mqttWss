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
            appendTtsLog("=== 测试TTS(默认) ===")
            testTtsBasic()
        }

        view.findViewById<Button>(R.id.test_tts_china).setOnClickListener {
            appendTtsLog("=== 测试中文TTS ===")
            testTtsChina()
        }

        view.findViewById<Button>(R.id.test_tts_us).setOnClickListener {
            appendTtsLog("=== 测试英文TTS ===")
            testTtsUS()
        }

        view.findViewById<Button>(R.id.test_tts_uk).setOnClickListener {
            appendTtsLog("=== 测试UK英文TTS ===")
            testTtsUK()
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
            appendTtsLog("=== 测试追加TTS ===")
            testTtsAdd()
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

    private fun getTtsInfo(): Pair<MainActivity?, TTSManager?> {
        val mainActivity = fragmentActivity as? MainActivity
        return Pair(mainActivity, mainActivity?.ttsManager)
    }

    private fun testTtsBasic() {
        val (main, tts) = getTtsInfo()
        appendTtsLog("MainActivity: ${if (main != null) "OK" else "NULL"}")
        appendTtsLog("ttsManager: ${if (tts != null) "OK" else "NULL"}")
        appendTtsLog("initStatus: ${tts?.getInitStatus()}")
        appendTtsLog("isReady: ${tts?.isReady()}")
        appendTtsLog("isSpeaking: ${tts?.isSpeaking()}")
        appendTtsLog("engine: ${tts?.getEngineInfo()}")

        if (tts?.isReady() == true) {
            appendTtsLog("调用speak(TTS准备就绪)")
            tts.speak("TTS准备就绪")
        } else {
            appendTtsLog("TTS未就绪!")
        }
    }

    private fun testTtsChina() {
        val (_, tts) = getTtsInfo()
        appendTtsLog("isReady: ${tts?.isReady()}")
        if (tts?.isReady() == true) {
            appendTtsLog("调用speakWithChinaLocale(中文测试)")
            tts.speakWithChinaLocale("中文测试")
        } else {
            appendTtsLog("TTS未就绪!")
        }
    }

    private fun testTtsUS() {
        val (_, tts) = getTtsInfo()
        appendTtsLog("isReady: ${tts?.isReady()}")
        if (tts?.isReady() == true) {
            appendTtsLog("调用speakWithUSLocale(Hello world)")
            tts.speakWithUSLocale("Hello world")
        } else {
            appendTtsLog("TTS未就绪!")
        }
    }

    private fun testTtsUK() {
        val (_, tts) = getTtsInfo()
        appendTtsLog("isReady: ${tts?.isReady()}")
        if (tts?.isReady() == true) {
            appendTtsLog("调用speakWithUKLocale(Hello from UK)")
            tts.speakWithUKLocale("Hello from UK")
        } else {
            appendTtsLog("TTS未就绪!")
        }
    }

    private fun testTtsSlow() {
        val (_, tts) = getTtsInfo()
        appendTtsLog("isReady: ${tts?.isReady()}")
        if (tts?.isReady() == true) {
            appendTtsLog("调用speakWithSlowRate(慢速朗读)")
            tts.speakWithSlowRate("慢速朗读")
        } else {
            appendTtsLog("TTS未就绪!")
        }
    }

    private fun testTtsFast() {
        val (_, tts) = getTtsInfo()
        appendTtsLog("isReady: ${tts?.isReady()}")
        if (tts?.isReady() == true) {
            appendTtsLog("调用speakWithFastRate(快速朗读)")
            tts.speakWithFastRate("快速朗读")
        } else {
            appendTtsLog("TTS未就绪!")
        }
    }

    private fun testTtsAdd() {
        val (_, tts) = getTtsInfo()
        appendTtsLog("isReady: ${tts?.isReady()}")
        if (tts?.isReady() == true) {
            appendTtsLog("调用speakAdd(排队第一句)")
            tts.speakAdd("排队第一句")
            appendTtsLog("调用speakAdd(排队第二句)")
            tts.speakAdd("排队第二句")
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