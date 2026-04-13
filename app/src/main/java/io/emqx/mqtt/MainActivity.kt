package io.emqx.mqtt

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity : AppCompatActivity(), MqttCallback {
    private var mClient: MqttAsyncClient? = null
    private var mConnection: Connection? = null
    private val mFragmentList: MutableList<Fragment> = ArrayList()
    private var isConnecting = false
    private var logCallback: ((String) -> Unit)? = null

    private lateinit var ttsManager: TTSManager
    private lateinit var floatWindowManager: FloatWindowManager

    var isTTSEnabled = true
    var isFloatWindowEnabled = true
    var isAutoCaptureVoiceEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ttsManager = TTSManager.getInstance(this)
        floatWindowManager = FloatWindowManager.getInstance(this)

        mFragmentList.add(ConnectionFragment.newInstance())
        mFragmentList.add(SubscriptionFragment.newInstance())
        mFragmentList.add(PublishFragment.newInstance())
        mFragmentList.add(MessageFragment.newInstance())

        val sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager, this, mFragmentList)
        val viewPager = findViewById<ViewPager>(R.id.view_pager)
        viewPager.offscreenPageLimit = 3
        viewPager.adapter = sectionsPagerAdapter

        val tabs = findViewById<TabLayout>(R.id.tabs)
        tabs.setupWithViewPager(viewPager)

        setupAccessibilityService()
    }

    private fun setupAccessibilityService() {
        VoiceAccessibilityService.setOnTextCapturedListener { text ->
            runOnUiThread {
                Log.d("MainActivity", "Voice captured: $text")
                if (isAutoCaptureVoiceEnabled) {
                    showFloatMessage("Voice Captured", text)
                    if (isTTSEnabled) {
                        ttsManager.speak("已捕获语音：$text")
                    }
                }
            }
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(packageName) == true
    }

    fun requestAccessibilityService() {
        Toast.makeText(
            this,
            "Please enable accessibility service for voice capture",
            Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            logCallback?.invoke(message)
            Log.d("MainActivity", message)
        }
    }

    fun connect(connection: Connection, listener: org.eclipse.paho.client.mqttv3.IMqttActionListener?) {
        if (isConnecting) {
            appendLog("Already connecting, ignore")
            return
        }

        if (mClient != null && mClient!!.isConnected) {
            appendLog("Already connected, ignore duplicate connect request")
            Toast.makeText(this, "Already connected", Toast.LENGTH_SHORT).show()
            return
        }

        isConnecting = true
        mConnection = connection
        mClient = connection.getMqttClient()

        try {
            mClient?.setCallback(this)
            appendLog("Calling connect()...")
            mClient?.connect(connection.mqttConnectOptions, null, object : org.eclipse.paho.client.mqttv3.IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isConnecting = false
                    appendLog("=== CONNECT SUCCESS ===")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connected!", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnecting = false
                    appendLog("=== CONNECT FAILED ===")
                    appendLog("Error: ${exception?.message}")
                    exception?.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connect failed: ${exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            isConnecting = false
            e.printStackTrace()
            Toast.makeText(this, "MqttException: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            isConnecting = false
            e.printStackTrace()
            Toast.makeText(this, "Exception: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun disconnect() {
        if (notConnected(true)) {
            return
        }
        try {
            mClient?.disconnect()
            mClient = null
            mConnection = null
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            e.printStackTrace()
        }
    }

    fun subscribe(subscription: Subscription, listener: org.eclipse.paho.client.mqttv3.IMqttActionListener?) {
        if (notConnected(true)) {
            return
        }
        try {
            mClient?.subscribe(subscription.topic, subscription.qos)
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to subscribe", Toast.LENGTH_SHORT).show()
        }
    }

    fun publish(publish: Publish, callback: org.eclipse.paho.client.mqttv3.IMqttActionListener?) {
        if (notConnected(true)) {
            return
        }
        try {
            mClient?.publish(
                publish.topic,
                publish.payload.toByteArray(),
                publish.qos,
                publish.isRetained
            )
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to publish", Toast.LENGTH_SHORT).show()
        }
    }

    fun publishMessage(topic: String, payload: String, qos: Int = 1) {
        if (notConnected(true)) {
            return
        }
        try {
            mClient?.publish(topic, payload.toByteArray(), qos, false)
            Toast.makeText(this, "Published: $payload", Toast.LENGTH_SHORT).show()
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to publish", Toast.LENGTH_SHORT).show()
        }
    }

    fun notConnected(showNotify: Boolean): Boolean {
        if (mClient == null || !mClient!!.isConnected) {
            if (showNotify) {
                Toast.makeText(this, "Client is not connected", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return false
    }

    fun showFloatMessage(title: String, message: String) {
        if (isFloatWindowEnabled) {
            floatWindowManager.showMessage(title, message)
        }
    }

    fun speakText(text: String) {
        if (isTTSEnabled) {
            ttsManager.speak(text)
        }
    }

    override fun connectionLost(cause: Throwable?) {
        appendLog("Connection lost: $cause")
        isConnecting = false
        runOnUiThread {
            (mFragmentList[0] as? ConnectionFragment)?.updateButtonText()
        }
    }

    @Throws(Exception::class)
    override fun messageArrived(topic: String, message: MqttMessage) {
        val payload = String(message.payload)
        Log.d("MainActivity", "Message arrived: [$topic] $payload")

        runOnUiThread {
            (mFragmentList[3] as? MessageFragment)?.updateMessage(Message(topic, message))

            if (isFloatWindowEnabled) {
                showFloatMessage(topic, payload)
            }

            if (isTTSEnabled) {
                ttsManager.speak(payload)
            }
        }
    }

    override fun deliveryComplete(token: IMqttDeliveryToken) {}
}
