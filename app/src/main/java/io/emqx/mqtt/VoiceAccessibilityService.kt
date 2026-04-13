package io.emqx.mqtt

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VoiceAccessibilityService : AccessibilityService() {
    private var onTextCapturedListener: ((String) -> Unit)? = null

    companion object {
        private var instance: VoiceAccessibilityService? = null

        fun getInstance(): VoiceAccessibilityService? = instance

        fun setOnTextCapturedListener(listener: ((String) -> Unit)?) {
            instance?.onTextCapturedListener = listener
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        onTextCapturedListener = null
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

        val text = extractVoiceText(event)
        if (text.isNotEmpty() && text.length > 2) {
            onTextCapturedListener?.invoke(text)
        }
    }

    private fun extractVoiceText(event: AccessibilityEvent): String {
        val text = StringBuilder()

        event.text?.let { list ->
            for (item in list) {
                if (item.isNotEmpty()) {
                    text.append(item).append(" ")
                }
            }
        }

        event.contentDescription?.let { desc ->
            if (desc.isNotEmpty()) {
                text.append(desc).append(" ")
            }
        }

        event.source?.let { node ->
            text.append(extractTextFromNode(node))
            node.recycle()
        }

        return text.toString().trim()
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        node ?: return ""
        val sb = StringBuilder()

        val text = node.text
        if (!text.isNullOrEmpty()) {
            sb.append(text).append(" ")
        }

        val contentDesc = node.contentDescription
        if (!contentDesc.isNullOrEmpty()) {
            sb.append(contentDesc).append(" ")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                sb.append(extractTextFromNode(child))
                child.recycle()
            }
        }

        return sb.toString()
    }

    override fun onInterrupt() {}

    fun captureCurrentScreen() {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val text = extractTextFromNode(rootNode)
            if (text.isNotEmpty()) {
                onTextCapturedListener?.invoke(text)
            }
            rootNode.recycle()
        }
    }
}
