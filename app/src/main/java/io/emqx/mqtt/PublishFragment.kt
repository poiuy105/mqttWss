package io.emqx.mqtt

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
    private var mLogView: TextView? = null
    private val logBuilder = StringBuilder()
    private lateinit var mConfigManager: ConfigManager

    override val layoutResId: Int
        get() = R.layout.fragment_publish_list

    private fun appendLog(message: String) {
        logBuilder.insert(0, "${message}\n")
        if (logBuilder.length > 2000) {
            logBuilder.setLength(2000)
        }
        activity?.runOnUiThread {
            mLogView?.text = "日志:\n${logBuilder.toString()}"
        }
    }

    override fun setUpView(view: View) {
        mConfigManager = ConfigManager.getInstance(fragmentActivity)

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
        mLogView = view.findViewById(R.id.tts_log)

        loadPublishSettings()

        view.findViewById<Button>(R.id.test_tts).setOnClickListener {
            appendLog("=== TTS测试 ===")
            testTts()
        }

        view.findViewById<Button>(R.id.test_popup).setOnClickListener {
            appendLog("=== 弹窗测试 ===")
            testPopup()
        }

        view.findViewById<Button>(R.id.test_overlay_permission).setOnClickListener {
            appendLog("=== 申请悬浮窗权限 ===")
            requestOverlayPermission()
        }

        val pubBtn = view.findViewById<Button>(R.id.publish)
        pubBtn.setOnClickListener {
            val publish = publish
            savePublishSettings()
            appendLog("发布消息: topic=${publish.topic}")
            (fragmentActivity as MainActivity).publish(publish, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    mPublishList.add(0, publish)
                    mAdapter!!.notifyItemInserted(0)
                    appendLog("发布成功")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Toast.makeText(fragmentActivity, "Failed to publish", Toast.LENGTH_SHORT).show()
                    appendLog("发布失败: ${exception?.message}")
                }
            })
        }
    }

    private fun savePublishSettings() {
        mConfigManager.publishTopic = mTopic?.text.toString() ?: ""
        mConfigManager.publishPayload = mPayload?.text.toString() ?: ""
        mConfigManager.publishQos = when (mQosRadioGroup?.checkedRadioButtonId) {
            R.id.qos0 -> 0
            R.id.qos1 -> 1
            R.id.qos2 -> 2
            else -> 0
        }
        mConfigManager.publishRetained = when (mRetainedRadioGroup?.checkedRadioButtonId) {
            R.id.retained_true -> true
            else -> false
        }
    }

    private fun loadPublishSettings() {
        val savedTopic = mConfigManager.publishTopic
        val savedPayload = mConfigManager.publishPayload
        val savedQos = mConfigManager.publishQos
        val savedRetained = mConfigManager.publishRetained

        mTopic?.setText(savedTopic)
        mPayload?.setText(savedPayload)

        when (savedQos) {
            0 -> mQosRadioGroup?.check(R.id.qos0)
            1 -> mQosRadioGroup?.check(R.id.qos1)
            2 -> mQosRadioGroup?.check(R.id.qos2)
        }
        if (savedRetained) {
            mRetainedRadioGroup?.check(R.id.retained_true)
        } else {
            mRetainedRadioGroup?.check(R.id.retained_false)
        }
    }

    private fun testTts() {
        val tts = getTtsManager()
        appendLog("ttsManager: ${if (tts != null) "OK" else "NULL"}")
        appendLog("isReady: ${tts?.isReady()}")

        if (tts?.isReady() == true) {
            appendLog("调用speak(TTS准备就绪)")
            tts.speak("TTS准备就绪")
            appendLog("speak()调用完成")
        } else {
            appendLog("TTS未就绪!")
        }
    }

    private fun testPopup() {
        appendLog("1. 检查悬浮窗权限...")
        val hasOverlay = Settings.canDrawOverlays(activity)
        appendLog("悬浮窗权限: ${if (hasOverlay) "已授权" else "未授权"}")

        if (!hasOverlay) {
            appendLog("请先授予悬浮窗权限")
            requestOverlayPermission()
            return
        }

        appendLog("2. 创建弹窗...")
        (fragmentActivity as? MainActivity)?.let { main ->
            main.showFloatMessage("测试弹窗", "这是一条测试消息\n${System.currentTimeMillis()}")
            appendLog("3. 弹窗已显示")
            appendLog("4. 5秒后自动消失")

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                main.hideFloatMessage()
                appendLog("5. 弹窗已隐藏")
            }, 5000)
        } ?: run {
            appendLog("MainActivity无效，无法显示弹窗")
        }
    }

    private fun requestOverlayPermission() {
        appendLog("正在打开悬浮窗权限设置...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${activity?.packageName}")
            try {
                startActivity(intent)
                appendLog("已打开权限设置页面")
            } catch (e: Exception) {
                appendLog("打开失败: ${e.message}")
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                try {
                    startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    appendLog("备用方式也失败: ${e2.message}")
                }
            }
        } else {
            appendLog("Android版本低于6.0，无需申请")
        }
    }

    private fun getTtsManager(): TTSManager? {
        return (fragmentActivity as? MainActivity)?.ttsManager
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