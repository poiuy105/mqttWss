package io.emqx.mqtt

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class FloatWindowManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var payloadTextView: TextView? = null
    private var autoCloseRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        @Volatile
        private var instance: FloatWindowManager? = null
        private const val AUTO_DISMISS_DELAY = 3000L

        fun getInstance(context: Context): FloatWindowManager {
            return instance ?: synchronized(this) {
                instance ?: FloatWindowManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    fun showMessage(topic: String, payload: String) {
        if (!canDrawOverlays()) {
            return
        }

        try {
            if (floatView == null) {
                createFloatWindow(topic, payload)
            } else {
                updatePayload(payload)
            }
            resetAutoDismiss()
        } catch (e: Exception) {
            Log.e("FloatWindow", "Error: ${e.message}")
        }
    }

    private fun createFloatWindow(topic: String, payload: String) {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val windowWidth = screenWidth / 3

        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = windowWidth
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 100
        }

        floatView = createFloatView(topic, payload)
        windowManager?.addView(floatView, layoutParams)
        Log.d("FloatWindow", "Float window created")
    }

    private fun createFloatView(topic: String, payload: String): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD2D2D3D.toInt())
            setPadding(16, 12, 16, 12)
            elevation = 8f
        }

        val topicView = TextView(context).apply {
            text = topic
            setTextColor(0xFF00BCD4.toInt())
            textSize = 14f
            maxLines = 1
        }

        payloadTextView = TextView(context).apply {
            text = payload
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            maxLines = 3
        }

        container.addView(topicView)
        container.addView(payloadTextView)

        return container
    }

    private fun updatePayload(payload: String) {
        payloadTextView?.text = payload
    }

    private fun resetAutoDismiss() {
        autoCloseRunnable?.let { handler.removeCallbacks(it) }
        autoCloseRunnable = Runnable { hide() }
        handler.postDelayed(autoCloseRunnable!!, AUTO_DISMISS_DELAY)
    }

    fun hide() {
        autoCloseRunnable?.let { handler.removeCallbacks(it) }
        floatView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e("FloatWindow", "Error removing: ${e.message}")
            }
            floatView = null
            payloadTextView = null
        }
    }

    fun isShowing(): Boolean = floatView != null
}
