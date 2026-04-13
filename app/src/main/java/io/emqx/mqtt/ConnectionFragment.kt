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

    override val layoutResId: Int
        get() = R.layout.fragment_connection

    override fun setUpView(view: View) {
        mHost = view.findViewById(R.id.host)
        mHost.setText("broker.emqx.io")
        mPort = view.findViewById(R.id.port)
        mPath = view.findViewById(R.id.path)
        mClientId = view.findViewById(R.id.clientid)
        mClientId.setText(MqttAsyncClient.generateClientId())
        mUsername = view.findViewById(R.id.username)
        mPassword = view.findViewById(R.id.password)
        mProtocol = view.findViewById(R.id.protocol)
        mButton = view.findViewById(R.id.btn_connect)

        mProtocol.setOnCheckedChangeListener { _, checkedId ->
            val port = when (checkedId) {
                R.id.protocol_tcp -> 1883
                R.id.protocol_ssl -> 8883
                R.id.protocol_ws -> 8083
                R.id.protocol_wss -> 8084
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
                (fragmentActivity as MainActivity).connect(
                    connection,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken) {
                            Log.d(
                                "ConnectionFragment",
                                "Connected to: " + asyncActionToken.client.serverURI
                            )
                            updateButtonText()
                        }

                        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                            Toast.makeText(
                                fragmentActivity,
                                exception.toString(),
                                Toast.LENGTH_SHORT
                            ).show()
                            exception.printStackTrace()
                        }
                    })
            } else {
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
