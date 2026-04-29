package io.emqx.mqtt

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class VoiceAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: VoiceAccessibilityService? = null
        private const val THROTTLE_DELAY_MS = 300L
        private const val MAX_DEPTH = 12
        private const val MIN_TEXT_LENGTH = 1

        /** 无障碍服务完整组件名（用于ADB写入和自检） */
        const val SERVICE_COMPONENT = "io.emqx.mqtt/.VoiceAccessibilityService"

        /** 前台通知相关 */
        private const val CHANNEL_ID = "byd_acc_service"
        private const val NOTIFY_ID = 10086

        /** 自检间隔：1分钟（优化后，从10分钟缩短） */
        private const val CHECK_INTERVAL_MS = 1 * 60 * 1000L

        fun getInstance(): VoiceAccessibilityService? = instance
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastProcessedTime = 0L
    private var pendingEvent: AccessibilityEvent? = null
    private val processRunnable = Runnable { processPendingEvent() }
    private var lastCapturedHash = 0

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ========== 比亚迪车机保活：前台服务（API29强制要求）==========
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            startForeground(NOTIFY_ID, buildNotification())
        }

        // ========== 定时自检无障碍状态（防系统偶发抹除）==========
        startAccStatusCheck()
    }

    /** 创建通知渠道（安卓O+必须，低优先级不弹窗打扰） */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.acc_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.acc_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /** 构建常驻前台通知 */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.acc_notify_title))
            .setContentText(getString(R.string.acc_notify_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    /**
     * 循环自检 + 自动恢复：检测系统是否意外关闭了本服务的无障碍开关。
     *
     * 双模式策略：
     * - **API 29（Android 10）**: 检测到被抹除 → 写SP标记 → 引导用户重走ADB
     * - **API 34（Android 14+）**: 检测到被抹除 → 尝试通过WRITE_SECURE_SETTINGS自行恢复 → 失败才写SP标记
     *
     * 前提：Android 14 需要先通过 ADB 执行：
     *   adb shell pm grant io.emqx.mqtt android.permission.WRITE_SECURE_SETTINGS
     */
    private fun startAccStatusCheck() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                try {
                    // ⭐ 优化：使用AccessibilityUtils统一检测
                    val isCurrentlyEnabled = AccessibilityUtils.isServiceEnabled(this@VoiceAccessibilityService)

                    if (!isCurrentlyEnabled) {
                        // 系统抹除了我们的无障碍权限！
                        // ★★★ 尝试自动恢复（仅当App持有 WRITE_SECURE_SETTINGS 权限时有效）★★★
                        val restored = tryAutoRestoreAccessibility()

                        if (!restored) {
                            // 自动恢复失败 → 记录到SP供MainActivity引导用户修复
                            val sp = getSharedPreferences("a11y_status", MODE_PRIVATE)
                            sp.edit().putBoolean("a11y_was_reset", true).apply()
                            android.util.Log.w(
                                "A11yService",
                                "⚠️ Accessibility reset by system, auto-restore failed. Need ADB re-lock."
                            )
                        } else {
                            android.util.Log.i(
                                "A11yService",
                                "✅ Auto-restored accessibility via WRITE_SECURE_SETTINGS!"
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("A11yService", "Self-check failed: ${e.message}")
                }
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }, 5000) // 首次延迟5秒后开始自检
    }

    /**
     * 尝试通过 WRITE_SECURE_SETTINGS 自动恢复无障碍权限
     *
     * 仅在以下条件满足时有效：
     * 1. App已被授予 android.permission.WRITE_SECURE_SETTINGS（ADB命令）
     * 2. 用户之前手动授权过一次无障碍服务
     *
     * @return true=恢复成功, false=恢复失败(无权限或其他异常)
     */
    private fun tryAutoRestoreAccessibility(): Boolean {
        return try {
            // 尝试直接写入Secure Settings（需要WRITE_SECURE_SETTINGS权限）
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                SERVICE_COMPONENT
            )
            // 同时确保全局无障碍开关是开着的
            Settings.Secure.putInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
            // 验证写入结果
            val verify = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            verify?.contains(SERVICE_COMPONENT) == true
        } catch (e: SecurityException) {
            // 没有WRITE_SECURE_SETTINGS权限 → 正常情况，走SP兜底逻辑
            android.util.Log.d("A11yService", "No WRITE_SECURE_SETTINGS permission, using fallback mode")
            false
        } catch (e: Exception) {
            android.util.Log.e("A11yService", "Auto-restore error: ${e.message}")
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(processRunnable)
        handler.removeCallbacksAndMessages(null) // 清除所有自检任务
        pendingEvent?.recycle()
        instance = null
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 200
        }
        serviceInfo = info

        // 服务连接成功，标记"上次运行时是开着的"
        getSharedPreferences("a11y_status", MODE_PRIVATE)
            .edit().putBoolean("a11y_was_enabled", true).apply()
        
        // ⭐ 新增：自动恢复CapturedTextManager的启用状态（重启后关键修复）
        CapturedTextManager.isEnabled = true
        android.util.Log.i("A11yService", "✅ Accessibility connected, auto-enabled text capture")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return

        if (!CapturedTextManager.isEnabled) return
        if (CapturedTextManager.shouldIgnorePackage(packageName)) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime < THROTTLE_DELAY_MS) {
            pendingEvent?.recycle()
            pendingEvent = AccessibilityEvent.obtain(event)
            handler.removeCallbacks(processRunnable)
            handler.postDelayed(processRunnable, THROTTLE_DELAY_MS)
            return
        }

        lastProcessedTime = currentTime
        processEvent(event)
    }

    private fun processPendingEvent() {
        pendingEvent?.let { event ->
            processEvent(event)
            pendingEvent = null
        }
    }

    private fun processEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        val capturedList = mutableListOf<Triple<String, Rect, Pair<Int, String>>>()

        event.source?.let { rootNode ->
            try {
                extractTextWithPosition(rootNode, packageName, 0, capturedList)
            } finally {
                rootNode.recycle()
            }
        }

        for (captured in capturedList) {
            val textHash = captured.first.hashCode()
            if (textHash != lastCapturedHash) {
                lastCapturedHash = textHash
                CapturedTextManager.onTextCaptured(
                    text = captured.first,
                    packageName = packageName,
                    boundsLeft = captured.second.left,
                    boundsTop = captured.second.top,
                    boundsRight = captured.second.right,
                    boundsBottom = captured.second.bottom,
                    viewDepth = captured.third.first,
                    viewClass = captured.third.second
                )
            }
        }
    }

    private fun extractTextWithPosition(
        node: AccessibilityNodeInfo?,
        packageName: String,
        depth: Int,
        result: MutableList<Triple<String, Rect, Pair<Int, String>>>
    ) {
        if (depth > MAX_DEPTH) return
        node ?: return

        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (text.isNotBlank() && text.length >= MIN_TEXT_LENGTH) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val viewClass = node.className?.toString() ?: ""
            if (bounds.left >= 0 && bounds.top >= 0 && bounds.width() > 5 && bounds.height() > 5) {
                result.add(Triple(text.trim(), Rect(bounds), Pair(depth, viewClass)))
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                extractTextWithPosition(child, packageName, depth + 1, result)
            } finally {
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {}

    fun captureCurrentScreen() {
        val rootNode = rootInActiveWindow ?: return
        val packageName = rootNode.packageName?.toString() ?: "unknown"
        val capturedList = mutableListOf<Triple<String, Rect, Pair<Int, String>>>()

        extractTextWithPosition(rootNode, packageName, 0, capturedList)
        rootNode.recycle()

        capturedList.forEach { (text, bounds, viewInfo) ->
            CapturedTextManager.onTextCaptured(
                text = text,
                packageName = packageName,
                boundsLeft = bounds.left,
                boundsTop = bounds.top,
                boundsRight = bounds.right,
                boundsBottom = bounds.bottom,
                viewDepth = viewInfo.first,
                viewClass = viewInfo.second
            )
        }
    }

    // ========== 智能返回相关方法 ==========

    /**
     * 检查屏幕上是否包含指定文本（用于HA响应后判断是否需要返回）
     * @param targetText 要查找的文本
     * @param maxDepth 最大遍历深度（避免性能问题，默认5）
     * @return true=找到，false=未找到
     */
    fun isTextOnScreen(targetText: String, maxDepth: Int = 5): Boolean {
        if (targetText.isBlank()) {
            android.util.Log.w("A11yService", "isTextOnScreen: empty target text")
            return false
        }

        val rootNode = rootInActiveWindow ?: run {
            android.util.Log.w("A11yService", "isTextOnScreen: no active window")
            return false
        }

        try {
            val found = findTextInNode(rootNode, targetText, 0, maxDepth)
            android.util.Log.d("A11yService", "isTextOnScreen: '$targetText' -> ${if (found) "FOUND" else "NOT FOUND"}")
            return found
        } catch (e: Exception) {
            android.util.Log.e("A11yService", "isTextOnScreen error: ${e.message}", e)
            return false
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 递归查找文本节点
     */
    private fun findTextInNode(
        node: AccessibilityNodeInfo?,
        target: String,
        currentDepth: Int,
        maxDepth: Int
    ): Boolean {
        if (node == null || currentDepth > maxDepth) {
            return false
        }

        // 1. 检查当前节点的 text
        node.text?.let {
            val nodeText = it.toString().trim()
            if (nodeText.isNotEmpty() && nodeText.contains(target, ignoreCase = true)) {
                android.util.Log.d("A11yService", "Found in text: '$nodeText'")
                return true
            }
        }

        // 2. 检查 contentDescription
        node.contentDescription?.let {
            val desc = it.toString().trim()
            if (desc.isNotEmpty() && desc.contains(target, ignoreCase = true)) {
                android.util.Log.d("A11yService", "Found in description: '$desc'")
                return true
            }
        }

        // 3. 递归检查子节点
        val childCount = node.childCount
        if (childCount > 0 && childCount < 200) {  // 限制子节点数量
            for (i in 0 until childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    if (findTextInNode(child, target, currentDepth + 1, maxDepth)) {
                        child.recycle()
                        return true
                    }
                    child.recycle()
                }
            }
        }

        return false
    }

    /**
     * 执行系统级返回（模拟返回键）
     * @return true=成功，false=失败
     */
    fun performGlobalBack(): Boolean {
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        android.util.Log.d("A11yService", "performGlobalBack: ${if (success) "SUCCESS" else "FAILED"}")
        return success
    }

    /**
     * 执行系统级Home键
     */
    fun performGlobalHome(): Boolean {
        val success = performGlobalAction(GLOBAL_ACTION_HOME)
        android.util.Log.d("A11yService", "performGlobalHome: ${if (success) "SUCCESS" else "FAILED"}")
        return success
    }
}
