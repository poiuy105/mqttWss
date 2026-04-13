package io.emqx.mqtt

import android.os.Bundle
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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

    private fun formatException(e: Throwable): String {
        val sb = StringBuilder()
        sb.append("Exception: ${e.javaClass.name}\n")
        sb.append("Message: ${e.message}\n")

        var cause = e.cause
        var level = 1
        while (cause != null && level <= 5) {
            sb.append("Cause $level: ${cause.javaClass.name}\n")
            sb.append("Cause $level Message: ${cause.message}\n")
            cause = cause.cause
            level++
        }

        val stackTrace = e.stackTraceToString()
        if (stackTrace.isNotEmpty()) {
            sb.append("\nStackTrace:\n")
            val lines = stackTrace.split("\n").take(20)
            for (line in lines) {
                sb.append("$line\n")
            }
        }

        return sb.toString()
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

        appendLog("Creating MQTT client...")
        appendLog("URI: ${connection.buildUri()}")
        appendLog("TLS: ${connection.mqttConnectOptions.socketFactory != null}")

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
                    val errorMsg = formatException(exception ?: Exception("Unknown error"))
                    appendLog("=== CONNECT FAILED ===")
                    for (line in errorMsg.split("\n")) {
                        if (line.isNotEmpty()) {
                            appendLog(line)
                        }
                    }
                    exception?.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connect failed: ${exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            isConnecting = false
            appendLog("MqttException: ${formatException(e)}")
            e.printStackTrace()
            Toast.makeText(this, "MqttException: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            isConnecting = false
            appendLog("Exception: ${formatException(e)}")
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

    fun notConnected(showNotify: Boolean): Boolean {
        if (mClient == null || !mClient!!.isConnected) {
            if (showNotify) {
                Toast.makeText(this, "Client is not connected", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return false
    }

    override fun connectionLost(cause: Throwable?) {
        appendLog("Connection lost: $cause")
        isConnecting = false
        runOnUiThread {
            (mFragmentList[0] as ConnectionFragment).updateButtonText()
        }
    }

    @Throws(Exception::class)
    override fun messageArrived(topic: String, message: MqttMessage) {
        (mFragmentList[3] as MessageFragment).updateMessage(Message(topic, message))
    }

    override fun deliveryComplete(token: IMqttDeliveryToken) {}
}
