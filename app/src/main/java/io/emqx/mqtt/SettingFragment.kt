package io.emqx.mqtt

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingFragment : BaseFragment() {
    private lateinit var mHost: EditText
    private lateinit var mPort: EditText
    private lateinit var mPath: EditText
    private lateinit var mClientId: EditText
    private lateinit var mUsername: EditText
    private lateinit var mPassword: EditText
    private lateinit var mTilHost: TextInputLayout
    private lateinit var mTilPort: TextInputLayout
    private lateinit var mTilPath: TextInputLayout
    private lateinit var mTilClientId: TextInputLayout
    private lateinit var mTilUsername: TextInputLayout
    private lateinit var mTilPassword: TextInputLayout
    private lateinit var mProtocol: RadioGroup
    private lateinit var mButton: Button
    private lateinit var mDisconnectButton: Button
    private lateinit var mLogText: TextView
    private lateinit var mAutoConnect: Switch
    private lateinit var mTtsSwitch: Switch
    private lateinit var mFloatSwitch: Switch
    private lateinit var mVoiceSwitch: Switch
    private lateinit var mAutoStartSwitch: Switch
    private lateinit var mNotificationSwitch: Switch
    private lateinit var mShowDebugLogSwitch: Switch
    private lateinit var mDebugLogContainer: View
    private lateinit var mConfigManager: ConfigManager

    private val logBuilder = StringBuilder()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -&gt;
        if (isGranted) {
            MqttService.startService(requireContext())
        }
    }

    override val layoutResId: Int
        get() = R.layout.fragment_connection

    private fun appendLog(message: String) {
        activity?.runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logBuilder.insert(0, "[$timestamp] $message\n")
            if (logBuilder.length &gt; 2000) {
                logBuilder.setLength(2000)
            }
            mLogText.text = logBuilder.toString()
            Log.d("SettingFragment", message)
        }
    }

    private fun maskText(text: String): String {
        if (text.length &lt;= 2) return text
        return text.take(2) + "*".repeat(text.length - 2)
    }

    private fun setupPrivacyToggle(
        til: TextInputLayout,
        editText: EditText,
        originalValue: String,
        isPrivacyMode: Boolean,
        onToggle: (Boolean, String) -&gt; Unit
    ) {
        var currentOriginal = originalValue
        var currentPrivacy = isPrivacyMode

        if (currentPrivacy &amp;&amp; currentOriginal.isNotEmpty()) {
            editText.setText(maskText(currentOriginal))
        }

        til.setEndIconOnClickListener {
            currentPrivacy = !currentPrivacy
            if (currentPrivacy) {
                til.setEndIconDrawable(R.drawable.ic_eye_off)
                if (currentOriginal.isNotEmpty()) {
                    editText.setText(maskText(currentOriginal))
                }
            } else {
                til.setEndIconDrawable(R.drawable.ic_eye_on)
                editText.setText(currentOriginal)
            }
            onToggle(currentPrivacy, currentOriginal)
        }

        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!currentPrivacy) {
                    currentOriginal = s?.toString() ?: ""
                    onToggle(currentPrivacy, currentOriginal)
                }
            }
        })
    }

    private var originalHost = ""
    private var originalPort = ""
    private var originalPath = ""
    private var originalClientId = ""
    private var originalUsername = ""
    private var originalPassword = ""
    private var isHostPrivacy = true
    private var isPortPrivacy = true
    private var isPathPrivacy = true
    private var isClientIdPrivacy = true
    private var isUsernamePrivacy = true
    private var isPasswordPrivacy = true

    private fun saveCurrentConfig() {
        val protocolName = when (mProtocol.checkedRadioButtonId) {
            R.id.protocol_tcp -&gt; "TCP"
            R.id.protocol_ssl -&gt; "SSL"
            R.id.protocol_ws -&gt; "WS"
            R.id.protocol_wss -&gt; "WSS"
            else -&gt; "TCP"
        }

        mConfigManager.saveConnectionConfig(
            host = originalHost,
            port = originalPort.toIntOrNull() ?: 1883,
            path = originalPath,
            clientId = originalClientId,
            username = originalUsername,
            password = originalPassword,
            protocol = protocolName
        )
        mConfigManager.autoConnect = mAutoConnect.isChecked
        mConfigManager.autoStart = mAutoStartSwitch.isChecked
        mConfigManager.persistentNotification = mNotificationSwitch.isChecked
    }

    private fun loadSavedConfig() {
        if (mConfigManager.hasSavedConfig()) {
            originalHost = mConfigManager.host
            originalPort = mConfigManager.port.toString()
            originalPath = mConfigManager.path
            originalClientId = mConfigManager.clientId
            originalUsername = mConfigManager.username
            originalPassword = mConfigManager.password

            mHost.setText(if (isHostPrivacy &amp;&amp; originalHost.isNotEmpty()) maskText(originalHost) else originalHost)
            mPort.setText(if (isPortPrivacy &amp;&amp; originalPort.isNotEmpty()) maskText(originalPort) else originalPort)
            mPath.setText(if (isPathPrivacy &amp;&amp; originalPath.isNotEmpty()) maskText(originalPath) else originalPath)
            mClientId.setText(if (isClientIdPrivacy &amp;&amp; originalClientId.isNotEmpty()) maskText(originalClientId) else originalClientId)
            mUsername.setText(if (isUsernamePrivacy &amp;&amp; originalUsername.isNotEmpty()) maskText(originalUsername) else originalUsername)
            mPassword.setText(if (isPasswordPrivacy &amp;&amp; originalPassword.isNotEmpty()) maskText(originalPassword) else originalPassword)

            mAutoConnect.isChecked = mConfigManager.autoConnect
            mAutoStartSwitch.isChecked = mConfigManager.autoStart
            mNotificationSwitch.isChecked = mConfigManager.persistentNotification

            when (mConfigManager.protocol) {
                "TCP" -&gt; mProtocol.check(R.id.protocol_tcp)
                "SSL" -&gt; mProtocol.check(R.id.protocol_ssl)
                "WS" -&gt; mProtocol.check(R.id.protocol_ws)
                "WSS" -&gt; mProtocol.check(R.id.protocol_wss)
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
        mTilHost = view.findViewById(R.id.til_host)
        mTilPort = view.findViewById(R.id.til_port)
        mTilPath = view.findViewById(R.id.til_path)
        mTilClientId = view.findViewById(R.id.til_clientid)
        mTilUsername = view.findViewById(R.id.til_username)
        mTilPassword = view.findViewById(R.id.til_password)
        mProtocol = view.findViewById(R.id.protocol)
        mButton = view.findViewById(R.id.btn_connect)
        mDisconnectButton = view.findViewById(R.id.btn_disconnect)
        mLogText = view.findViewById(R.id.log_text)
        mAutoConnect = view.findViewById(R.id.auto_connect_switch)
        mTtsSwitch = view.findViewById(R.id.tts_switch)
        mFloatSwitch = view.findViewById(R.id.float_switch)
        mVoiceSwitch = view.findViewById(R.id.voice_switch)
        mAutoStartSwitch = view.findViewById(R.id.auto_start_switch)
        mNotificationSwitch = view.findViewById(R.id.notification_switch)
        mShowDebugLogSwitch = view.findViewById(R.id.show_debug_log_switch)
        mDebugLogContainer = view.findViewById(R.id.debug_log_container)

        if (mClientId.text.isNullOrEmpty()) {
            mClientId.setText(MqttAsyncClient.generateClientId())
            originalClientId = mClientId.text.toString()
        }

        (activity as? MainActivity)?.setLogCallback { message -&gt;
            appendLog(message)
        }

        appendLog("=== MQTT Setting ===")
        appendLog("Fragment initialized")

        setupPrivacyToggle(mTilHost, mHost, originalHost, isHostPrivacy) { privacy, original -&gt;
            isHostPrivacy = privacy
            originalHost = original
        }
        setupPrivacyToggle(mTilPort, mPort, originalPort, isPortPrivacy) { privacy, original -&gt;
            isPortPrivacy = privacy
            originalPort = original
        }
        setupPrivacyToggle(mTilPath, mPath, originalPath, isPathPrivacy) { privacy, original -&gt;
            isPathPrivacy = privacy
            originalPath = original
        }
        setupPrivacyToggle(mTilClientId, mClientId, originalClientId, isClientIdPrivacy) { privacy, original -&gt;
            isClientIdPrivacy = privacy
            originalClientId = original
        }
        setupPrivacyToggle(mTilUsername, mUsername, originalUsername, isUsernamePrivacy) { privacy, original -&gt;
            isUsernamePrivacy = privacy
            originalUsername = original
        }
        setupPrivacyToggle(mTilPassword, mPassword, originalPassword, isPasswordPrivacy) { privacy, original -&gt;
            isPasswordPrivacy = privacy
            originalPassword = original
        }

        loadSavedConfig()

        val mainActivity = (activity as? MainActivity)
        mainActivity?.let {
            it.isTTSEnabled = mTtsSwitch.isChecked
            it.isFloatWindowEnabled = mFloatSwitch.isChecked
            it.isAutoCaptureVoiceEnabled = mVoiceSwitch.isChecked
        }

        mTtsSwitch.setOnCheckedChangeListener { _, isChecked -&gt;
            (activity as? MainActivity)?.let { main -&gt;
                main.isTTSEnabled = isChecked
                appendLog("TTS ${if (isChecked) "enabled" else "disabled"}")
            }
        }
        mFloatSwitch.setOnCheckedChangeListener { _, isChecked -&gt;
            (activity as? MainActivity)?.let { main -&gt;
                main.isFloatWindowEnabled = isChecked
                appendLog("Float Window ${if (isChecked) "enabled" else "disabled"}")
            }
        }
        mVoiceSwitch.setOnCheckedChangeListener { _, isChecked -&gt;
            CapturedTextManager.isEnabled = isChecked
            (activity as? MainActivity)?.let { main -&gt;
                main.isAutoCaptureVoiceEnabled = isChecked
                appendLog("Auto Capture Voice ${if (isChecked) "enabled" else "disabled"}")
                if (isChecked &amp;&amp; !main.isAccessibilityServiceEnabled()) {
                    main.requestAccessibilityService()
                }
            }
        }

        mAutoStartSwitch.setOnCheckedChangeListener { _, isChecked -&gt;
            mConfigManager.autoStart = isChecked
            appendLog("Auto Start ${if (isChecked) "enabled" else "disabled"}")
        }

        mNotificationSwitch.setOnCheckedChangeListener { _, isChecked -&gt;
            mConfigManager.persistentNotification = isChecked
            appendLog("Persistent Notification ${if (isChecked) "enabled" else "disabled"}")
        }

        mShowDebugLogSwitch.setOnCheckedChangeListener { _, isChecked -&gt;
            mDebugLogContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            appendLog("Show Debug Log ${if (isChecked) "enabled" else "disabled"}")
        }

        (activity as? MainActivity)?.let { main -&gt;
            main.setOnMqttStatusChangedListener { connected -&gt;
                activity?.runOnUiThread {
                    updateConnectionStatus(connected)
                }
            }
            if (main.mClient?.isConnected == true) {
                updateConnectionStatus(true)
            } else {
                updateConnectionStatus(false)
            }
        }

        mProtocol.setOnCheckedChangeListener { _, checkedId -&gt;
            val port = when (checkedId) {
                R.id.protocol_tcp -&gt; 1883
                R.id.protocol_ssl -&gt; 8883
                R.id.protocol_ws -&gt; 8083
                R.id.protocol_wss -&gt; 443
                else -&gt; 1883
            }
            originalPort = port.toString()
            mPort.setText(if (isPortPrivacy &amp;&amp; originalPort.isNotEmpty()) maskText(originalPort) else originalPort)

            val pathVisibility = when (checkedId) {
                R.id.protocol_ws, R.id.protocol_wss -&gt; View.VISIBLE
                else -&gt; View.GONE
            }
            mPath.visibility = pathVisibility
        }

        view.findViewById&lt;Button&gt;(R.id.test_tts).setOnClickListener {
            appendLog("=== TTS测试 ===")
            testTts()
        }

        view.findViewById&lt;Button&gt;(R.id.test_popup).setOnClickListener {
            appendLog("=== 弹窗测试 ===")
            testPopup()
        }

        view.findViewById&lt;Button&gt;(R.id.test_overlay_permission).setOnClickListener {
            appendLog("=== 申请悬浮窗权限 ===")
            requestOverlayPermission()
        }

        mButton.setOnClickListener {
            if (mButton.text.toString() == getString(R.string.connect)) {
                val protocolName = when (mProtocol.checkedRadioButtonId) {
                    R.id.protocol_tcp -&gt; "TCP"
                    R.id.protocol_ssl -&gt; "SSL"
                    R.id.protocol_ws -&gt; "WS"
                    R.id.protocol_wss -&gt; "WSS"
                    else -&gt; "TCP"
                }

                appendLog("=== Starting Connection ===")
                appendLog("Protocol: $protocolName")
                appendLog("Host: $originalHost")
                appendLog("Port: $originalPort")
                appendLog("Path: $originalPath")
                appendLog("ClientId: $originalClientId")
                appendLog("Username: $originalUsername")
                appendLog("TTS: ${mTtsSwitch.isChecked}, Float: ${mFloatSwitch.isChecked}")

                saveCurrentConfig()

                val connection = Connection(
                    fragmentActivity!!,
                    originalHost,
                    originalPort.toInt(),
                    originalClientId,
                    originalUsername,
                    originalPassword,
                    protocolName,
                    originalPath
                )
                appendLog("Calling connect()...")

                (fragmentActivity as MainActivity).connect(
                    connection,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken) {
                            appendLog("=== CONNECT SUCCESS ===")
                            updateConnectionStatus(true)
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
                            updateConnectionStatus(false)
                        }
                    })
            }
        }

        mDisconnectButton.setOnClickListener {
            appendLog("Disconnect button clicked")
            (fragmentActivity as MainActivity).disconnect()
            stopNotificationService()
            updateConnectionStatus(false)
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
        (fragmentActivity as? MainActivity)?.let { main -&gt;
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
        if (Build.VERSION.SDK_INT &gt;= Build.VERSION_CODES.M) {
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

    private fun updateConnectionStatus(connected: Boolean) {
        if (connected) {
            mButton.text = "Connected"
            mButton.isEnabled = false
            mDisconnectButton.isEnabled = true
            updateNotificationStatus("MQTT Connected", "$originalHost:$originalPort")
        } else {
            mButton.text = getString(R.string.connect)
            mButton.isEnabled = true
            mDisconnectButton.isEnabled = false
            updateNotificationStatus("MQTT Disconnected", "Not connected")
        }
    }

    private fun updateNotificationStatus(title: String, message: String) {
        MqttService.updateStatus(requireContext(), title, message)
    }

    private fun startNotificationService() {
        if (Build.VERSION.SDK_INT &gt;= Build.VERSION_CODES.TIRAMISU) {
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
        updateNotificationStatus("MQTT Connected", "$originalHost:$originalPort")
    }

    private fun stopNotificationService() {
        MqttService.stopService(requireContext())
    }

    fun updateButtonText() {
        if ((fragmentActivity as? MainActivity)?.notConnected(false) == true) {
            mButton.text = getText(R.string.connect)
            mButton.isEnabled = true
            mDisconnectButton.isEnabled = false
        } else {
            mButton.text = getString(R.string.disconnect)
            mButton.isEnabled = false
            mDisconnectButton.isEnabled = true
        }
    }

    companion object {
        fun newInstance(): SettingFragment {
            return SettingFragment()
        }
    }
}