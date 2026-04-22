package io.emqx.mqtt

import android.content.Intent
import java.io.File
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    
    // ========== MQTT 连接监控 ==========
    private val mqttCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var mqttCheckRunnable: Runnable? = null
    private val MQTT_CHECK_INTERVAL = 15000L  // 每15秒检查一次连接状态

    var ttsPlayer: CloudTTSPlayer? = null
    var floatWindowManager: FloatWindowManager? = null

    // ========== 横竖屏切换时保持MQTT连接不断（static holder跨recreate存活）==========
    companion object {
        /** 保存MqttAsyncClient实例，在Activity重建时避免断连 */
        private var sPreservedClient: MqttAsyncClient? = null
        private var sPreservedConnection: Connection? = null
        
        /**
         * 模拟系统级点击屏幕右侧1/4宽度、1/2高度区域
         * 用于替代返回键功能
         */
        fun simulateClickBack(activity: MainActivity) {
            try {
                val displayMetrics = activity.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                
                // 计算点击位置：右侧1/4宽度，中间1/2高度
                val clickX = (screenWidth * 0.875).toInt()  // 右侧7/8位置（右侧1/4的中心）
                val clickY = (screenHeight * 0.5).toInt()   // 垂直居中
                
                Log.d("MainActivity", "Simulating click at ($clickX, $clickY) for back action")
                
                // 使用Instrumentation模拟点击
                val instrumentation = android.app.Instrumentation()
                Thread {
                    try {
                        instrumentation.sendPointerSync(
                            android.view.MotionEvent.obtain(
                                android.os.SystemClock.uptimeMillis(),
                                android.os.SystemClock.uptimeMillis(),
                                android.view.MotionEvent.ACTION_DOWN,
                                clickX.toFloat(),
                                clickY.toFloat(),
                                0
                            )
                        )
                        instrumentation.sendPointerSync(
                            android.view.MotionEvent.obtain(
                                android.os.SystemClock.uptimeMillis(),
                                android.os.SystemClock.uptimeMillis(),
                                android.view.MotionEvent.ACTION_UP,
                                clickX.toFloat(),
                                clickY.toFloat(),
                                0
                            )
                        )
                        Log.d("MainActivity", "Click simulation completed")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to simulate click: ${e.message}")
                    }
                }.start()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in simulateClickBack: ${e.message}")
            }
        }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        CapturedTextManager.init(this)

        // ========== 初始化云端TTS（启动即用，无需等待） ==========
        ttsPlayer = CloudTTSPlayer.getInstance()
        // 设置Context（用于显示Toast）
        ttsPlayer?.setContext(this)
        // 设置音频缓存目录（先下载到本地文件再播放）
        val ttsCacheDir = File(cacheDir, "cloudtts_cache")
        ttsPlayer?.setCacheDir(ttsCacheDir)
        // 从ConfigManager恢复云TTS设置
        val cfg = ConfigManager.getInstance(this)
        ttsPlayer?.apply {
            currentApiIndex = cfg.cloudTtsApiIndex
            voice = cfg.cloudTtsVoice
            speed = cfg.cloudTtsSpeed
            // pitch在Edge-TTS中为String格式如"+0Hz"，ConfigManager存储的是Float近似值，需要转换
            val pitchVal = cfg.cloudTtsPitch
            if (pitchVal > 0f) {
                pitch = "+${(pitchVal * 10).toInt()}Hz"
            } else if (pitchVal < 0f) {
                pitch = "${(pitchVal * 10).toInt()}Hz"
            } else {
                pitch = "+0Hz"
            }
            volume = cfg.cloudTtsVolume
        }
        appendLog("[CloudTTS] 已初始化: ${ttsPlayer?.getCurrentApiName()}")

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

        // 使用自定义垂直侧边栏替代TabLayout
        val navSidebar = findViewById<LinearLayout>(R.id.nav_sidebar)
        setupLandscapeSidebar(navSidebar, viewPager, sectionsPagerAdapter)
        
        // 动态设置侧边栏宽度（根据屏幕方向）
        navSidebar.post {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val isLandscape = screenWidth > screenHeight
            
            // 竖屏：宽度 = 屏幕宽度的 1/10
            // 横屏：宽度 = min(高,宽) / 5
            val sideSize = if (isLandscape) {
                Math.min(screenWidth, screenHeight) / 5
            } else {
                screenWidth / 10
            }
            
            if (sideSize > 0) {
                val params = navSidebar.layoutParams as LinearLayout.LayoutParams
                params.width = sideSize
                navSidebar.layoutParams = params
                Log.d("MainActivity", "Sidebar width: $sideSize px (${if (isLandscape) "landscape" else "portrait"})")
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

        // Auto Connect：只要有配置就自动连接（已移除 Auto Connect 开关）
        val configManager = ConfigManager.getInstance(this)
        val autoConnectFromBoot = intent.getBooleanExtra("auto_connect", false)
        
        if (configManager.hasSavedConfig()) {
            // 有保存的配置：延迟3秒后自动连接（避免启动卡顿时任务被阻塞）
            Log.d("MainActivity", "Saved config exists, will auto-connect after 3s delay...")
            window.decorView.postDelayed({
                autoConnectIfConfigured()
            }, 3000)
        } else if (autoConnectFromBoot) {
            // 从 BootReceiver/通知栏启动：延迟2秒后自动连接
            Log.d("MainActivity", "Auto-connect requested from boot/notification")
            window.decorView.postDelayed({
                autoConnectIfConfigured()
            }, 2000)
        } else {
            Log.d("MainActivity", "No saved config, no auto-connect")
        }
    }

    // 横屏侧边栏导航项View列表，用于选中态管理
    private val sidebarNavViews = mutableListOf<View>()

    private fun setupLandscapeSidebar(sidebar: LinearLayout, viewPager: ViewPager, adapter: SectionsPagerAdapter) {
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
                // 动态设置图标尺寸：使用weight让图标和文字按比例分配空间
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    0.7f  // 图标占70%高度
                ).apply {
                    // 设置最小尺寸，确保图标不会太小
                    minimumWidth = resources.getDimensionPixelSize(R.dimen.sidebar_icon_size)
                    minimumHeight = resources.getDimensionPixelSize(R.dimen.sidebar_icon_size)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }

            val textView = TextView(this).apply {
                text = getString(SectionsPagerAdapter.TAB_TITLES[i])
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                isSingleLine = true
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    0.3f  // 文字占30%高度
                ).apply {
                    topMargin = padding / 2
                }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存关键运行时状态，供recreate()后恢复
        outState.putBoolean("was_connected", mClient?.isConnected == true)
        outState.putBoolean("was_connecting", isConnecting)
        outState.putInt("current_tab", findViewById<ViewPager>(R.id.view_pager)?.currentItem ?: 0)
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
        
        // 处理auto_connect参数（BootReceiver/通知栏场景）
        val autoConnectFromBoot = intent?.getBooleanExtra("auto_connect", false) == true
        val configManager = ConfigManager.getInstance(this)
        
        if (autoConnectFromBoot || (configManager.autoConnect && configManager.hasSavedConfig())) {
            Log.d("MainActivity", "onNewIntent: Auto-connect triggered")
            window.decorView.postDelayed({ 
                autoConnectIfConfigured() 
            }, 1000)
        } else {
            Log.d("MainActivity", "onNewIntent: No auto-connect needed")
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
                    
                    // 初始化 Home Assistant 集成
                    HomeAssistantIntegration.init(this@MainActivity)
                    
                    // 发布 Home Assistant 自动发现配置
                    HomeAssistantIntegration.publishDiscoveryConfig(this@MainActivity, mClient)
                    
                    // 启动定期电池上报（会自动立即上报一次）
                    HomeAssistantIntegration.startBatteryReporting(this@MainActivity, mClient, this@MainActivity)
                    
                    // 启动MQTT连接状态监控
                    startMqttConnectionMonitor()
                    
                    runOnUiThread {
                        ToastUtils.showShort(this@MainActivity, "Connected!")
                        notifyMqttStatusChanged(true)
                        // TTS播报MQTT连接状态
                        ttsPlayer?.speak("MQTT已连接", force = true)
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnecting = false
                    appendLog("=== CONNECT FAILED ===")
                    appendLog("Error: ${exception?.message}")
                    MqttService.updateConnectionStatus(this@MainActivity, false)
                    exception?.printStackTrace()
                    runOnUiThread {
                        ToastUtils.showLong(this@MainActivity, "Connect failed: ${exception?.message}")
                        notifyMqttStatusChanged(false)
                        // TTS播报MQTT连接状态
                        ttsPlayer?.speak("MQTT未连接", force = true)
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
        
        // 停止电池上报
        HomeAssistantIntegration.stopBatteryReporting()
        
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
                ToastUtils.showShort(this, "Client is not connected")
            }
            return true
        }
        return false
    }

    fun showFloatMessage(title: String, message: String) {
        Log.d("MainActivity", "showFloatMessage called: title=$title, isFloatWindowEnabled=$isFloatWindowEnabled")
        if (isFloatWindowEnabled) {
            if (floatWindowManager?.canDrawOverlays() == false) {
                ToastUtils.showLong(this, "Please grant overlay permission for float window")
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
            ttsPlayer?.speak(text)
        } else {
            Log.d("MainActivity", "TTS is disabled, skipping")
        }
    }

    /** 通知所有MQTT状态监听者（HomeFragment + SettingFragment） */
    private fun notifyMqttStatusChanged(connected: Boolean) {
        mqttStatusListeners.forEach { it.invoke(connected) }
    }

    /**
     * 添加 Publish 历史记录（供 HomeAssistantIntegration 调用）
     */
    fun addPublishHistory(publish: Publish) {
        runOnUiThread {
            val publishFragment = mFragmentList.getOrNull(3) as? PublishFragment
            publishFragment?.let {
                // 通过反射访问私有字段和方法
                try {
                    val listField = PublishFragment::class.java.getDeclaredField("mPublishList")
                    listField.isAccessible = true
                    val publishList = listField.get(it) as ArrayList<Publish>
                    
                    // 添加到列表开头
                    publishList.add(0, publish)
                    
                    // 更新 adapter
                    val adapterField = PublishFragment::class.java.getDeclaredField("mAdapter")
                    adapterField.isAccessible = true
                    val adapter = adapterField.get(it) as? PublishRecyclerViewAdapter
                    adapter?.notifyItemInserted(0)
                    
                    // 保存历史
                    val saveMethod = PublishFragment::class.java.getDeclaredMethod("savePublishHistory")
                    saveMethod.isAccessible = true
                    saveMethod.invoke(it)
                    
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to add publish history", e)
                }
            }
        }
    }

    override fun connectionLost(cause: Throwable?) {
        appendLog("Connection lost: $cause")
        isConnecting = false
        
        // 停止电池上报
        HomeAssistantIntegration.stopBatteryReporting()
        
        MqttService.updateConnectionStatus(this, false)
        notifyMqttStatusChanged(false)
        runOnUiThread {
            (mFragmentList[1] as? SettingFragment)?.updateButtonText()
        }
        
        // ========== Auto Connect: 只要有配置就自动重连 ==========
        val configManager = ConfigManager.getInstance(this)
        if (configManager.hasSavedConfig()) {
            appendLog("🔄 Config exists, will attempt reconnect in 3 seconds...")
            Log.d("MainActivity", "Saved config found, scheduling reconnect...")
            
            // 延迟3秒后尝试重连（避免频繁重连）
            window.decorView.postDelayed({
                if (configManager.hasSavedConfig() && !isConnecting && (mClient?.isConnected != true)) {
                    appendLog("🔄 Attempting auto-reconnect...")
                    val connection = Connection(
                        this@MainActivity,
                        configManager.host,
                        configManager.port,
                        configManager.clientId,
                        configManager.username,
                        configManager.password,
                        configManager.protocol,
                        configManager.path
                    )
                    connect(connection, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            appendLog("✅ Auto-reconnect successful")
                            Log.d("MainActivity", "Auto-reconnect success after connectionLost")
                        }
                        
                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            appendLog("❌ Auto-reconnect failed: ${exception?.message}")
                            Log.e("MainActivity", "Auto-reconnect failed", exception)
                            // 如果失败，继续尝试重连（最多重试5次）
                            scheduleReconnectIfNeeded(configManager, retryCount = 1)
                        }
                    })
                }
            }, 3000)
        } else {
            Log.d("MainActivity", "No saved config, no auto-reconnect")
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
                Log.d("MainActivity", "Speaking with CloudTTS")
                appendLog("Speaking CloudTTS...")
                ttsPlayer?.speak(payload, force = true)
            } else {
                appendLog("TTS is disabled, skipping")
            }
        }
    }

    override fun deliveryComplete(token: IMqttDeliveryToken) {}

    // ========== MQTT 连接状态监控 ==========
    
    /**
     * 启动MQTT连接状态监控
     */
    private fun startMqttConnectionMonitor() {
        // 先停止之前的监控
        stopMqttConnectionMonitor()
        
        mqttCheckRunnable = Runnable {
            checkMqttConnection()
            // 循环执行
            mqttCheckHandler.postDelayed(mqttCheckRunnable!!, MQTT_CHECK_INTERVAL)
        }
        
        // 立即执行第一次检查，然后每隔15秒检查一次
        mqttCheckHandler.postDelayed(mqttCheckRunnable!!, MQTT_CHECK_INTERVAL)
        Log.d("MainActivity", "MQTT connection monitor started (interval: ${MQTT_CHECK_INTERVAL}ms)")
    }
    
    /**
     * 停止MQTT连接状态监控
     */
    private fun stopMqttConnectionMonitor() {
        mqttCheckRunnable?.let {
            mqttCheckHandler.removeCallbacks(it)
            mqttCheckRunnable = null
            Log.d("MainActivity", "MQTT connection monitor stopped")
        }
    }
    
    /**
     * 检查MQTT连接状态，如果断开则尝试重连
     */
    private fun checkMqttConnection() {
        val client = mClient
        val connection = mConnection
        
        if (client == null || connection == null) {
            Log.d("MainActivity", "MQTT client or connection is null, skip check")
            return
        }
        
        val isConnected = client.isConnected
        Log.d("MainActivity", "MQTT connection check: isConnected=$isConnected")
        
        if (!isConnected && !isConnecting) {
            appendLog("⚠️ MQTT connection lost detected by monitor, attempting reconnect...")
            Log.w("MainActivity", "MQTT disconnected, auto-reconnecting...")
            
            // 尝试重连
            runOnUiThread {
                connect(connection, object : org.eclipse.paho.client.mqttv3.IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        appendLog("✅ Auto-reconnect successful")
                        Log.d("MainActivity", "Auto-reconnect successful")
                    }
                    
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        appendLog("❌ Auto-reconnect failed: ${exception?.message}")
                        Log.e("MainActivity", "Auto-reconnect failed", exception)
                    }
                })
            }
        }
    }
    
    /**
     * 调度自动重连（带重试机制）
     * @param configManager 配置管理器
     * @param retryCount 当前重试次数
     * @param maxRetries 最大重试次数
     */
    private fun scheduleReconnectIfNeeded(configManager: ConfigManager, retryCount: Int = 0, maxRetries: Int = 5) {
        if (retryCount >= maxRetries) {
            appendLog("⛔ Max reconnect retries ($maxRetries) reached, giving up")
            Log.w("MainActivity", "Max reconnect retries reached")
            return
        }
        
        // 只要有配置就尝试重连（已移除 Auto Connect 开关检查）
        if (!configManager.hasSavedConfig()) {
            Log.d("MainActivity", "No saved config, stop retrying")
            return
        }
        
        // 指数退避：3s, 6s, 12s, 24s, 48s
        val delay = 3000L * (1 shl retryCount) // 3s * 2^retryCount
        appendLog("🔄 Scheduling reconnect attempt ${retryCount + 1}/$maxRetries in ${delay/1000}s...")
        
        window.decorView.postDelayed({
            if (configManager.hasSavedConfig() && !isConnecting && (mClient?.isConnected != true)) {
                appendLog("🔄 Reconnect attempt ${retryCount + 1}/$maxRetries...")
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
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        appendLog("✅ Reconnect successful on attempt ${retryCount + 1}")
                        Log.d("MainActivity", "Reconnect success on attempt ${retryCount + 1}")
                    }
                    
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        appendLog("❌ Reconnect failed on attempt ${retryCount + 1}: ${exception?.message}")
                        Log.e("MainActivity", "Reconnect failed on attempt ${retryCount + 1}", exception)
                        // 继续下一次重试
                        scheduleReconnectIfNeeded(configManager, retryCount + 1, maxRetries)
                    }
                })
            }
        }, delay)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 云端TTS不需要onActivityResult处理
    }

    /**
     * 拦截返回键，将应用移至后台而不是退出
     * 这样可以让 MqttService 继续在后台运行，保持 MQTT 连接和 TTS 能力
     */
    override fun onBackPressed() {
        // 移动到后台，就像按了 Home 键一样
        moveTaskToBack(true)
    }

    override fun onResume() {
        super.onResume()
        // 当 App 回到前台时，重新启动 MQTT 连接监控
        startMqttConnectionMonitor()
        Log.d("MainActivity", "App resumed, MQTT connection monitor started")
    }

    override fun onStop() {
        super.onStop()
        // 当 App 进入后台时，停止 MQTT 连接监控，避免后台启动服务导致崩溃
        stopMqttConnectionMonitor()
        Log.d("MainActivity", "App stopped, MQTT connection monitor stopped")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // 屏幕旋转时重新计算侧边栏宽度
        val navSidebar = findViewById<LinearLayout>(R.id.nav_sidebar)
        navSidebar.post {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val isLandscape = screenWidth > screenHeight
            
            val sideSize = if (isLandscape) {
                Math.min(screenWidth, screenHeight) / 5
            } else {
                screenWidth / 10
            }
            
            if (sideSize > 0) {
                val params = navSidebar.layoutParams as LinearLayout.LayoutParams
                params.width = sideSize
                navSidebar.layoutParams = params
                Log.d("MainActivity", "Sidebar resized: $sideSize px (${if (isLandscape) "landscape" else "portrait"})")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 停止MQTT连接监控
        stopMqttConnectionMonitor()
        
        // 停止电池上报
        HomeAssistantIntegration.stopBatteryReporting()
        
        // 断开 MQTT 连接以触发 Last Will
        if (mClient != null && mClient!!.isConnected) {
            try {
                appendLog("App destroying, disconnecting MQTT to trigger Last Will...")
                mClient?.disconnect()
                mClient = null
                mConnection = null
                // 重要：同步更新 MqttService 的连接状态，避免 UI 显示不一致
                MqttService.updateConnectionStatus(this, false)
                notifyMqttStatusChanged(false)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to disconnect MQTT", e)
            }
        }
        
        // 释放云端TTS资源
        ttsPlayer?.release()
        // 释放浮动窗口资源
        floatWindowManager?.release()
    }
}
