package io.emqx.mqtt

class CapturedText(
    val text: String,
    val packageName: String,
    val timestamp: Long
) {
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
