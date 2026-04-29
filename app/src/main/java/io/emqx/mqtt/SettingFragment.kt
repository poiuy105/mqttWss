package io.emqx.mqtt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
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
    private lateinit var mTtsSwitch: Switch
    private lateinit var mFloatSwitch: Switch
    private lateinit var mVoiceSwitch: Switch
    private lateinit var mAutoStartSwitch: Switch
    private lateinit var mNotificationSwitch: Switch
    // Debug Log 已移至 MainActivity 主页面
    private lateinit var mAllowUntrustedCheckbox: Switch
    private lateinit var mSslUntrustedContainer: LinearLayout
    private lateinit var mPathContainer: LinearLayout  // 横屏时path外层容器
    private lateinit var mHaAddress: EditText
    private lateinit var mHaToken: EditText
    private lateinit var mHaLanguage: EditText
    private lateinit var mHaHttpsCheckbox: Switch
    private lateinit var mHaResponseDelaySeekbar: SeekBar
    private lateinit var mHaResponseDelayValue: TextView
    private lateinit var mHaClickBackSwitch: Switch
    private lateinit var mHaClickCount: EditText
    private lateinit var mConfigManager: ConfigManager
    // 车机保活按钮
    private lateinit var mAdbGuideButton: Button
    private lateinit var mBydWhitelistButton: Button
    private lateinit var mBatteryOptButton: Button
    private lateinit var mAutostartButton: Button
    // 密码可见性切换按钮
    private lateinit var mBtnTogglePasswordVisibility: ImageButton
    private var isPasswordVisible = false  // 默认密文模式（闭眼）

    // ========== 云端TTS设置控件 ==========
    private var mCloudTtsApiSpinner: Spinner? = null
    private var mBtnTtsTestCloud: Button? = null
    private var mBtnTtsResetDefault: Button? = null
    // ⭐ 新增：TTS速度和音量拖动条
    private var mTtsSpeedSeekbar: SeekBar? = null
    private var mTtsSpeedValue: TextView? = null
    private var mTtsVolumeSeekbar: SeekBar? = null
    private var mTtsVolumeValue: TextView? = null
    // ⭐ 新增：管理Edge-TTS缓存按钮
    private var mBtnManageEdgeTtsCache: Button? = null

    // 标志位：区分是用户手动切换协议还是加载配置
    private var isInitializing = true

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
            haHttps = mHaHttpsCheckbox.isChecked,
            haResponseDelay = mConfigManager.haResponseDelay,
            haClickBackEnabled = mHaClickBackSwitch.isChecked,
            haClickCount = mConfigManager.haClickCount
        )
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
        mAutoStartSwitch.isChecked = mConfigManager.autoStart
        mNotificationSwitch.isChecked = mConfigManager.persistentNotification
        mAllowUntrustedCheckbox.isChecked = mConfigManager.allowUntrusted
        
        // 恢复HA字段
        mHaAddress.setText(mConfigManager.haAddress)
        mHaToken.setText(mConfigManager.haToken)
        mHaLanguage.setText(mConfigManager.haLanguage)
        mHaHttpsCheckbox.isChecked = mConfigManager.haHttps
        
        // 恢复HA响应延迟设置（默认50ms，范围10-2000ms）
        val delayValue = mConfigManager.haResponseDelay.coerceIn(10, 2000)
        mHaResponseDelaySeekbar.progress = delayValue - 10  // SeekBar从0开始，对应10ms
        mHaResponseDelayValue.text = "${delayValue}ms"
        
        // 恢复HA单击替代返回开关
        mHaClickBackSwitch.isChecked = mConfigManager.haClickBackEnabled
        
        // 恢复HA点击次数设置（默认3次）
        mHaClickCount.setText(mConfigManager.haClickCount.toString())

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
        
        // 配置加载完成，允许用户手动切换协议时自动设置端口
        isInitializing = false
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
        mTtsSwitch = view.findViewById(R.id.tts_switch)
        mFloatSwitch = view.findViewById(R.id.float_switch)
        mVoiceSwitch = view.findViewById(R.id.voice_switch)
        mAutoStartSwitch = view.findViewById(R.id.auto_start_switch)
        mNotificationSwitch = view.findViewById(R.id.notification_switch)
        mAllowUntrustedCheckbox = view.findViewById(R.id.allow_untrusted_checkbox)
        mSslUntrustedContainer = view.findViewById(R.id.ssl_untrusted_container)
        mPathContainer = view.findViewById(R.id.path_container)  // 横屏path外层容器（竖屏时为null）
        mHaAddress = view.findViewById(R.id.ha_address)
        mHaToken = view.findViewById(R.id.ha_token)
        mHaLanguage = view.findViewById(R.id.ha_language)
        mHaHttpsCheckbox = view.findViewById(R.id.ha_https_checkbox)
        mHaResponseDelaySeekbar = view.findViewById(R.id.ha_response_delay_seekbar)
        mHaResponseDelayValue = view.findViewById(R.id.ha_response_delay_value)
        mHaClickBackSwitch = view.findViewById(R.id.ha_click_back_switch)
        mHaClickCount = view.findViewById(R.id.ha_click_count)
        mBtnTogglePasswordVisibility = view.findViewById(R.id.btn_toggle_password_visibility)

        // ========== 车机保活按钮初始化 ==========
        mAdbGuideButton = view.findViewById(R.id.btn_adb_guide)
        mBydWhitelistButton = view.findViewById(R.id.btn_byd_whitelist)
        mBatteryOptButton = view.findViewById(R.id.btn_battery_opt)
        mAutostartButton = view.findViewById(R.id.btn_autostart)

        // ========== 云端TTS设置控件初始化 ==========
        mCloudTtsApiSpinner = view.findViewById(R.id.cloud_tts_api_spinner)
        mBtnTtsTestCloud = view.findViewById(R.id.btn_tts_test_cloud)
        mBtnTtsResetDefault = view.findViewById(R.id.btn_tts_reset_default)
        // ⭐ 新增：初始化TTS速度和音量拖动条
        mTtsSpeedSeekbar = view.findViewById(R.id.tts_speed_seekbar)
        mTtsSpeedValue = view.findViewById(R.id.tts_speed_value)
        mTtsVolumeSeekbar = view.findViewById(R.id.tts_volume_seekbar)
        mTtsVolumeValue = view.findViewById(R.id.tts_volume_value)
        // ⭐ 新增：初始化管理Edge-TTS缓存按钮
        mBtnManageEdgeTtsCache = view.findViewById(R.id.btn_manage_edge_tts_cache)

        if (mClientId.text.isNullOrEmpty()) {
            mClientId.setText(MqttAsyncClient.generateClientId())
        }

        (activity as? MainActivity)?.setLogCallback { message ->
            appendLog(message)
        }

        appendLog("=== MQTT Setting ===")
        appendLog("Fragment initialized")

        mProtocol.setOnCheckedChangeListener { _, checkedId ->
            // 只在用户手动切换协议时才自动设置默认端口
            // 加载配置时不覆盖用户已保存的自定义端口
            if (!isInitializing) {
                val port = when (checkedId) {
                    R.id.protocol_tcp -> 1883
                    R.id.protocol_ssl -> 8883
                    R.id.protocol_ws -> 8083
                    R.id.protocol_wss -> 443
                    else -> 1883
                }
                mPort.setText(port.toString())
            }

            // Path输入框：WS/WSS时显示，TCP/SSL时隐藏
            // 注意：只改变可见性，不会清除已保存的Path值
            val pathVisibility = when (checkedId) {
                R.id.protocol_ws, R.id.protocol_wss -> View.VISIBLE
                else -> View.GONE
            }
            mPath.visibility = pathVisibility
            // 横屏模式：path的外层LinearLayout容器也需要同步可见性
            if (::mPathContainer.isInitialized) {
                try { mPathContainer.visibility = pathVisibility } catch (e: Exception) {}  // 竖屏布局无此容器，忽略
            }

            // Untrusted开关：仅SSL时显示
            // 注意：只改变可见性，不会清除已保存的Untrusted值
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
            
            appendLog("Protocol changed to: $protoName")
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
        
        // HA响应延迟设置 - SeekBar监听器（10ms-2000ms）
        mHaResponseDelaySeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val delayValue = (progress + 10).coerceIn(10, 2000)
                mHaResponseDelayValue.text = "${delayValue}ms"
                if (fromUser) {
                    mConfigManager.haResponseDelay = delayValue
                    appendLog("HA Response Delay set to ${delayValue}ms")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // HA单击替代返回开关
        mHaClickBackSwitch.setOnCheckedChangeListener { _, isChecked ->
            mConfigManager.haClickBackEnabled = isChecked
            appendLog("HA Click Back ${if (isChecked) "enabled" else "disabled"}")
        }
        
        // HA点击次数设置 - 实时保存
        mHaClickCount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val clickCount = s?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                mConfigManager.haClickCount = clickCount
            }
        })

        // ========== 功能开关 - 实时持久化 + 同步MainActivity ==========

        val mainActivity = (activity as? MainActivity)
        // 恢复后立即同步到MainActivity运行时状态
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

        // test_tts 按钮已移除（本地TTS调试区域已替换为云端TTS设置）

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

        // ========== 车机保活设置按钮 ==========
        mAdbGuideButton.setOnClickListener {
            showAdbGuideDialog()
        }
        mBydWhitelistButton.setOnClickListener {
            appendLog("跳转比亚迪极速模式白名单")
            BydPermitUtils.jumpToSpeedWhiteList(requireContext())
        }
        mBatteryOptButton.setOnClickListener {
            appendLog("跳转电池优化设置")
            BydPermitUtils.jumpToBatteryOptimization(requireContext())
        }
        mAutostartButton.setOnClickListener {
            appendLog("跳转自启动管理")
            // 使用新的BootUtils工具类
            BootUtils.openAutoStartSettings(requireContext())
        }

        // ========== 云端TTS设置 ==========
        setupCloudTtsSettings()

        // ========== 密码可见性切换按钮 ==========
        mBtnTogglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(isPasswordVisible)
            appendLog("Password visibility: ${if (isPasswordVisible) "visible" else "hidden"}")
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


    /** 获取云端TTS播放器实例 */
    private fun getCloudTtsPlayer(): CloudTTSPlayer? {
        return (fragmentActivity as? MainActivity)?.ttsPlayer
    }

    /**
     * 初始化云端TTS设置UI（接口选择、音色、语速/音调/音量滑块、测试按钮）
     */
    private fun setupCloudTtsSettings() {
        val player = getCloudTtsPlayer() ?: run {
            appendLog("[CloudTTS] CloudTTSPlayer未初始化")
            return
        }

        // ⭐ 扫描所有可用的TTS引擎
        val engines = player.scanAvailableEngines(requireContext())
        val engineNames = player.getAvailableEngineNames()
        
        appendLog("[CloudTTS] Found ${engines.count { it.isLocal }} local + 3 cloud TTS engines")

        // 1. 接口选择 Spinner（动态显示所有引擎）
        val apiAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            engineNames
        )
        apiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mCloudTtsApiSpinner?.adapter = apiAdapter

        // 设置默认选择
        mCloudTtsApiSpinner?.setSelection(player.currentEngineIndex)
        
        mCloudTtsApiSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                player.setCurrentEngine(position, requireContext())
                mConfigManager.cloudTtsApiIndex = position
                
                val engine = engines[position]
                if (engine.isLocal) {
                    appendLog("[CloudTTS] 切换到本地TTS: ${engine.name}")
                } else {
                    appendLog("[CloudTTS] 切换到云端TTS: ${engine.name}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 测试按钮
        mBtnTtsTestCloud?.setOnClickListener {
            val testText = "你好，这是语音合成测试。当前车速35公里，电量75%。"
            
            val engine = engines.getOrNull(player.currentEngineIndex)
            
            if (engine?.isLocal == true) {
                // 本地TTS
                appendLog("[CloudTTS] 🎯 Testing Local TTS: ${engine.name}")
                
                if (player.isCurrentTTSReady()) {
                    appendLog("[CloudTTS] ✅ Local TTS is READY, speaking...")
                    Toast.makeText(context, "使用本地TTS播报", Toast.LENGTH_SHORT).show()
                    player.speakByCurrentEngine(testText, force = true)
                } else {
                    appendLog("[CloudTTS] ⚠️ Local TTS NOT ready, please wait...")
                    Toast.makeText(context, "TTS初始化中，请稍候...", Toast.LENGTH_LONG).show()
                    
                    // 延迟3秒后重试
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (player.isCurrentTTSReady()) {
                            appendLog("[CloudTTS] ✅ Retry success, speaking...")
                            player.speakByCurrentEngine(testText, force = true)
                        } else {
                            appendLog("[CloudTTS] ❌ Local TTS still NOT ready")
                            Toast.makeText(context, "TTS初始化失败", Toast.LENGTH_LONG).show()
                        }
                    }, 3000)
                }
            } else {
                // 云端TTS
                appendLog("[CloudTTS] ☁️ Testing Cloud TTS: ${engine?.name}")
                Toast.makeText(context, "使用云端TTS", Toast.LENGTH_SHORT).show()
                player.speakByCurrentEngine(testText, force = true)
            }
        }

        // 7. 重置默认按钮
        mBtnTtsResetDefault?.setOnClickListener {
            player.resetToDefaults()
            // 同步ConfigManager
            mConfigManager.cloudTtsApiIndex = player.currentApiIndex
            mConfigManager.cloudTtsVoice = player.voice
            mConfigManager.cloudTtsSpeed = player.speed
            // pitch是String格式如"+0Hz"，解析为近似float存储
            try {
                val pitchVal = player.pitch.replace("[^\\-+\\d]".toRegex(), "").toInt()
                mConfigManager.cloudTtsPitch = (pitchVal / 10f)
            } catch (e: Exception) {
                mConfigManager.cloudTtsPitch = 0f
            }
            mConfigManager.cloudTtsVolume = player.volume
            // 刷新UI
            mCloudTtsApiSpinner?.setSelection(player.currentApiIndex)
            appendLog("[CloudTTS] 已重置为默认配置: ${player.getCurrentApiName()}")
            Toast.makeText(context, "TTS settings reset to defaults", Toast.LENGTH_SHORT).show()
        }

        appendLog("[CloudTTS] 设置已加载: ${player.getCurrentApiName()}")
        
        // ⭐ 新增：设置TTS速度拖动条（0.5-3.0，step 0.5）
        setupTtsSpeedSeekbar(player)
        
        // ⭐ 新增：设置TTS音量拖动条（0.5-5.0，step 0.5）
        setupTtsVolumeSeekbar(player)
    }
    
    /**
     * ⭐ 设置TTS速度拖动条
     * 范围：0.5-3.0，步长0.5，默认1.0
     * SeekBar max=5，对应值 = (progress + 1) * 0.5
     */
    private fun setupTtsSpeedSeekbar(player: CloudTTSPlayer) {
        val currentSpeed = mConfigManager.cloudTtsSpeed
        
        // 将速度值转换为SeekBar进度：(speed / 0.5) - 1
        val initialProgress = ((currentSpeed / 0.5f) - 1).toInt().coerceIn(0, 5)
        
        mTtsSpeedSeekbar?.progress = initialProgress
        updateTtsSpeedDisplay(currentSpeed)
        
        mTtsSpeedSeekbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 计算速度值：(progress + 1) * 0.5
                    val speed = (progress + 1) * 0.5f
                    updateTtsSpeedDisplay(speed)
                    
                    // 保存到ConfigManager
                    mConfigManager.cloudTtsSpeed = speed
                    player.speed = speed
                    
                    appendLog("[CloudTTS] Speed changed to: ${speed}x")
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    /**
     * ⭐ 更新速度显示
     */
    private fun updateTtsSpeedDisplay(speed: Float) {
        mTtsSpeedValue?.text = String.format("%.1fx", speed)
    }
    
    /**
     * ⭐ 设置TTS音量拖动条
     * 范围：0.5-5.0，步长0.5，默认1.0
     * SeekBar max=9，对应值 = (progress + 1) * 0.5
     */
    private fun setupTtsVolumeSeekbar(player: CloudTTSPlayer) {
        val currentVolume = mConfigManager.cloudTtsVolume
        
        // 将音量值转换为SeekBar进度：(volume / 0.5) - 1
        val initialProgress = ((currentVolume / 0.5f) - 1).toInt().coerceIn(0, 9)
        
        mTtsVolumeSeekbar?.progress = initialProgress
        updateTtsVolumeDisplay(currentVolume)
        
        mTtsVolumeSeekbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 计算音量值：(progress + 1) * 0.5
                    val volume = (progress + 1) * 0.5f
                    updateTtsVolumeDisplay(volume)
                    
                    // 保存到ConfigManager
                    mConfigManager.cloudTtsVolume = volume
                    player.volume = volume
                    
                    appendLog("[CloudTTS] Volume changed to: $volume")
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // ⭐ 新增：管理Edge-TTS缓存按钮点击事件
        mBtnManageEdgeTtsCache?.setOnClickListener {
            showEdgeTtsCacheManager(player)
        }
    }
    
    /**
     * ⭐ 更新音量显示
     */
    private fun updateTtsVolumeDisplay(volume: Float) {
        mTtsVolumeValue?.text = String.format("%.1f", volume)
    }
    
    /**
     * ⭐ 显示Edge-TTS缓存管理对话框
     */
    private fun showEdgeTtsCacheManager(player: CloudTTSPlayer) {
        val history = player.getRecentSpeakHistory()
        
        if (history.isEmpty()) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Edge-TTS播报历史")
                .setMessage("暂无播报记录。\n\n播报Edge-TTS音频后，这里会显示最近50条记录。")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        
        // 构建历史记录列表
        val items = history.map { record ->
            val statusIcon = if (record.isSuccess) "✅" else "❌"
            val timeStr = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(record.timestamp))
            "$statusIcon ${record.text}\n   $timeStr"
        }.toTypedArray()
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Edge-TTS播报历史（最近${history.size}条）")
            .setItems(items) { _, which ->
                val record = history[which]
                showCacheItemOptions(player, record)
            }
            .setNegativeButton("关闭", null)
            .setNeutralButton("清除全部") { _, _ ->
                player.clearEdgeTtsCache()
                appendLog("[Edge-TTS] All cache cleared")
            }
            .show()
    }
    
    /**
     * ⭐ 显示单个缓存项的操作选项
     */
    private fun showCacheItemOptions(player: CloudTTSPlayer, record: CloudTTSPlayer.SpeakRecord) {
        val options = arrayOf(
            "🗑️ 删除此条缓存",
            "📋 复制原文",
            "ℹ️ 查看详情"
        )
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("操作：${record.text.take(30)}...")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 删除缓存
                        val deleted = player.deleteCacheByText(record.text)
                        if (deleted) {
                            appendLog("[Edge-TTS] Deleted: ${record.text}")
                            android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "删除失败", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        // 复制原文
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("TTS Text", record.text)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // 查看详情
                        val detailMsg = "原文：${record.text}\n\n" +
                                "缓存Key：${record.cacheKey}\n\n" +
                                "时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(record.timestamp))}\n\n" +
                                "状态：${if (record.isSuccess) "✅ 成功" else "❌ 失败"}"
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("详细信息")
                            .setMessage(detailMsg)
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            }
            .show()
    }

    /** 从CloudTTSPlayer同步滑块值到UI显示 */
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
            updateNotificationStatus("MQTT Connected", "${mConfigManager.host}:${mConfigManager.port}")
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

    /**
     * 显示 ADB 无障碍永久化说明弹窗
     * 根据当前系统版本自动切换内容：
     *   - API 29 (Android 10): 基础版
     *   - API 30+ (Android 11+): 含「受限制设置」步骤
     *   - API 34 (Android 14): 含 pm grant WRITE_SECURE_SETTINGS 步骤
     *
     * 弹窗包含：可复制的完整命令 + 复制按钮
     */
    private fun showAdbGuideDialog() {
        val sdkInt = Build.VERSION.SDK_INT
        appendLog("显示ADB说明弹窗 (API $sdkInt)")

        // 根据版本生成说明文本和命令
        val (title, message, commands) = when {
            sdkInt >= 34 -> buildApi34Guide()
            sdkInt >= 30 -> buildApi30Guide()
            else -> buildApi29Guide()
        }

        // 构建带复制按钮的弹窗
        val context = requireContext()
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(350)
            )
        }
        
        // 使用垂直LinearLayout来组织内容
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }
        
        // 智能解析message，将ADB命令行为可点击项，普通文本为说明
        val lines = message.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            
            // 检查是否是ADB命令行（以adb开头）
            if (line.trim().startsWith("adb ")) {
                // 创建可点击的命令TextView
                val cmdView = TextView(context).apply {
                    text = line.trim()
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#0066CC"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                    background = context.getDrawable(android.R.drawable.editbox_background)
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(2)
                        bottomMargin = dpToPx(2)
                    }
                    
                    // 添加点击事件
                    setOnClickListener {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("ADB Command", text.toString())
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制: ${text.toString().take(30)}${if (text.toString().length > 30) "..." else ""}", Toast.LENGTH_SHORT).show()
                        appendLog("已复制ADB命令: $text")
                    }
                }
                contentLayout.addView(cmdView)
            } else {
                // 普通说明文本
                if (line.isNotBlank()) {
                    val textView = TextView(context).apply {
                        text = line
                        textSize = 13f
                        setTextColor(android.graphics.Color.parseColor("#333333"))
                        setTypeface(null, android.graphics.Typeface.NORMAL)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = if (line.startsWith("【") || line.startsWith("①") || line.startsWith("②") || line.startsWith("③") || line.startsWith("★")) dpToPx(8) else dpToPx(2)
                        }
                    }
                    contentLayout.addView(textView)
                } else {
                    // 空行，添加小间距
                    val spacer = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dpToPx(4)
                        )
                    }
                    contentLayout.addView(spacer)
                }
            }
            i++
        }
        
        scrollView.addView(contentLayout)

        // 使用AlertDialog构建弹窗（删除了中性按钮-超链接）
        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("📋 复制全部命令") { _, _ ->
                // 复制所有命令到剪贴板
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("ADB Commands", commands)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "已复制到剪贴板！\n请在电脑ADB中使用", Toast.LENGTH_LONG).show()
                appendLog("已复制ADB命令到剪贴板")
            }
            .setNegativeButton("关闭", null)
            .create()

        dialog.show()

        // 让正按钮文字颜色更醒目
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            context.resources.getColor(android.R.color.holo_blue_dark, null)
        )
    }
    /** Android 10 (API 29) 指南 */
    private fun buildApi29Guide(): Triple<String, String, String> {
        val title = "ADB 无障碍锁定 (Android 10)"
        val commands = buildString {
            appendLine("# 连接车机")
            appendLine("adb connect <车机IP>")
            appendLine("")
            appendLine("# 写入无障碍服务（核心）")
            appendLine("adb shell settings put secure enabled_accessibility_services io.emqx.mqtt/.VoiceAccessibilityService")
            appendLine("")
            appendLine("# 启用全局无障碍")
            appendLine("adb shell settings put secure accessibility_enabled 1")
        }
        val message = buildString {
            appendLine("🚗 当前系统：Android 10 (API ${Build.VERSION.SDK_INT})\n")
            appendLine("━━━ 操作步骤 ━━━\n")
            appendLine("① 在电脑上打开终端/命令行\n")
            appendLine("② 用USB线连接车机，或无线连接：")
            appendLine("   adb connect <车机IP>\n")
            appendLine("③ 依次执行以下命令：\n")
            appendLine("【命令1】写入无障碍服务组件名")
            appendLine("adb shell settings put secure enabled_accessibility_services io.emqx.mqtt/.VoiceAccessibilityService\n")
            appendLine("【命令2】启用全局无障碍开关")
            appendLine("adb shell settings put secure accessibility_enabled 1\n")
            appendLine("④ 验证是否成功：")
            appendLine("adb shell settings get secure enabled_accessibility_services\n")
            appendLine("   返回 io.emqx.mqtt/.VoiceAccessibilityService = ✅ 成功\n")
            appendLine("\n⚠️ 首次使用还需手动操作：")
            appendLine("• 车机 → 设置 → 无障碍 → 开启MQTT Assistant\n")
            appendLine("• 配置下方3项白名单（极速模式/电池优化/自启动）\n")
            appendLine("完成后重启测试，无障碍将永久保持开启！")
        }
        return Triple(title, message, commands)
    }

    /** Android 11-13 (API 30-33) 指南 - 含受限制设置 */
    private fun buildApi30Guide(): Triple<String, String, String> {
        val title = "ADB 无障碍锁定 (Android 11~13)"
        val commands = buildString {
            appendLine("# 连接车机")
            appendLine("adb connect <车机IP>")
            appendLine("")
            appendLine("# 授予写入权限")
            appendLine("adb shell pm grant io.emqx.mqtt android.permission.WRITE_SECURE_SETTINGS")
            appendLine("")
            appendLine("# 写入无障碍服务（核心）")
            appendLine("adb shell settings put secure enabled_accessibility_services io.emqx.mqtt/.VoiceAccessibilityService")
            appendLine("")
            appendLine("# 启用全局无障碍")
            appendLine("adb shell settings put secure accessibility_enabled 1")
        }
        val message = buildString {
            appendLine("🚗 当前系统：Android ${if (Build.VERSION.SDK_INT >= 33) "13" else "11/12"} (API ${Build.VERSION.SDK_INT})\n")
            appendLine("━━━ 操作步骤 ━━━\n")
            appendLine("⚠️ Android 11+ 需要额外一步！\n")
            appendLine("【前置】在车机上手动操作：")
            appendLine("① 设置 → 应用 → MQTT Assistant\n")
            appendLine("② 右上角 ⋮ 菜单 → 「允许受限制的设置」\n")
            appendLine("   （验证密码/指纹后确认）\n")
            appendLine("③ 设置 → 无障碍 → 开启MQTT Assistant\n")
            appendLine("\n然后在电脑上执行ADB命令：\n")
            appendLine("【命令1】连接车机")
            appendLine("adb connect <车机IP>\n")
            appendLine("【命令2】授予写入权限")
            appendLine("adb shell pm grant io.emqx.mqtt android.permission.WRITE_SECURE_SETTINGS\n")
            appendLine("【命令3】写入无障碍服务")
            appendLine("adb shell settings put secure enabled_accessibility_services io.emqx.mqtt/.VoiceAccessibilityService\n")
            appendLine("【命令4】启用全局无障碍")
            appendLine("adb shell settings put secure accessibility_enabled 1\n")
            appendLine("\n✅ 完成后配置下方白名单，重启即可生效！")
        }
        return Triple(title, message, commands)
    }

    /** Android 14 (API 34+) 指南 - 完整含自动恢复 */
    private fun buildApi34Guide(): Triple<String, String, String> {
        val title = "ADB 无障碍锁定 (Android 14+)"
        val commands = buildString {
            appendLine("# 连接车机")
            appendLine("adb connect <车机IP>")
            appendLine("")
            appendLine("# 【必须】授予写入权限（支持App内自动恢复！）")
            appendLine("adb shell pm grant io.emqx.mqtt android.permission.WRITE_SECURE_SETTINGS")
            appendLine("")
            appendLine("# 写入无障碍服务（核心）")
            appendLine("adb shell settings put secure enabled_accessibility_services io.emqx.mqtt/.VoiceAccessibilityService")
            appendLine("")
            appendLine("# 启用全局无障碍")
            appendLine("adb shell settings put secure accessibility_enabled 1")
            appendLine("")
            appendLine("# 防重置标记（比亚迪兼容）")
            appendLine("adb shell settings put global persist.sys.accessibility_retain 1")
        }
        val message = buildString {
            appendLine("🚗 当前系统：Android 14+ (API ${Build.VERSION.SDK_INT})\n")
            appendLine("━━━ 操作步骤 ━━━\n")
            appendLine("★ Android 14 特有优势：执行后App可在被抹除时自动恢复！\n")
            appendLine("【第1步】车机上手动授权（仅首次）：")
            appendLine("① 设置 → 应用 → MQTT Assistant\n")
            appendLine("② ⋮ 菜单 → 「允许受限制的设置」→ 确认\n")
            appendLine("③ 设置 → 无障碍 → 开启MQTT Assistant\n")
            appendLine("\n【第2步】电脑执行ADB命令：\n")
            appendLine("adb connect <车机IP>\n")
            appendLine("adb shell pm grant io.emqx.mqtt android.permission.WRITE_SECURE_SETTINGS\n")
            appendLine("adb shell settings put secure enabled_accessibility_services io.emqx.mqtt/.VoiceAccessibilityService\n")
            appendLine("adb shell settings put secure accessibility_enabled 1\n")
            appendLine("\n【第3步】验证 + 白名单：")
            appendLine("adb shell settings get secure enabled_accessibility_services\n")
            appendLine("→ 返回包名即成功\n")
            appendLine("→ 配置下方：极速白名单 + 电池优化 + 自启动\n")
            appendLine("\n✨ 效果：即使系统清空，App会在30秒内自动恢复！")
        }
        return Triple(title, message, commands)
    }

    /** dp转px工具 */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * 切换密码字段的明文/密文显示
     * @param isVisible true=明文显示（睁眼），false=密文显示（闭眼）
     */
    private fun togglePasswordVisibility(isVisible: Boolean) {
        val inputType = if (isVisible) {
            InputType.TYPE_CLASS_TEXT  // 明文
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD  // 密文
        }
        
        // 应用到需要保密的字段：Host、Username、Password、HA Address
        mHost.inputType = inputType
        mUsername.inputType = inputType
        mPassword.inputType = inputType
        mHaAddress.inputType = inputType
        
        // 保持光标在末尾，避免输入位置错乱
        mHost.setSelection(mHost.text.length)
        mUsername.setSelection(mUsername.text.length)
        mPassword.setSelection(mPassword.text.length)
        mHaAddress.setSelection(mHaAddress.text.length)
        
        // 更新图标
        val iconRes = if (isVisible) {
            R.drawable.ic_eye_open  // 睁眼图标
        } else {
            R.drawable.ic_eye_closed  // 闭眼图标
        }
        mBtnTogglePasswordVisibility.setImageResource(iconRes)
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
