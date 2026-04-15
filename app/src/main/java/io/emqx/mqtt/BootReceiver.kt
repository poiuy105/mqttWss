package io.emqx.mqtt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, checking auto-start...")

            val configManager = ConfigManager.getInstance(context)
            if (configManager.autoStart && configManager.hasSavedConfig()) {
                Log.d("BootReceiver", "Auto-start enabled, launching MainActivity...")
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("auto_connect", true)
                }
                context.startActivity(launchIntent)
            } else {
                Log.d("BootReceiver", "Auto-start disabled or no saved config")
            }
        }
    }
}