package io.emqx.mqtt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private lateinit var mAutoConnect: Switch
    private lateinit var mConfigManager: ConfigManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            MqttService.startService(requireContext())
        }
    }

    override val layoutResId: Int
        get() = R.layout.fragment_connection

    private fun appendLog(message: String) {
        activity?.runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logMessage = "[$timestamp] $message\n"
            mLogText.append(logMessage)
            Log.d("ConnectionFragment", message)
        }
    }

    private fun saveCurrentConfig() {
        val protocolName = when (mProtocol.checkedRadioButtonId) {
            R.id.protocol_tcp -> "TCP"
            R.id.protocol_ssl -> "SSL"
            R.id.protocol_ws -> "WS"
            R.id.protocol_wss -> "WSS"
            else -> "TCP"
        }

        mConfigManager.saveConnectionConfig(
            host = mHost.text.toString(),
            port = mPort.text.toString().toIntOrNull() ?: 1883,
            path = mPath.text.toString(),
            clientId = mClientId.text.toString(),
            username = mUsername.text.toString(),
            password = mPassword.text.toString(),
            protocol = protocolName
        )
        mConfigManager.autoConnect = mAutoConnect.isChecked
    }

    private fun loadSavedConfig() {
        if (mConfigManager.hasSavedConfig()) {
            mHost.setText(mConfigManager.host)
            mPort.setText(mConfigManager.port.toString())
            mPath.setText(mConfigManager.path)
            mClientId.setText(mConfigManager.clientId)
            mUsername.setText(mConfigManager.username)
            mPassword.setText(mConfigManager.password)
            mAutoConnect.isChecked = mConfigManager.autoConnect

            when (mConfigManager.protocol) {
                "TCP" -> mProtocol.check(R.id.protocol_tcp)
                "SSL" -> mProtocol.check(R.id.protocol_ssl)
                "WS" -> mProtocol.check(R.id.protocol_ws)
                "WSS" -> mProtocol.check(R.id.protocol_wss)
            }
            appendLog("Loaded saved configuration")
        }
    }

    override fun setUpView(view: View) {
        mConfigManager = ConfigManager.getInstance(requireContext())

        mHost = view.findViewById(R.id.host)
        mPort = view.findViewById(R.id.port)
        mPath = view.findViewById(R.id.path)
        mClientId = view.findViewById(R.id.clientid)
        mUsername = view.findViewById(R.id.username)
        mPassword = view.findViewById(R.id.password)
        mProtocol = view.findViewById(R.id.protocol)
        mButton = view.findViewById(R.id.btn_connect)
        mLogText = view.findViewById(R.id.log_text)
        mAutoConnect = view.findViewById(R.id.auto_connect_switch)

        if (mClientId.text.isNullOrEmpty()) {
            mClientId.setText(MqttAsyncClient.generateClientId())
        }

        (activity as? MainActivity)?.setLogCallback { message ->
            appendLog(message)
        }

        appendLog("=== Connection Debug Log ===")
        appendLog("Fragment initialized")

        loadSavedConfig()

        mProtocol.setOnCheckedChangeListener { _, checkedId ->
            val port = when (checkedId) {
                R.id.protocol_tcp -> 1883
                R.id.protocol_ssl -> 8883
                R.id.protocol_ws -> 8083
                R.id.protocol_wss -> 443
                else -> 1883
            }
            mPort.setText(port.toString())

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

                saveCurrentConfig()

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
                appendLog("Connection object created")
                appendLog("URI: ${connection.buildUri()}")
                appendLog("Calling connect()...")

                (fragmentActivity as MainActivity).connect(
                    connection,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken) {
                            appendLog("=== CONNECT SUCCESS ===")
                            appendLog("Server URI: ${asyncActionToken.client.serverURI}")
                            appendLog("Client ID: ${asyncActionToken.client.clientId}")
                            updateButtonText()
                            startNotificationService()
                        }

                        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                            appendLog("=== CONNECT FAILED ===")
                            appendLog("Error: ${exception?.message}")
                            Toast.makeText(
                                fragmentActivity,
                                "Connect failed: ${exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
            } else {
                appendLog("Disconnect button clicked")
                stopNotificationService()
                (fragmentActivity as MainActivity).disconnect()
            }
        }
    }

    private fun startNotificationService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        MqttService.startService(requireContext())
    }

    private fun stopNotificationService() {
        MqttService.stopService(requireContext())
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
