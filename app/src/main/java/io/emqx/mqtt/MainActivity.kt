package io.emqx.mqtt

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
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
import org.eclipse.paho.client.mqttv3.IMqttActionListener

class MainActivity : AppCompatActivity(), MqttCallback {
    var mClient: MqttAsyncClient? = null
    private var mConnection: Connection? = null
    private val mFragmentList: MutableList<Fragment> = ArrayList()
    private var isConnecting = false
    private var logCallback: ((String) -> Unit)? = null

    var ttsManager: TTSManager? = null
    var floatWindowManager: FloatWindowManager? = null

    var isTTSEnabled = true
        set(value) {
            field = value
            Log.d("MainActivity", "isTTSEnabled changed to: $value")
        }

    var isFloatWindowEnabled = true
        set(value) {
            field = value
            Log.d("MainActivity", "isFloatWindowEnabled changed to: $value")
        }

    var isAutoCaptureVoiceEnabled = false

    private var mqttStatusListener: ((Boolean) -> Unit)? = null

    fun setOnMqttStatusChangedListener(listener: (Boolean) -> Unit) {
        mqttStatusListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        CapturedTextManager.init(this)

        window.decorView.post {
            ttsManager = TTSManager(this)
            ttsManager?.setOnInitListener(object : TTSManager.OnInitListener {
                override fun onInitSuccess() {
                    Log.d("MainActivity", "TTS initialized successfully!")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "TTS ready", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onInitFailed(status: Int) {
                    Log.e("MainActivity", "TTS initialization failed! status=$status")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "TTS init failed: $status", Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
        floatWindowManager = FloatWindowManager.getInstance(this)

        mFragmentList.add(HomeFragment.newInstance())
        mFragmentList.add(SettingFragment.newInstance())
        mFragmentList.add(SubscriptionFragment.newInstance())
        mFragmentList.add(PublishFragment.newInstance())
        mFragmentList.add(MessageFragment.newInstance())

        val sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager, this, mFragmentList)
        val viewPager = findViewById<ViewPager>(R.id.view_pager)
        viewPager.offscreenPageLimit = 4
        viewPager.adapter = sectionsPagerAdapter

        val tabs = findViewById<TabLayout>(R.id.tabs)
        tabs.setupWithViewPager(viewPager)

        for (i in 0 until tabs.tabCount) {
            val tab = tabs.getTabAt(i)
            tab?.setIcon(sectionsPagerAdapter.getPageIcon(i))
        }

        tabs.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTabLayoutForOrientation()
        }
        updateTabLayoutForOrientation()

        setupAccessibilityService()

        if (intent.getBooleanExtra("auto_connect", false)) {
            Log.d("MainActivity", "Auto-connect requested")
            window.decorView.postDelayed({
                autoConnectIfConfigured()
            }, 1000)
        }
    }

    private fun updateTabLayoutForOrientation() {
        val tabs = findViewById<TabLayout>(R.id.tabs)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val tabHeight = resources.getDimensionPixelSize(R.dimen.tab_height)

        if (isLandscape) {
            tabs.layoutParams = android.widget.LinearLayout.LayoutParams(
                tabHeight,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            )
            tabs.tabGravity = TabLayout.GRAVITY_FILL
            tabs.tabMode = TabLayout.MODE_FIXED
            tabs.rotation = 270f

            val viewPager = findViewById<ViewPager>(R.id.view_pager)
            viewPager.layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )

            val rootLayout = findViewById<android.widget.LinearLayout>(R.id.root_layout)
            rootLayout?.orientation = android.widget.LinearLayout.HORIZONTAL
        } else {
            tabs.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            tabs.tabGravity = TabLayout.GRAVITY_FILL
            tabs.tabMode = TabLayout.MODE_FIXED
            tabs.rotation = 0f

            val viewPager = findViewById<ViewPager>(R.id.view_pager)
            viewPager.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    private fun autoConnectIfConfigured() {
        val configManager = ConfigManager.getInstance(this)
        if (configManager.autoConnect && configManager.hasSavedConfig()) {
            Log.d("MainActivity", "Auto-connecting with saved config...")
            val connection = Connection(
                this,
                configManager.host,
                configManager.port,
                configManager.clientId,
                configManager.username,
                configManager.password,
                configManager.protocol,
                configManager.path
            )
            connect(connection, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.d("MainActivity", "Auto-connect success")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Auto-connected", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.e("MainActivity", "Auto-connect failed: ${exception?.message}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Auto-connect failed", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun setupAccessibilityService() {
        CapturedTextManager.addListener { captured ->
            Log.d("MainActivity", "Text captured from ${captured.packageName}: ${captured.text}")
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

    fun appendLog(message: String) {
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
            MqttService.updateConnectionStatus(this, false)
            mClient?.connect(connection.mqttConnectOptions, null, object : org.eclipse.paho.client.mqttv3.IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isConnecting = false
                    appendLog("=== CONNECT SUCCESS ===")
                    MqttService.updateConnectionStatus(this@MainActivity, true)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connected!", Toast.LENGTH_SHORT).show()
                        mqttStatusListener?.invoke(true)
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnecting = false
                    appendLog("=== CONNECT FAILED ===")
                    appendLog("Error: ${exception?.message}")
                    MqttService.updateConnectionStatus(this@MainActivity, false)
                    exception?.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connect failed: ${exception?.message}", Toast.LENGTH_LONG).show()
                        mqttStatusListener?.invoke(false)
                    }
                }
            })
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            isConnecting = false
            MqttService.updateConnectionStatus(this, false)
            mqttStatusListener?.invoke(false)
            e.printStackTrace()
            Toast.makeText(this, "MqttException: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            isConnecting = false
            MqttService.updateConnectionStatus(this, false)
            mqttStatusListener?.invoke(false)
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
            MqttService.updateConnectionStatus(this, false)
            mqttStatusListener?.invoke(false)
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            MqttService.updateConnectionStatus(this, false)
            mqttStatusListener?.invoke(false)
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
        Log.d("MainActivity", "showFloatMessage called: title=$title, isFloatWindowEnabled=$isFloatWindowEnabled")
        if (isFloatWindowEnabled) {
            if (floatWindowManager?.canDrawOverlays() == false) {
                Toast.makeText(this, "Please grant overlay permission for float window", Toast.LENGTH_LONG).show()
                floatWindowManager?.requestOverlayPermission()
                return
            }
            floatWindowManager?.showMessage(title, message)
        } else {
            Log.d("MainActivity", "Float window is disabled, skipping")
        }
    }

    fun hideFloatMessage() {
        floatWindowManager?.hide()
    }

    fun speakText(text: String) {
        Log.d("MainActivity", "speakText called: $text, isTTSEnabled=$isTTSEnabled")
        if (isTTSEnabled) {
            ttsManager?.speak(text)
        } else {
            Log.d("MainActivity", "TTS is disabled, skipping")
        }
    }

    override fun connectionLost(cause: Throwable?) {
        appendLog("Connection lost: $cause")
        isConnecting = false
        MqttService.updateConnectionStatus(this, false)
        mqttStatusListener?.invoke(false)
        runOnUiThread {
            (mFragmentList[1] as? SettingFragment)?.updateButtonText()
        }
    }

    @Throws(Exception::class)
    override fun messageArrived(topic: String, message: MqttMessage) {
        val payload = String(message.payload)
        Log.d("MainActivity", "===== MESSAGE ARRIVED =====")
        Log.d("MainActivity", "Topic: $topic")
        Log.d("MainActivity", "Payload: $payload")
        Log.d("MainActivity", "isTTSEnabled: $isTTSEnabled")
        Log.d("MainActivity", "isFloatWindowEnabled: $isFloatWindowEnabled")

        runOnUiThread {
            appendLog("===== MESSAGE RECEIVED =====")
            appendLog("Topic: $topic")
            appendLog("Payload: $payload")

            (mFragmentList[2] as? SubscriptionFragment)?.updateSubscriptionMessage(topic, payload)

            if (isFloatWindowEnabled) {
                Log.d("MainActivity", "Showing float window for message")
                appendLog("Showing float window...")
                floatWindowManager?.showMessage(topic, payload)
            } else {
                appendLog("Float window is disabled, skipping")
            }

            if (isTTSEnabled) {
                Log.d("MainActivity", "Speaking message with TTS")
                appendLog("Speaking TTS...")
                ttsManager?.speak(payload)
            } else {
                appendLog("TTS is disabled, skipping")
            }
        }
    }

    override fun deliveryComplete(token: IMqttDeliveryToken) {}
}
