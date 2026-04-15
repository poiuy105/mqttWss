package io.emqx.mqtt

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VoiceAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: VoiceAccessibilityService? = null

        fun getInstance(): VoiceAccessibilityService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return
        val capturedList = mutableListOf<Triple<String, Rect, Pair<Int, String>>>()

        event.source?.let { rootNode ->
            extractTextWithPosition(rootNode, packageName, 0, capturedList)
            rootNode.recycle()
        }

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

    private fun extractTextWithPosition(
        node: AccessibilityNodeInfo?,
        packageName: String,
        depth: Int,
        result: MutableList<Triple<String, Rect, Pair<Int, String>>>
    ) {
        node ?: return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        val viewClass = node.className?.toString() ?: ""

        if (text.isNotEmpty() && text.length > 1) {
            result.add(Triple(text, Rect(bounds), Pair(depth, viewClass)))
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractTextWithPosition(child, packageName, depth + 1, result)
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
