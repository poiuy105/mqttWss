package io.emqx.mqtt

import android.content.Context
import android.widget.Toast

/**
 * Toast 工具类
 * 优化 Toast 时长：SHORT=500ms, LONG=1000ms
 */
object ToastUtils {
    
    /**
     * 显示短时 Toast（0.5秒）
     */
    fun showShort(context: Context, message: String) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast.duration = 500
        toast.show()
    }
    
    /**
     * 显示长时 Toast（1秒）
     */
    fun showLong(context: Context, message: String) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.duration = 1000
        toast.show()
    }
}
