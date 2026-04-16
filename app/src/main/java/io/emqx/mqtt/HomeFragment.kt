package io.emqx.mqtt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.widget.TextView

class HomeFragment : BaseFragment() {
    private var batteryLevelText: TextView? = null
    private var mqttStatusText: TextView? = null
    private var batteryReceiver: BroadcastReceiver? = null

    override val layoutResId: Int
        get() = R.layout.fragment_home

    override fun setUpView(view: View) {
        batteryLevelText = view.findViewById(R.id.battery_level)
        mqttStatusText = view.findViewById(R.id.mqtt_status)

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra("level", -1) ?: -1
                val scale = intent?.getIntExtra("scale", 100) ?: 100
                if (level >= 0) {
                    val batteryPct = (level * 100) / scale
                    batteryLevelText?.text = "$batteryPct%"
                }
            }
        }

        (fragmentActivity as? MainActivity)?.let { main ->
            main.setOnMqttStatusChangedListener { connected ->
                updateMqttStatus(connected)
            }
            updateMqttStatus(main.notConnected(false) == false)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        fragmentActivity?.registerReceiver(batteryReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        try {
            fragmentActivity?.unregisterReceiver(batteryReceiver)
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

    companion object {
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }
}
