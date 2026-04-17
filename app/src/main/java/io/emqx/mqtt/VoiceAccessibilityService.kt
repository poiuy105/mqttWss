package io.emqx.mqtt

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VoiceAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: VoiceAccessibilityService? = null
        private const val THROTTLE_DELAY_MS = 300L
        private const val MAX_DEPTH = 12
        private const val MIN_TEXT_LENGTH = 1

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
        // 初始化 CapturedTextManager
        CapturedTextManager.init(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(processRunnable)
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
}
