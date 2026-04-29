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
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : BaseFragment() {
    private var batteryLevelText: TextView? = null
    private var batteryStatusText: TextView? = null
    private var mqttStatusText: TextView? = null
    private var mqttStatusIndicator: View? = null  // ⭐ 新增：MQTT状态指示器
    private var networkTypeText: TextView? = null
    private var wifiNameText: TextView? = null
    private var screenInfoText: TextView? = null
    private var orientationText: TextView? = null
    private var androidVersionText: TextView? = null
    private var networkTitleText: TextView? = null
    private var deviceDetailsText: TextView? = null

    // ========== Debug Log（仅在Home页签显示）==========
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
        mqttStatusIndicator = view.findViewById(R.id.mqtt_status_indicator)  // ⭐ 新增：初始化状态指示器
        networkTypeText = view.findViewById(R.id.network_type)
        wifiNameText = view.findViewById(R.id.wifi_name)
        screenInfoText = view.findViewById(R.id.screen_info)
        orientationText = view.findViewById(R.id.orientation)
        androidVersionText = view.findViewById(R.id.android_version)
        networkTitleText = view.findViewById(R.id.network_title)
        deviceDetailsText = view.findViewById(R.id.device_details)

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
            // ⭐ 修复：初始化MQTT状态（检查实际连接状态）
            val isConnected = main.getMqttClient()?.isConnected == true
            updateMqttStatus(isConnected)

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
        
        // 延迟1秒后获取设备详细信息（确保UI已完全加载）
        Handler(Looper.getMainLooper()).postDelayed({
            updateDeviceInfo()
        }, 1000)

        // 连续点击"Network"文字5次显示Debug Log
        networkTitleText?.setOnClickListener {
            onNetworkClicked()
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
        
        // ⭐ 修复：先注销旧的接收器，防止重复注册
        unregisterReceivers()
        
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        fragmentActivity?.registerReceiver(batteryReceiver, batteryFilter)

        val networkFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        fragmentActivity?.registerReceiver(networkReceiver, networkFilter)
        
        // ⭐ 修复：每次恢复时同步MQTT状态（解决UI与实际连接状态不一致的问题）
        (fragmentActivity as? MainActivity)?.let { main ->
            val isConnected = main.getMqttClient()?.isConnected == true
            updateMqttStatus(isConnected)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceivers()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // ⭐ 修复：确保在视图销毁时也注销接收器，防止内存泄漏
        unregisterReceivers()
    }
    
    /**
     * ⭐ 修复：统一注销广播接收器，改进异常处理
     */
    private fun unregisterReceivers() {
        fragmentActivity?.let { activity ->
            try {
                activity.unregisterReceiver(batteryReceiver)
            } catch (e: IllegalArgumentException) {
                // 接收器未注册，忽略
                Log.d("HomeFragment", "Battery receiver not registered")
            }
            
            try {
                activity.unregisterReceiver(networkReceiver)
            } catch (e: IllegalArgumentException) {
                // 接收器未注册，忽略
                Log.d("HomeFragment", "Network receiver not registered")
            }
        }
    }

    private fun updateMqttStatus(connected: Boolean) {
        if (connected) {
            mqttStatusText?.text = "Connected"
            mqttStatusText?.setTextColor(0xFF00FF00.toInt())
            // ⭐ 修复：更新状态指示器为绿色
            mqttStatusIndicator?.setBackgroundResource(R.drawable.status_dot_green)
        } else {
            mqttStatusText?.text = "Disconnected"
            mqttStatusText?.setTextColor(0xFFFF0000.toInt())
            // ⭐ 修复：更新状态指示器为红色
            mqttStatusIndicator?.setBackgroundResource(R.drawable.status_dot_red)
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

    /**
     * 获取并展示更多设备信息（安全地，不报错）
     */
    private fun updateDeviceInfo() {
        try {
            val infoBuilder = StringBuilder()
            
            // 1. 制造商和型号
            infoBuilder.append("Manufacturer: ${Build.MANUFACTURER}\n")
            infoBuilder.append("Model: ${Build.MODEL}\n")
            infoBuilder.append("Brand: ${Build.BRAND}\n")
            
            // 2. 硬件信息
            infoBuilder.append("Board: ${safeGet { Build.BOARD }}\n")
            infoBuilder.append("Hardware: ${safeGet { Build.HARDWARE }}\n")
            infoBuilder.append("Device: ${safeGet { Build.DEVICE }}\n")
            
            // 3. CPU/ABI 信息
            infoBuilder.append("CPU ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
            
            // 4. 内存信息
            val activityManager = fragmentActivity?.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            if (memInfo != null) {
                val totalMem = memInfo.totalMem / (1024 * 1024)
                val availMem = memInfo.availMem / (1024 * 1024)
                infoBuilder.append("RAM: ${totalMem}MB (可用: ${availMem}MB)\n")
            }
            
            // 5. 存储空间
            val dataDir = android.os.Environment.getDataDirectory()
            val statFs = android.os.StatFs(dataDir.path)
            val totalSpace = statFs.totalBytes / (1024 * 1024)
            val freeSpace = statFs.freeBytes / (1024 * 1024)
            infoBuilder.append("Storage: ${totalSpace}MB (空闲: ${freeSpace}MB)\n")
            
            // 6. 屏幕密度
            val displayMetrics = resources.displayMetrics
            infoBuilder.append("Density: ${displayMetrics.densityDpi}dpi (${displayMetrics.density})\n")
            
            // 7. 系统特性
            infoBuilder.append("Rooted: ${checkRootMethod1() || checkRootMethod2()}\n")
            infoBuilder.append("Debuggable: ${isAppDebuggable()}\n")
            
            // 8. 电池温度（如果可用）
            try {
                val batteryIntent = fragmentActivity?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temp = batteryIntent?.getIntExtra("temperature", -1)
                if (temp != null && temp > 0) {
                    infoBuilder.append("Battery Temp: ${temp / 10.0}°C\n")
                }
            } catch (e: Exception) {
                // 忽略电池温度获取失败
            }
            
            // 9. 网络MAC地址（Android 6+可能不可用）
            try {
                val wifiManager = fragmentActivity?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiInfo = wifiManager?.connectionInfo
                val macAddress = wifiInfo?.macAddress
                if (!macAddress.isNullOrEmpty() && macAddress != "02:00:00:00:00:00") {
                    infoBuilder.append("WiFi MAC: $macAddress\n")
                }
            } catch (e: Exception) {
                // 忽略MAC地址获取失败
            }
            
            // 将信息显示到 Debug Log
            appendLocalLog("=== Device Info ===")
            appendLocalLog(infoBuilder.toString())
            
            // 同时显示到 UI TextView
            deviceDetailsText?.text = infoBuilder.toString()
            
        } catch (e: Exception) {
            appendLocalLog("[DeviceInfo] Error: ${e.message}")
            deviceDetailsText?.text = "Failed to load device info"
        }
    }
    
    /**
     * 安全地获取 Build 字段，避免某些字段在某些设备上不存在
     */
    private fun safeGet(getter: () -> String?): String {
        return try {
            getter() ?: "N/A"
        } catch (e: Exception) {
            "N/A"
        }
    }
    
    /**
     * 检查 Root 方法1
     */
    private fun checkRootMethod1(): Boolean {
        return try {
            File("/system/bin/su").exists() || File("/system/xbin/su").exists()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查 Root 方法2
     */
    private fun checkRootMethod2(): Boolean {
        return try {
            Runtime.getRuntime().exec(arrayOf("which", "su")).waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查应用是否可调试
     */
    private fun isAppDebuggable(): Boolean {
        return try {
            fragmentActivity?.applicationInfo?.let { 
                (it.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }
}
