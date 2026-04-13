package io.emqx.mqtt

import android.util.Log
import android.view.View
import android.widget.*
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient

class ConnectionFragment : BaseFragment() {
    private lateinit var mHost: EditText
    private lateinit var mPort: EditText
    private lateinit var mPath: EditText
    private lateinit var mClientId: EditText
    private lateinit var mUsername: EditText
    private lateinit var mPassword: EditText
    private lateinit var mProtocol: RadioGroup
    private lateinit var mButton: Button
    private lateinit var mLogText: TextView

    override val layoutResId: Int
        get() = R.layout.fragment_connection

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
            val lines = stackTrace.split("\n").take(15)
            for (line in lines) {
                sb.append("$line\n")
            }
        }

        return sb.toString()
    }

    private fun appendLog(message: String) {
        activity?.runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            val logMessage = "[$timestamp] $message\n"
            mLogText.append(logMessage)
            Log.d("ConnectionFragment", message)
        }
    }

    override fun setUpView(view: View) {
        mHost = view.findViewById(R.id.host)
        mHost.setText("ha.urright.cloud")
        mPort = view.findViewById(R.id.port)
        mPort.setText("1443")
        mPath = view.findViewById(R.id.path)
        mPath.setText("/mqtt/ws")
        mClientId = view.findViewById(R.id.clientid)
        mClientId.setText(MqttAsyncClient.generateClientId())
        mUsername = view.findViewById(R.id.username)
        mUsername.setText("weipc")
        mPassword = view.findViewById(R.id.password)
        mPassword.setText("weipc")
        mProtocol = view.findViewById(R.id.protocol)
        mButton = view.findViewById(R.id.btn_connect)
        mLogText = view.findViewById(R.id.log_text)

        appendLog("=== Connection Debug Log ===")
        appendLog("Fragment initialized")

        mProtocol.setOnCheckedChangeListener { _, checkedId ->
            val port = when (checkedId) {
                R.id.protocol_tcp -> 1883
                R.id.protocol_ssl -> 8883
                R.id.protocol_ws -> 8083
                R.id.protocol_wss -> 1443
                else -> 1883
            }
            mPort.setText(port.toString())
            appendLog("Protocol changed, port set to: $port")

            val pathVisibility = when (checkedId) {
                R.id.protocol_ws, R.id.protocol_wss -> View.VISIBLE
                else -> View.GONE
            }
            mPath.visibility = pathVisibility
        }

        mButton.setOnClickListener {
            if (mButton.text.toString() == getString(R.string.connect)) {
                val protocolName = when (mProtocol.checkedRadioButtonId) {
                    R.id.protocol_tcp -> "TCP"
                    R.id.protocol_ssl -> "SSL"
                    R.id.protocol_ws -> "WS"
                    R.id.protocol_wss -> "WSS"
                    else -> "TCP"
                }

                appendLog("=== Starting Connection ===")
                appendLog("Protocol: $protocolName")
                appendLog("Host: ${mHost.text}")
                appendLog("Port: ${mPort.text}")
                appendLog("Path: ${mPath.text}")
                appendLog("ClientId: ${mClientId.text}")
                appendLog("Username: ${mUsername.text}")

                val connection = Connection(
                    fragmentActivity!!,
                    mHost.text.toString(),
                    mPort.text.toString().toInt(),
                    mClientId.text.toString(),
                    mUsername.text.toString(),
                    mPassword.text.toString(),
                    protocolName,
                    mPath.text.toString()
                )
                appendLog("Connection object created, URI: ${connection.buildUri()}")
                appendLog("Calling connect()...")

                (fragmentActivity as MainActivity).connect(
                    connection,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken) {
                            appendLog("=== CONNECT SUCCESS ===")
                            appendLog("Server URI: ${asyncActionToken.client.serverURI}")
                            appendLog("Client ID: ${asyncActionToken.client.clientId}")
                            updateButtonText()
                        }

                        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                            appendLog("=== CONNECT FAILED ===")
                            appendLog(formatException(exception))
                            Toast.makeText(
                                fragmentActivity,
                                "Connect failed: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
            } else {
                appendLog("Disconnect button clicked")
                (fragmentActivity as MainActivity).disconnect()
            }
        }
    }

    fun updateButtonText() {
        if ((fragmentActivity as MainActivity).notConnected(false)) {
            mButton.text = getText(R.string.connect)
        } else {
            mButton.text = getString(R.string.disconnect)
        }
    }

    companion object {
        fun newInstance(): ConnectionFragment {
            return ConnectionFragment()
        }
    }
}
