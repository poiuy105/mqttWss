package io.emqx.mqtt

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
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
        val capturedList = mutableListOf<Triple<String, IntArray, Pair<Int, String>>>()

        event.source?.let { rootNode ->
            extractTextWithPosition(rootNode, packageName, 0, capturedList)
            rootNode.recycle()
        }

        capturedList.forEach { (text, bounds, viewInfo) ->
            val boundsLeft = bounds[0]
            val boundsTop = bounds[1]
            val boundsRight = bounds[2]
            val boundsBottom = bounds[3]
            CapturedTextManager.onTextCaptured(
                text = text,
                packageName = packageName,
                boundsLeft = boundsLeft,
                boundsTop = boundsTop,
                boundsRight = boundsRight,
                boundsBottom = boundsBottom,
                viewDepth = viewInfo.first,
                viewClass = viewInfo.second
            )
        }
    }

    private fun extractTextWithPosition(
        node: AccessibilityNodeInfo?,
        packageName: String,
        depth: Int,
        result: MutableList<Triple<String, IntArray, Pair<Int, String>>>
    ) {
        node ?: return

        val bounds = IntArray(4)
        node.getBoundsInScreen(bounds)

        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        val viewClass = node.className?.toString() ?: ""

        if (text.isNotEmpty() && text.length > 1) {
            result.add(Triple(text, bounds.copyOf(), Pair(depth, viewClass)))
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
        val capturedList = mutableListOf<Triple<String, IntArray, Pair<Int, Int>>>()

        extractTextWithPosition(rootNode, packageName, 0, capturedList)
        rootNode.recycle()

        capturedList.forEach { (text, bounds, viewInfo) ->
            CapturedTextManager.onTextCaptured(
                text = text,
                packageName = packageName,
                boundsLeft = bounds[0],
                boundsTop = bounds[1],
                boundsRight = bounds[2],
                boundsBottom = bounds[3],
                viewDepth = viewInfo.first,
                viewClass = viewInfo.second
            )
        }
    }
}
