package io.emqx.mqtt

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.ViewGroup
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), MqttCallback {
    var mClient: MqttAsyncClient? = null
    private var mConnection: Connection? = null
    private val mFragmentList: MutableList<Fragment> = ArrayList()
    private var isConnecting = false
    private var logCallback: ((String) -> Unit)? = null

    var ttsManager: TTSManager? = null
    var floatWindowManager: FloatWindowManager? = null

    // ========== 横竖屏切换时保持MQTT连接不断（static holder跨recreate存活）==========
    companion object {
        /** 保存MqttAsyncClient实例，在Activity重建时避免断连 */
        private var sPreservedClient: MqttAsyncClient? = null
        private var sPreservedConnection: Connection? = null
    }

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

    // ========== MQTT状态多监听器（解决Home/Setting互相覆盖问题）==========
    private val mqttStatusListeners = mutableListOf<(Boolean) -> Unit>()

    fun addMqttStatusListener(listener: (Boolean) -> Unit) {
        mqttStatusListeners.add(listener)
    }

    // 保留旧API兼容SettingFragment（内部改用addMqttStatusListener）
    @Deprecated("Use addMqttStatusListener() for multiple listeners")
    fun setOnMqttStatusChangedListener(listener: (Boolean) -> Unit) {
        mqttStatusListeners.add(listener)
    }

    // ========== 日志回调（写入HomeFragment的Debug Log容器）==========
    private var homeLogCallback: ((String) -> Unit)? = null
    // Network点击触发器（由HomeFragment设置）
    private var networkClickTrigger: (() -> Unit)? = null

    fun setLogCallbackToHome(callback: (String) -> Unit) {
        homeLogCallback = callback
    }

    fun setNetworkClickTrigger(trigger: () -> Unit) {
        networkClickTrigger = trigger
    }

    // ========== 全局logBuilder（用于Clear/Copy操作）==========
    val globalLogBuilder = StringBuilder()

    fun clearDebugLog() {
        globalLogBuilder.clear()
    }

    // 标记TTS是否已在之前的Activity实例中初始化完成（用于recreate场景避免重复toast）
    private var ttsWasReadyBeforeRecreate = false
    /** 是否跳过TTS就绪toast（recreate场景避免重启感知） */
    private var skipTtsReadyToast = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        CapturedTextManager.init(this)

        window.decorView.post {
            // recreate()导致的Activity重建：如果TTS之前已就绪，静默重新初始化不显示toast
            skipTtsReadyToast = savedInstanceState?.getBoolean("tts_was_ready") == true
            initTTSForCarMachine(skipTtsReadyToast)
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

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            // 横屏模式：使用自定义垂直侧边栏替代TabLayout
            val navSidebar = findViewById<LinearLayout>(R.id.nav_sidebar)
            setupLandscapeSidebar(navSidebar, viewPager, sectionsPagerAdapter)
        } else {
            // 竖屏模式：使用标准底部TabLayout
            val tabs = findViewById<TabLayout>(R.id.tabs)
            tabs.setupWithViewPager(viewPager)
            for (i in 0 until tabs.tabCount) {
                val tab = tabs.getTabAt(i)
                tab?.setIcon(sectionsPagerAdapter.getPageIcon(i))
            }
        }

        setupAccessibilityService()

        // ========== 比亚迪车机：启动时检测无障碍状态 ==========
        checkAccessibilityOnStartup()

        // 恢复横竖屏切换前保存的MQTT连接（避免断连重连）
        if (sPreservedClient != null && sPreservedClient?.isConnected == true) {
            mClient = sPreservedClient
            mConnection = sPreservedConnection
            // 重新将callback指向新的Activity实例（旧的Activity已被销毁）
            mClient?.setCallback(this@MainActivity)
            Log.i("MainActivity", "Restored MQTT client from recreate: connected=${mClient?.isConnected}, server=${mConnection?.host}")
            // 更新UI状态为已连接
            MqttService.updateConnectionStatus(this, true)
            notifyMqttStatusChanged(true)
            appendLog("[MQTT] Connection preserved across orientation change")
            sPreservedClient = null
            sPreservedConnection = null
        }

        // 自动检测并申请悬浮窗权限（车机首次启动需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.d("MainActivity", "No overlay permission, requesting...")
                window.decorView.postDelayed({
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to request overlay permission: ${e.message}")
                    }
                }, 1500)
            }
        }

        // Auto Connect：无论从哪种方式启动，都检查是否需要自动重连
        val configManager = ConfigManager.getInstance(this)
        if (configManager.autoConnect && configManager.hasSavedConfig()) {
            Log.d("MainActivity", "Auto-connect is ON, will reconnect after delay...")
            window.decorView.postDelayed({
                autoConnectIfConfigured()
            }, 2000)
        } else if (intent.getBooleanExtra("auto_connect", false)) {
            Log.d("MainActivity", "Auto-connect requested")
            window.decorView.postDelayed({
                autoConnectIfConfigured()
            }, 1000)
        }
    }

    // 横屏侧边栏导航项View列表，用于选中态管理
    private val sidebarNavViews = mutableListOf<View>()

    private fun setupLandscapeSidebar(sidebar: LinearLayout, viewPager: ViewPager, adapter: SectionsPagerAdapter) {
        val iconSize = resources.getDimensionPixelSize(R.dimen.sidebar_icon_size)
        val textSize = resources.getDimension(R.dimen.sidebar_text_size)
        val padding = resources.getDimensionPixelSize(R.dimen.sidebar_item_padding)

        sidebarNavViews.clear()

        for (i in 0 until adapter.count) {
            // 每个导航项：垂直LinearLayout，weight=1实现均匀分布
            val navItem = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                setPadding(padding, padding, padding, padding)

                // 点击切换ViewPager页面
               setOnClickListener { viewPager.currentItem = i }
            }

            val imageView = ImageView(this).apply {
                setImageResource(adapter.getPageIcon(i))
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            }

            val textView = TextView(this).apply {
                text = getString(SectionsPagerAdapter.TAB_TITLES[i])
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                isSingleLine = true
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = padding }
            }

            navItem.addView(imageView)
            navItem.addView(textView)
            sidebar.addView(navItem)
            sidebarNavViews.add(navItem)
        }

        // 设置初始选中态(第一个tab)
        updateSidebarSelection(0)

        // 监听页面滑动同步侧边栏选中态
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) { updateSidebarSelection(position) }
            override fun onPageScrollStateChanged(state: Int) {}
        })
    }

    private fun updateSidebarSelection(selectedIndex: Int) {
        val selectedColor = Color.parseColor("#FFFFFF")
        val unselectedColor = Color.parseColor("#B3FFFFFF") // 70%白色

        sidebarNavViews.forEachIndexed { index, view ->
            if (index == selectedIndex) {
                view.setBackgroundColor(Color.parseColor("#33FFFFFF")) // 浅白背景高亮
                // 图标和文字设为全白
                (view as? LinearLayout)?.let { container ->
                    if (container.childCount >= 2) {
                        (container.getChildAt(0) as? ImageView)?.setColorFilter(selectedColor)
                        (container.getChildAt(1) as? TextView)?.setTextColor(selectedColor)
                    }
                }
            } else {
                view.background = null
                (view as? LinearLayout)?.let { container ->
                    if (container.childCount >= 2) {
                        (container.getChildAt(0) as? ImageView)?.setColorFilter(unselectedColor)
                        (container.getChildAt(1) as? TextView)?.setTextColor(unselectedColor)
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // recreate()前保存MQTT客户端到静态变量，避免Activity重建时断连
        sPreservedClient = mClient
        sPreservedConnection = mConnection
        Log.d("MainActivity", "onConfigurationChanged: preserving MQTT client (connected=${mClient?.isConnected})")
        // configChanges声明后Android不会自动reinflate布局，必须手动recreate()
        // 这样onCreate会被重新调用，系统会根据新方向加载layout或layout-land的资源
        recreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存关键运行时状态，供recreate()后恢复
        outState.putBoolean("was_connected", mClient?.isConnected == true)
        outState.putBoolean("was_connecting", isConnecting)
        outState.putInt("current_tab", findViewById<ViewPager>(R.id.view_pager)?.currentItem ?: 0)
        // 保存TTS就绪状态：recreate后新Activity跳过"TTS ready" toast避免重启感知
        outState.putBoolean("tts_was_ready", ttsManager?.isReady() == true)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val wasConnected = savedInstanceState.getBoolean("was_connected", false)
        val currentTab = savedInstanceState.getInt("current_tab", 0)
        Log.d("MainActivity", "onRestoreInstanceState: wasConnected=$wasConnected, currentTab=$currentTab")
        // 恢复ViewPager当前页（延迟执行确保adapter已设置）
        window.decorView.post {
            findViewById<ViewPager>(R.id.view_pager)?.currentItem = currentTab
        }
    }

    /** singleTask模式下从通知栏/BootReceiver返回时触发 */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 处理auto_connect参数（BootReceiver场景）
        if (intent?.getBooleanExtra("auto_connect", false) == true) {
            window.decorView.postDelayed({ autoConnectIfConfigured() }, 1000)
        }
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

    /**
     * 车机TTS初始化（适配比亚迪Android 10）
     * - 使用Activity Context而非ApplicationContext
     * - 先检测可用引擎，引导用户安装
     * - 失败时支持重试和重新初始化
     * @param skipReadyToast true=不显示"TTS ready"toast（recreate场景避免重启感知）
     */
    private fun initTTSForCarMachine(skipReadyToast: Boolean = false) {
        Log.d("MainActivity", "initTTSForCarMachine: starting TTS init for car machine (skipToast=$skipReadyToast)")
        appendLog("[TTS] Initializing for Android ${android.os.Build.VERSION.SDK_INT}...")
        
        // 使用 Activity Context（车机上 ApplicationContext 可能导致 TTS 初始化失败）
        ttsManager = TTSManager(this@MainActivity)
        
        // 先检测是否有可用的 TTS 引擎
        val hasEngine = ttsManager?.hasAvailableEngine() == true
        
        if (!hasEngine) {
            Log.w("MainActivity", "initTTSForCarMachine: No TTS engine found!")
            appendLog("[TTS] Warning: No TTS engine detected")
            
            // 尝试列出所有引擎信息
            val engines = ttsManager?.getEnginesInfo()
            if (engines.isNullOrEmpty()) {
                appendLog("[TTS] No engines at all, prompting to install...")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, 
                        "No TTS engine detected. Please install one.", 
                        Toast.LENGTH_LONG).show()
                    val checkIntent = ttsManager?.getTTSCheckIntent()
                    if (checkIntent != null) {
                        startActivityForResult(checkIntent, 1001)
                    }
                }
            } else {
                appendLog("[TTS] Found ${engines.size} engine(s): ${engines.joinToString { it.first }}")
                // 有引擎但 hasAvailableEngine 返回 false，可能是权限问题，继续尝试初始化
                doInitTTSWithRetry()
            }
        } else {
            appendLog("[TTS] Engine available, initializing...")
            doInitTTSWithRetry()
        }
    }

    private fun doInitTTSWithRetry() {
        val shouldSkipToast = this.skipTtsReadyToast
        ttsManager?.setOnInitListener(object : TTSManager.OnInitListener {
            override fun onInitSuccess() {
                Log.i("MainActivity", "initTTSForCarMachine: SUCCESS!")
                val engineInfo = ttsManager?.getEnginesInfo()?.joinToString { it.second } ?: "unknown"
                appendLog("[TTS] Ready! Engine: $engineInfo")
                // recreate()导致的重建不显示toast，避免用户感知到"重启"
                if (!shouldSkipToast) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "TTS ready ($engineInfo)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("MainActivity", "TTS ready (silent, skip toast for recreate)")
                }
            }

            override fun onInitFailed(status: Int) {
                Log.e("MainActivity", "initTTSForCarMachine: FAILED status=$status")
                
                when (status) {
                    -1 -> appendLog("[TTS] Failed: TextToSpeech initialization error")
                    -2 -> appendLog("[TTS] Failed: Initialization timeout (${TTSManager.INIT_TIMEOUT_MS}ms)")
                    -3 -> appendLog("[TTS] Failed: Chinese language not supported")
                    -4 -> appendLog("[TTS] Failed: No available language")
                    -5 -> appendLog("[TTS] Failed: Exception during creation")
                    else -> appendLog("[TTS] Failed: Error code $status")
                }
                
                runOnUiThread {
                    val msg = when (status) {
                        -2 -> "TTS timeout (car machine slow). Retrying..."
                        -3, -4 -> "TTS language not available"
                        else -> "TTS failed: $status. Will retry..."
                    }
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    
                    // 延迟后自动重试一次
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d("MainActivity", "initTTSForCarMachine: Auto-retrying...")
                        appendLog("[TTS] Auto-retry...")
                        ttsManager?.reinitialize()
                        // 更新监听器为简单版本，避免无限重试
                        ttsManager?.setOnInitListener(object : TTSManager.OnInitListener {
                            override fun onInitSuccess() {
                                appendLog("[TTS] Retry SUCCESS!")
                                runOnUiThread { Toast.makeText(this@MainActivity, "TTS ready after retry!", Toast.LENGTH_SHORT).show() }
                            }
                            override fun onInitFailed(s: Int) {
                                appendLog("[TTS] Retry also failed: $s")
                                runOnUiThread { Toast.makeText(this@MainActivity, "TTS failed completely: $s", Toast.LENGTH_LONG).show() }
                            }
                        })
                    }, 3000)
                }
            }
        })
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

    // ========== 比亚迪车机：启动时检测无障碍状态 ==========
    private fun checkAccessibilityOnStartup() {
        val sp = getSharedPreferences("a11y_status", MODE_PRIVATE)
        val needsFix = sp.getBoolean("a11y_needs_fix", false)

        // 检测当前无障碍是否真的开启了
        val isCurrentlyEnabled = isAccessibilityServiceEnabled()

        Log.d("MainActivity", "A11y startup check: needsFix=$needsFix, currentlyEnabled=$isCurrentlyEnabled")

        if (needsFix && !isCurrentlyEnabled) {
            // 系统重置了无障碍权限！弹出强烈提示
            sp.edit().remove("a11y_needs_fix").apply()
            window.decorView.postDelayed({
                Toast.makeText(this, getString(R.string.a11y_reset_warning), Toast.LENGTH_LONG).show()
                // 延迟再弹一次引导
                window.decorView.postDelayed({
                    requestAccessibilityService()
                }, 2000)
            }, 1500)
        } else if (isCurrentlyEnabled) {
            // 无障碍正常，清除异常标记
            sp.edit().remove("a11y_needs_fix").apply()
            // 记录"本次运行时无障碍是开着的"
            sp.edit().putBoolean("a11y_was_enabled", true).apply()
            Log.d("MainActivity", "✅ A11y service is running normally")
        }
    }

    /** 保留兼容性：SettingFragment仍通过此回调获取日志 */
    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    /**
     * 写入日志：同时写入全局builder + HomeFragment容器 + SettingFragment回调
     */
    fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val formattedMessage = "[$timestamp] $message\n"
            
            // 写入全局builder（用于Clear/Copy）
            globalLogBuilder.insert(0, formattedMessage)
            if (globalLogBuilder.length > 2000) {
                globalLogBuilder.setLength(2000)
            }
            
            // 写入HomeFragment的Debug Log容器
            homeLogCallback?.invoke(message)
            
            // 兼容SettingFragment的日志回调
            logCallback?.invoke(message)
            
            Log.d("MainActivity", message)
        }
    }

    /** 触发Network x5 显示Debug Log（由HomeFragment调用） */
    fun onNetworkTextClicked() {
        networkClickTrigger?.invoke()
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
                        notifyMqttStatusChanged(true)
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
                        notifyMqttStatusChanged(false)
                    }
                }
            })
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            isConnecting = false
            MqttService.updateConnectionStatus(this, false)
            notifyMqttStatusChanged(false)
            e.printStackTrace()
            Toast.makeText(this, "MqttException: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            isConnecting = false
            MqttService.updateConnectionStatus(this, false)
            notifyMqttStatusChanged(false)
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
            notifyMqttStatusChanged(false)
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            MqttService.updateConnectionStatus(this, false)
            notifyMqttStatusChanged(false)
            e.printStackTrace()
        }
    }

    fun subscribe(subscription: Subscription, listener: org.eclipse.paho.client.mqttv3.IMqttActionListener?) {
        if (notConnected(true)) {
            return
        }
        try {
            mClient?.subscribe(subscription.topic, subscription.qos, null, listener)
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
                publish.isRetained,
                null,
                callback
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

    /** 通知所有MQTT状态监听者（HomeFragment + SettingFragment） */
    private fun notifyMqttStatusChanged(connected: Boolean) {
        mqttStatusListeners.forEach { it.invoke(connected) }
    }

    override fun connectionLost(cause: Throwable?) {
        appendLog("Connection lost: $cause")
        isConnecting = false
        MqttService.updateConnectionStatus(this, false)
        notifyMqttStatusChanged(false)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // TTS引擎和语言数据可用，重新初始化
                Log.d("MainActivity", "TTS engine and language data available, reinitializing")
                ttsManager?.release()
                ttsManager = TTSManager(applicationContext)
                ttsManager?.setOnInitListener(object : TTSManager.OnInitListener {
                    override fun onInitSuccess() {
                        Log.d("MainActivity", "TTS reinitialized successfully!")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "TTS ready", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onInitFailed(status: Int) {
                        Log.e("MainActivity", "TTS reinitialization failed! status=$status")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "TTS init failed: $status", Toast.LENGTH_LONG).show()
                        }
                    }
                })
            } else {
                // TTS引擎或语言数据不可用
                Log.e("MainActivity", "TTS engine or language data not available")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放TTS资源
        ttsManager?.release()
        // 释放浮动窗口资源
        floatWindowManager?.release()
    }
}
