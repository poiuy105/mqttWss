package io.emqx.mqtt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat.getSystemService

class HomeFragment : BaseFragment() {
    private var batteryLevelText: TextView? = null
    private var batteryStatusText: TextView? = null
    private var mqttStatusText: TextView? = null
    private var networkTypeText: TextView? = null
    private var wifiNameText: TextView? = null
    private var screenInfoText: TextView? = null
    private var orientationText: TextView? = null
    private var androidVersionText: TextView? = null

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
            main.setOnMqttStatusChangedListener { connected ->
                updateMqttStatus(connected)
            }
            updateMqttStatus(main.notConnected(false) == false)
        }

        updateNetworkInfo()
        updateScreenInfo()
        updateOrientation()
        updateAndroidVersion()
    }

    override fun onResume() {
        super.onResume()
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        fragmentActivity?.registerReceiver(batteryReceiver, batteryFilter)

        val networkFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        fragmentActivity?.registerReceiver(networkReceiver, networkFilter)
    }

    override fun onPause() {
        super.onPause()
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
