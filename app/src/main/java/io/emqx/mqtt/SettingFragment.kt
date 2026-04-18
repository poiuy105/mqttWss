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
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import android.text.Editable
import android.text.TextWatcher
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
    private lateinit var mProtocol: RadioGroup
    private lateinit var mButton: Button
    private lateinit var mDisconnectButton: Button
    private lateinit var mAutoConnect: Switch
    private lateinit var mTtsSwitch: Switch
    private lateinit var mFloatSwitch: Switch
    private lateinit var mVoiceSwitch: Switch
    private lateinit var mAutoStartSwitch: Switch
    private lateinit var mNotificationSwitch: Switch
    // Debug Log 已移至 MainActivity 主页面
    private lateinit var mAllowUntrustedCheckbox: Switch
    private lateinit var mSslUntrustedContainer: LinearLayout
    private lateinit var mHaAddress: EditText
    private lateinit var mHaToken: EditText
    private lateinit var mHaLanguage: EditText
    private lateinit var mHaHttpsCheckbox: Switch
    private lateinit var mConfigManager: ConfigManager

    private val logBuilder = StringBuilder()

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
            logBuilder.insert(0, "[$timestamp] $message\n")
            if (logBuilder.length > 2000) {
                logBuilder.setLength(2000)
            }
            // 通过回调将日志传递到MainActivity显示（Debug Log容器已在主页面）
            Log.d("SettingFragment", message)
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
            protocol = protocolName,
            allowUntrusted = mAllowUntrustedCheckbox.isChecked,
            haAddress = mHaAddress.text.toString(),
            haToken = mHaToken.text.toString(),
            haLanguage = mHaLanguage.text.toString(),
            haHttps = mHaHttpsCheckbox.isChecked
        )
        mConfigManager.autoConnect = mAutoConnect.isChecked
        mConfigManager.autoStart = mAutoStartSwitch.isChecked
        mConfigManager.persistentNotification = mNotificationSwitch.isChecked
    }

    private fun loadSavedConfig() {
        // 恢复MQTT连接字段
        if (mConfigManager.hasSavedConfig()) {
            mHost.setText(mConfigManager.host)
            mPort.setText(mConfigManager.port.toString())
            mPath.setText(mConfigManager.path)
            mClientId.setText(mConfigManager.clientId)
            mUsername.setText(mConfigManager.username)
            mPassword.setText(mConfigManager.password)
        }
        
        // 恢复所有开关和选项（即使没有完整配置也要恢复）
        mAutoConnect.isChecked = mConfigManager.autoConnect
        mAutoStartSwitch.isChecked = mConfigManager.autoStart
        mNotificationSwitch.isChecked = mConfigManager.persistentNotification
        mAllowUntrustedCheckbox.isChecked = mConfigManager.allowUntrusted
        
        // 恢复HA字段
        mHaAddress.setText(mConfigManager.haAddress)
        mHaToken.setText(mConfigManager.haToken)
        mHaLanguage.setText(mConfigManager.haLanguage)
        mHaHttpsCheckbox.isChecked = mConfigManager.haHttps

        // 恢复协议选择
        when (mConfigManager.protocol) {
            "TCP" -> mProtocol.check(R.id.protocol_tcp)
            "SSL" -> mProtocol.check(R.id.protocol_ssl)
            "WS" -> mProtocol.check(R.id.protocol_ws)
            "WSS" -> mProtocol.check(R.id.protocol_wss)
        }

        // 恢复功能开关（从持久化存储）
        mTtsSwitch.isChecked = mConfigManager.ttsEnabled
        mFloatSwitch.isChecked = mConfigManager.floatWindowEnabled
        mVoiceSwitch.isChecked = mConfigManager.voiceCaptureEnabled

        appendLog("Loaded saved configuration")
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
        mDisconnectButton = view.findViewById(R.id.btn_disconnect)
        mAutoConnect = view.findViewById(R.id.auto_connect_switch)
        mTtsSwitch = view.findViewById(R.id.tts_switch)
        mFloatSwitch = view.findViewById(R.id.float_switch)
        mVoiceSwitch = view.findViewById(R.id.voice_switch)
        mAutoStartSwitch = view.findViewById(R.id.auto_start_switch)
        mNotificationSwitch = view.findViewById(R.id.notification_switch)
        mAllowUntrustedCheckbox = view.findViewById(R.id.allow_untrusted_checkbox)
        mSslUntrustedContainer = view.findViewById(R.id.ssl_untrusted_container)
        mHaAddress = view.findViewById(R.id.ha_address)
        mHaToken = view.findViewById(R.id.ha_token)
        mHaLanguage = view.findViewById(R.id.ha_language)
        mHaHttpsCheckbox = view.findViewById(R.id.ha_https_checkbox)

        if (mClientId.text.isNullOrEmpty()) {
            mClientId.setText(MqttAsyncClient.generateClientId())
        }

        (activity as? MainActivity)?.setLogCallback { message ->
            appendLog(message)
        }

        appendLog("=== MQTT Setting ===")
        appendLog("Fragment initialized")

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

            val sslUntrustedVisibility = when (checkedId) {
                R.id.protocol_ssl -> View.VISIBLE
                else -> View.GONE
            }
            mSslUntrustedContainer.visibility = sslUntrustedVisibility

            // 实时保存协议选择
            val protoName = when (checkedId) {
                R.id.protocol_tcp -> "TCP"
                R.id.protocol_ssl -> "SSL"
                R.id.protocol_ws -> "WS"
                R.id.protocol_wss -> "WSS"
                else -> "TCP"
            }
            mConfigManager.protocol = protoName
        }

        loadSavedConfig()

        // ========== MQTT连接字段 - 实时保存（输入即持久化）==========
        mHost.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { mConfigManager.host = s?.toString() ?: "" }
        })
        mPort.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { mConfigManager.port = s?.toString()?.toIntOrNull() ?: 1883 }
        })
        mPath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { mConfigManager.path = s?.toString() ?: "/mqtt" }
        })
        mClientId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { mConfigManager.clientId = s?.toString() ?: "" }
        })
        mUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { mConfigManager.username = s?.toString() ?: "" }
        })
        mPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { mConfigManager.password = s?.toString() ?: "" }
        })
        mAllowUntrustedCheckbox.setOnCheckedChangeListener { _, isChecked ->
            mConfigManager.allowUntrusted = isChecked
            appendLog("Allow Untrusted ${if (isChecked) "enabled" else "disabled"}")
        }
        mAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            mConfigManager.autoConnect = isChecked
            appendLog("Auto Connect ${if (isChecked) "enabled" else "disabled"}")
            // Auto Connect打开时锁定所有MQTT连接参数（防止修改后无法匹配已保存配置）
            updateMqttControlsEnabled(!isChecked)
        }
        // 初始状态：根据当前MQTT连接状态和autoConnect值决定控件可用性
        val mainActivity = (activity as? MainActivity)
        val isConnected = mainActivity?.mClient?.isConnected == true
        mAutoConnect.isEnabled = isConnected
        if (!isConnected) {
            mAutoConnect.alpha = 0.5f
        } else {
            mAutoConnect.alpha = 1.0f
        }
        // 初始化时也应用autoConnect对控件的锁定状态
        updateMqttControlsEnabled(!mAutoConnect.isChecked)

        // ========== HA容器控件实时保存 ==========
        mHaAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { mConfigManager.haAddress = s?.toString() ?: "" }
        })
        mHaToken.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { mConfigManager.haToken = s?.toString() ?: "" }
        })
        mHaLanguage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { mConfigManager.haLanguage = s?.toString() ?: "" }
        })
        mHaHttpsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            mConfigManager.haHttps = isChecked
            appendLog("HA HTTPS ${if (isChecked) "enabled" else "disabled"}")
        }

        // ========== 功能开关 - 实时持久化 + 同步MainActivity ==========

        // 恢复后立即同步到MainActivity运行时状态（复用上面已声明的mainActivity变量）
        mainActivity?.let {
            it.isTTSEnabled = mTtsSwitch.isChecked
            it.isFloatWindowEnabled = mFloatSwitch.isChecked
            it.isAutoCaptureVoiceEnabled = mVoiceSwitch.isChecked
        }

        mTtsSwitch.setOnCheckedChangeListener { _, isChecked ->
            mConfigManager.ttsEnabled = isChecked
            (activity as? MainActivity)?.isTTSEnabled = isChecked
            appendLog("TTS ${if (isChecked) "enabled" else "disabled"}")
        }
        mFloatSwitch.setOnCheckedChangeListener { _, isChecked ->
            mConfigManager.floatWindowEnabled = isChecked
            (activity as? MainActivity)?.isFloatWindowEnabled = isChecked
            appendLog("Float Window ${if (isChecked) "enabled" else "disabled"}")
        }
        mVoiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            CapturedTextManager.init(requireContext())
            CapturedTextManager.isEnabled = isChecked
            mConfigManager.voiceCaptureEnabled = isChecked
            (activity as? MainActivity)?.isAutoCaptureVoiceEnabled = isChecked
            appendLog("Auto Capture Voice ${if (isChecked) "enabled" else "disabled"}")
            if (isChecked && (activity as? MainActivity)?.isAccessibilityServiceEnabled() == false) {
                (activity as? MainActivity)?.requestAccessibilityService()
            }
        }

        mAutoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            mConfigManager.autoStart = isChecked
            appendLog("Auto Start ${if (isChecked) "enabled" else "disabled"}")
        }

        mNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            mConfigManager.persistentNotification = isChecked
            appendLog("Persistent Notification ${if (isChecked) "enabled" else "disabled"}")
        }

        (activity as? MainActivity)?.let { main ->
            main.setOnMqttStatusChangedListener { connected ->
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

        view.findViewById<Button>(R.id.test_tts).setOnClickListener {
            appendLog("=== TTS测试 ===")
            testTts()
        }

        view.findViewById<Button>(R.id.test_popup).setOnClickListener {
            appendLog("=== 弹窗测试 ===")
            testPopup()
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
                appendLog("TTS: ${mTtsSwitch.isChecked}, Float: ${mFloatSwitch.isChecked}")

                saveCurrentConfig()

                val connection = Connection(
                    fragmentActivity!!,
                    mHost.text.toString(),
                    mPort.text.toString().toInt(),
                    mClientId.text.toString(),
                    mUsername.text.toString(),
                    mPassword.text.toString(),
                    protocolName,
                    mPath.text.toString(),
                    mAllowUntrustedCheckbox.isChecked
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

    /**
     * 统一控制MQTT连接参数区域内所有控件的启用/禁用状态
     * Auto Connect打开时，所有参数应被锁定（防止修改后无法匹配已保存配置）
     * @param enabled true=可操作, false=禁用(半透明)
     */
    private fun updateMqttControlsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f
        // 协议选择
        mProtocol.alpha = alpha
        mProtocol.isEnabled = enabled
        // SSL选项
        mAllowUntrustedCheckbox.alpha = alpha
        mAllowUntrustedCheckbox.isEnabled = enabled
        // 连接参数输入框
        mHost.alpha = alpha; mHost.isEnabled = enabled
        mPort.alpha = alpha; mPort.isEnabled = enabled
        mPath.alpha = alpha; mPath.isEnabled = enabled
        mClientId.alpha = alpha; mClientId.isEnabled = enabled
        mUsername.alpha = alpha; mUsername.isEnabled = enabled
        mPassword.alpha = alpha; mPassword.isEnabled = enabled
    }

    private fun updateConnectionStatus(connected: Boolean) {
        if (connected) {
            mButton.text = "Connected"
            mButton.isEnabled = false
            mDisconnectButton.isEnabled = true
            // MQTT已连接：允许操作Auto Connect开关
            mAutoConnect.isEnabled = true
            mAutoConnect.alpha = 1.0f
            updateNotificationStatus("MQTT Connected", "${mConfigManager.host}:${mConfigManager.port}")
        } else {
            mButton.text = getString(R.string.connect)
            mButton.isEnabled = true
            mDisconnectButton.isEnabled = false
            // MQTT未连接：禁用Auto Connect开关（必须先手动连接成功才能启用）
            mAutoConnect.isEnabled = false
            mAutoConnect.alpha = 0.5f
            updateNotificationStatus("MQTT Disconnected", "Not connected")
        }
    }

    private fun updateNotificationStatus(title: String, message: String) {
        MqttService.updateStatus(requireContext(), title, message)
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
        updateNotificationStatus("MQTT Connected", "${mConfigManager.host}:${mConfigManager.port}")
    }

    private fun stopNotificationService() {
        MqttService.stopService(requireContext())
    }

    fun updateButtonText() {
        // 防御性检查: Fragment View 可能尚未创建或已销毁(如recreate()/横竖屏切换后)
        // lateinit属性(mButton/mDisconnectButton)在View未inflate前访问会抛UninitializedPropertyAccessException
        if (!this::mButton.isInitialized) {
            Log.w("SettingFragment", "updateButtonText called but mButton not initialized, skipping")
            return
        }
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
