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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FloatWindowManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())
    private var autoCloseRunnable: Runnable? = null

    companion object {
        @Volatile
        private var instance: FloatWindowManager? = null
        private const val AUTO_CLOSE_DELAY = 5000L

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

    fun showMessage(title: String, message: String, onClick: (() -> Unit)? = null, onClose: (() -> Unit)? = null) {
        Log.d("FloatWindow", "showMessage called: title=$title, message=$message")
        Log.d("FloatWindow", "canDrawOverlays: ${canDrawOverlays()}")

        if (!canDrawOverlays()) {
            Log.e("FloatWindow", "No overlay permission!")
            Toast.makeText(context, "Please grant overlay permission for float window", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        try {
            if (floatView != null) {
                hide()
            }

            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.END
                x = 20
                y = 100
            }

            floatView = createFloatView(title, message, onClick, onClose)
            windowManager?.addView(floatView, layoutParams)
            isShowing = true
            Log.d("FloatWindow", "Float view added successfully")

            floatView?.let { view ->
                val touchListener = FloatTouchListener(layoutParams, view)
                view.setOnTouchListener(touchListener)

                view.alpha = 0f
                view.animate().alpha(1f).setDuration(300).start()
            }

            scheduleAutoClose(onClose)
        } catch (e: Exception) {
            Log.e("FloatWindow", "Error showing float window: ${e.message}")
            e.printStackTrace()
            Toast.makeText(context, "Float window error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFloatView(title: String, message: String, onClick: (() -> Unit)?, onClose: (() -> Unit)?): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD1A1A2E.toInt())
            setPadding(30, 20, 30, 20)
            elevation = 8f
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
        }

        val messageView = TextView(context).apply {
            text = message
            setTextColor(0xCCCCCC.toInt())
            textSize = 14f
            maxLines = 3
        }

        val closeBtn = Button(context).apply {
            text = "X"
            setBackgroundColor(0x66FF4444.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                onClose?.invoke()
                hide()
            }
        }

        container.addView(titleView)
        container.addView(messageView)
        container.addView(closeBtn)

        if (onClick != null) {
            container.setOnClickListener {
                onClick()
                hide()
            }
        }

        return container
    }

    private fun scheduleAutoClose(onClose: (() -> Unit)?) {
        autoCloseRunnable?.let { handler.removeCallbacks(it) }
        autoCloseRunnable = Runnable {
            onClose?.invoke()
            hide()
        }
        handler.postDelayed(autoCloseRunnable!!, AUTO_CLOSE_DELAY)
    }

    fun hide() {
        autoCloseRunnable?.let { handler.removeCallbacks(it) }

        floatView?.let { view ->
            view.animate().alpha(0f).setDuration(200).withEndAction {
                try {
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    Log.e("FloatWindow", "Error removing view: ${e.message}")
                }
                floatView = null
                isShowing = false
            }.start()
        }
    }

    fun isShowing(): Boolean = isShowing

    private inner class FloatTouchListener(
        private val layoutParams: WindowManager.LayoutParams,
        private val view: View
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, layoutParams)
                    return true
                }
            }
            return false
        }
    }
}
