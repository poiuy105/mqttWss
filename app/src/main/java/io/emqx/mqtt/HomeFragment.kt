package io.emqx.mqtt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : BaseFragment() {
    private var batteryLevelText: TextView? = null
    private var batteryStatusText: TextView? = null
    private var mqttStatusText: TextView? = null
    private var networkTypeText: TextView? = null
    private var wifiNameText: TextView? = null
    private var screenInfoText: TextView? = null
    private var orientationText: TextView? = null
    private var androidVersionText: TextView? = null
    private var networkTitleText: TextView? = null

    // ========== 车况数据显示（BYD本地接口） ==========
    private var carStatusTitleText: TextView? = null
    private var carSpeedText: TextView? = null
    private var carSocText: TextView? = null
    private var carGearText: TextView? = null
    private var carElecRemainText: TextView? = null
    private var carFuelRemainText: TextView? = null
    private var carOutTempText: TextView? = null
    private var carInTempText: TextView? = null
    private var carAcStatusText: TextView? = null
    private var carMileageText: TextView? = null
    private var carPowerModeText: TextView? = null
    private var carApiStatusText: TextView? = null

    /** 车况数据定时刷新Handler */
    private val carDataHandler = Handler(Looper.getMainLooper())
    private var carDataRunnable: Runnable? = null
    private var isCarPollingActive = false

    // ========== Debug Log（仅在Home页签显示） ==========
    private var mDebugLogContainer: View? = null
    private var mDebugLogText: TextView? = null
    private val logBuilder = StringBuilder()
    private var networkClickCount = 0
    private var lastNetworkClickTime = 0L

    private var batteryReceiver: BroadcastReceiver? = null
    private var networkReceiver: BroadcastReceiver? = null

    override val layoutResId: Int
        get() = R.layout.fragment_home

    override fun setUpView(view: View) {
        batteryLevelText = view.findViewById(R.id.battery_level)
        batteryStatusText = view.findViewById(R.id.battery_status)
        mqttStatusText = view.findViewById(R.id.mqtt_status)
        networkTypeText = view.findViewById(R.id.network_type)
        wifiNameText = view.findViewById(R.id.wifi_name)
        screenInfoText = view.findViewById(R.id.screen_info)
        orientationText = view.findViewById(R.id.orientation)
        androidVersionText = view.findViewById(R.id.android_version)
        networkTitleText = view.findViewById(R.id.network_title)

        // ========== 初始化 Debug Log 容器 ==========
        mDebugLogContainer = view.findViewById(R.id.debug_log_container)
        mDebugLogText = view.findViewById(R.id.log_text)

        view.findViewById<Button>(R.id.btn_log_clear)?.setOnClickListener {
            logBuilder.clear()
            mDebugLogText?.text = ""
            appendLocalLog("Debug log cleared")
            // 同步清除MainActivity的logBuilder
            (fragmentActivity as? MainActivity)?.clearDebugLog()
        }
        view.findViewById<Button>(R.id.btn_log_copy)?.setOnClickListener {
            val content = logBuilder.toString()
            if (content.isNotBlank()) {
                val clipboard = context?.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Debug Log", content))
                Toast.makeText(context, "Copied ${content.length} chars", Toast.LENGTH_SHORT).show()
                appendLocalLog("Debug log copied (${content.length} chars)")
            } else {
                Toast.makeText(context, "Log is empty", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.btn_log_close)?.setOnClickListener {
            closeDebugLog()
        }

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra("level", -1) ?: -1
                val scale = intent?.getIntExtra("scale", 100) ?: 100
                val status = intent?.getIntExtra("status", -1) ?: -1

                if (level >= 0) {
                    val batteryPct = (level * 100) / scale
                    batteryLevelText?.text = "$batteryPct%"
                }

                batteryStatusText?.text = when (status) {
                    android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                    else -> "Unknown"
                }
            }
        }

        networkReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateNetworkInfo()
            }
        }

        (fragmentActivity as? MainActivity)?.let { main ->
            // 注册为MQTT状态监听者（使用多监听器模式，不会被覆盖）
            main.addMqttStatusListener { connected ->
                updateMqttStatus(connected)
            }
            updateMqttStatus(main.notConnected(false) == false)

            // 设置日志回调：MainActivity的日志写入此容器
            main.setLogCallbackToHome { message ->
                appendLocalLog(message)
            }

            // 注册Network点击触发器
            main.setNetworkClickTrigger { showDebugLog() }
        }

        updateNetworkInfo()
        updateScreenInfo()
        updateOrientation()
        updateAndroidVersion()

        // 连续点击"Network"文字5次显示Debug Log
        networkTitleText?.setOnClickListener {
            onNetworkClicked()
        }

        // ========== 初始化车况数据显示 ==========
        initCarDataViews(view)
        // 设置车况API日志回调 -> 输出到Debug Log容器
        BydLocalCarApi.logCallback = { msg ->
            appendLocalLog(msg)
        }
        startCarDataPolling()
    }

    /** 初始化所有车况数据View引用 */
    private fun initCarDataViews(view: View) {
        carStatusTitleText = view.findViewById(R.id.car_status_title)
        carSpeedText = view.findViewById(R.id.car_speed)
        carSocText = view.findViewById(R.id.car_soc)
        carGearText = view.findViewById(R.id.car_gear)
        carElecRemainText = view.findViewById(R.id.car_elec_remain)
        carFuelRemainText = view.findViewById(R.id.car_fuel_remain)
        carOutTempText = view.findViewById(R.id.car_out_temp)
        carInTempText = view.findViewById(R.id.car_in_temp)
        carAcStatusText = view.findViewById(R.id.car_ac_status)
        carMileageText = view.findViewById(R.id.car_mileage)
        carPowerModeText = view.findViewById(R.id.car_power_mode)
        carApiStatusText = view.findViewById(R.id.car_api_status)

        setDefaultCarDataUI()
    }

    /** 设置车况数据默认显示 */
    private fun setDefaultCarDataUI() {
        carSpeedText?.text = "--"
        carSocText?.text = "--"
        carGearText?.text = "-"
        carElecRemainText?.text = "--"
        carFuelRemainText?.text = "--"
        carOutTempText?.text = "--"
        carInTempText?.text = "--"
        carAcStatusText?.text = "--"
        carMileageText?.text = "--"
        carPowerModeText?.text = "-"
        carApiStatusText?.text = "等待连接..."
        carApiStatusText?.setTextColor(0xFF888888.toInt())
    }

    /** 启动车况数据定时轮询（每2秒） */
    private fun startCarDataPolling() {
        if (isCarPollingActive) return
        isCarPollingActive = true
        appendLocalLog("[CarPoll] 启动车况轮询 (间隔2s)")

        carDataRunnable = object : Runnable {
            override fun run() {
                if (!isAdded || activity == null) return
                fetchAndDisplayCarData()
                carDataHandler.postDelayed(this, 2000L)
            }
        }
        carDataHandler.post(carDataRunnable!!)
    }

    /** 停止车况数据轮询 */
    private fun stopCarDataPolling() {
        isCarPollingActive = false
        carDataRunnable?.let { carDataHandler.removeCallbacks(it) }
        carDataRunnable = null
        appendLocalLog("[CarPoll] 停止车况轮询")
    }

    /** 获取并显示车况数据（异步，回调在主线程） */
    private fun fetchAndDisplayCarData() {
        BydLocalCarApi.fetchCarDataAsync { data ->
            updateCarDataUI(data)
        }
    }

    /** 更新车况数据显示到UI */
    @Suppress("DEPRECATION")
    private fun updateCarDataUI(data: BydLocalCarApi.CarData) {
        try {
            if (data.success) {
                carSpeedText?.text = data.formatSpeed()
                carSocText?.text = data.formatSoc()
                carGearText?.text = data.formatGear()
                carElecRemainText?.text = data.formatElecRemain()
                carFuelRemainText?.text = data.formatFuelRemain()
                carOutTempText?.text = data.formatOutTemp()
                carInTempText?.text = data.formatInTemp()
                carAcStatusText?.text = data.formatAcStatus()
                carMileageText?.text = if (data.mileage > 0) "${data.mileage} km" else "--"
                carPowerModeText?.text = data.powerMode.ifEmpty { "-" }
                val port = BydLocalCarApi.getCurrentPort()
                carApiStatusText?.text = "OK (端口$port)"
                carApiStatusText?.setTextColor(0xFF00AA00.toInt())
            } else {
                appendLocalLog("[CarAPI] UI显示失败: ${data.errorMsg}")
                carApiStatusText?.text = "失败: ${data.errorMsg}"
                carApiStatusText?.setTextColor(0xFFFF5500.toInt())
            }
        } catch (e: Exception) {
            appendLocalLog("[CarData] UI更新异常: ${e.message}")
        }
    }

    /**
     * Network文字点击计数 - 连续5次显示Debug Log
     */
    private fun onNetworkClicked() {
        val now = System.currentTimeMillis()
        if (now - lastNetworkClickTime > 2000) {
            networkClickCount = 0
        }
        lastNetworkClickTime = now
        networkClickCount++
        if (networkClickCount >= 5) {
            networkClickCount = 0
            showDebugLog()
        }
    }

    /** 显示 Debug Log 容器 */
    fun showDebugLog() {
        mDebugLogContainer?.visibility = View.VISIBLE
        Toast.makeText(context, "Debug Log shown", Toast.LENGTH_SHORT).show()
        appendLocalLog("=== Debug Log opened ===")
    }

    /** 关闭 Debug Log 容器 */
    fun closeDebugLog() {
        mDebugLogContainer?.visibility = View.GONE
        appendLocalLog("=== Debug Log closed ===")
    }

    /** 写入本地日志到Debug Log容器 */
    private fun appendLocalLog(message: String) {
        activity?.runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logBuilder.insert(0, "[$timestamp] $message\n")
            if (logBuilder.length > 2000) {
                logBuilder.setLength(2000)
            }
            mDebugLogText?.text = logBuilder.toString()
        }
    }

    override fun onResume() {
        super.onResume()
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        fragmentActivity?.registerReceiver(batteryReceiver, batteryFilter)

        val networkFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        fragmentActivity?.registerReceiver(networkReceiver, networkFilter)

        // 恢复车况数据轮询
        startCarDataPolling()
    }

    override fun onPause() {
        super.onPause()
        // 暂停车况数据轮询
        stopCarDataPolling()

        try {
            fragmentActivity?.unregisterReceiver(batteryReceiver)
            fragmentActivity?.unregisterReceiver(networkReceiver)
        } catch (e: Exception) {
        }
    }

    private fun updateMqttStatus(connected: Boolean) {
        if (connected) {
            mqttStatusText?.text = "Connected"
            mqttStatusText?.setTextColor(0xFF00FF00.toInt())
        } else {
            mqttStatusText?.text = "Disconnected"
            mqttStatusText?.setTextColor(0xFFFF0000.toInt())
        }
    }

    private fun updateNetworkInfo() {
        val connectivityManager = fragmentActivity?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        connectivityManager?.let { cm ->
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)

            val networkType = when {
                capabilities == null -> "No Connection"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Unknown"
            }
            networkTypeText?.text = networkType

            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val wifiManager = fragmentActivity?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiInfo = wifiManager?.connectionInfo
                val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Unknown"
                wifiNameText?.text = if (ssid == "<unknown ssid>") "Unknown" else ssid
            } else {
                wifiNameText?.text = "--"
            }
        } ?: run {
            networkTypeText?.text = "Unknown"
            wifiNameText?.text = "--"
        }
    }

    private fun updateScreenInfo() {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        screenInfoText?.text = "${width}x${height} (@${density}dpi)"
    }

    private fun updateOrientation() {
        val orientation = resources.configuration.orientation
        orientationText?.text = when (orientation) {
            android.content.res.Configuration.ORIENTATION_PORTRAIT -> "Portrait"
            android.content.res.Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
            else -> "Unknown"
        }
    }

    private fun updateAndroidVersion() {
        androidVersionText?.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    companion object {
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }
}
