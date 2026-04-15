package io.emqx.mqtt

class CapturedText(
    val text: String,
    val packageName: String,
    val timestamp: Long,
    val boundsLeft: Int = -1,
    val boundsTop: Int = -1,
    val boundsRight: Int = -1,
    val boundsBottom: Int = -1,
    val viewDepth: Int = -1,
    val viewClass: String = ""
) {
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun getBoundsString(): String {
        return if (boundsLeft >= 0) {
            "[$boundsLeft, $boundsTop] - [$boundsRight, $boundsBottom]"
        } else {
            "N/A"
        }
    }

    fun getDepthString(): String {
        return if (viewDepth >= 0) "L$viewDepth" else ""
    }
}
