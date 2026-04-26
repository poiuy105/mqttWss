package io.emqx.mqtt

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object HomeAssistantService {
    private const val TAG = "HomeAssistantService"
    
    // 存储最近POST的文本，用于后续屏幕检查
    @Volatile
    var lastPostedText: String? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun appendLog(context: Context, message: String) {
        (context as? MainActivity)?.appendLog("[HA] $message") ?: Log.d(TAG, message)
    }

    fun sendCommand(context: Context, text: String, callback: (Boolean, String?) -> Unit) {
        // 保存POST的文本，用于后续屏幕检查
        lastPostedText = text
        
        val configManager = ConfigManager.getInstance(context)
        val haAddress = configManager.haAddress
        val haToken = configManager.haToken
        val haLanguage = configManager.haLanguage
        val useHttps = configManager.haHttps
        val responseDelay = configManager.haResponseDelay.coerceIn(10, 2000) // 10-2000ms

        if (haAddress.isBlank() || haToken.isBlank()) {
            appendLog(context, "Configuration incomplete - haAddress or haToken is empty")
            callback(false, "Home Assistant configuration is incomplete")
            return
        }

        val protocol = if (useHttps) "https" else "http"
        val url = "$protocol://$haAddress/api/conversation/process"

        appendLog(context, "Preparing HTTP POST request")
        appendLog(context, "URL: $url")

        val json = JSONObject()
        json.put("text", text)
        json.put("language", haLanguage)

        val requestBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        appendLog(context, "Request Headers:")
        appendLog(context, "  Authorization: Bearer ${haToken.take(10)}...")
        appendLog(context, "  Content-Type: application/json")
        appendLog(context, "Request Body: ${json.toString()}")
        appendLog(context, "curl command: curl -X POST $url -H \"Authorization: Bearer $haToken\" -H \"Content-Type: application/json\" -d '${json.toString()}'")

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $haToken")
            .addHeader("Content-Type", "application/json")
            .build()

        appendLog(context, "Executing HTTP request...")

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                appendLog(context, "HTTP Request FAILED: ${e.message}")
                callback(false, "Failed to connect to Home Assistant: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                appendLog(context, "HTTP Response received - Status: ${response.code}")
                val responseBody = response.body?.string()
                appendLog(context, "Response Body: $responseBody")
                                        
                // 应用响应延迟（模拟系统返回延迟）
                if (responseDelay > 0) {
                    appendLog(context, "Applying response delay: ${responseDelay}ms")
                    Thread.sleep(responseDelay.toLong())
                }
                                        
                // ⭐ 智能检查：如果启用了“单击替代返回”功能
                val configManager = ConfigManager.getInstance(context)
                if (configManager.haClickBackEnabled) {
                    val clickCount = configManager.haClickCount.coerceAtLeast(1)
                                
                    // 检查屏幕并执行智能返回
                    val actionExecuted = checkAndPerformSmartReturn(
                        context = context,
                        targetText = lastPostedText,
                        clickCount = clickCount,
                        responseDelay = responseDelay
                    )
                                
                    if (actionExecuted) {
                        appendLog(context, "✅ Smart return executed ($clickCount times)")
                    } else {
                        appendLog(context, "ℹ️ Screen cleared by HA, skip return")
                    }
                }
                            
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val speech = jsonResponse.optJSONObject("response")
                            ?.optJSONObject("speech")
                            ?.optJSONObject("plain")
                            ?.optString("speech")
                        appendLog(context, "Parsed speech response: $speech")
                        callback(true, speech)
                    } catch (e: Exception) {
                        appendLog(context, "JSON Parse FAILED: ${e.message}")
                        callback(false, "Failed to parse Home Assistant response: ${e.message}")
                    }
                } else {
                    appendLog(context, "HTTP Error: ${response.code} - $responseBody")
                    callback(false, "Home Assistant returned error: ${response.code}")
                }
            }
        })
    }
    
    /**
     * 智能检查屏幕并执行返回操作
     * @return true=执行了操作，false=跳过（屏幕已清空）
     */
    private fun checkAndPerformSmartReturn(
        context: Context,
        targetText: String?,
        clickCount: Int,
        responseDelay: Int
    ): Boolean {
        // 如果没有目标文本，直接执行返回
        if (targetText.isNullOrBlank()) {
            Log.d(TAG, "No target text, performing return anyway")
            performReturnActions(context, clickCount, responseDelay)
            return true
        }
        
        // 获取无障碍服务实例
        val a11yService = VoiceAccessibilityService.getInstance()
        if (a11yService == null) {
            Log.w(TAG, "Accessibility service not available, performing return anyway")
            performReturnActions(context, clickCount, responseDelay)
            return true
        }
        
        // ⭐ 关键检查：屏幕上是否还有目标文本
        val textFound = a11yService.isTextOnScreen(targetText, maxDepth = 5)
        
        if (textFound) {
            Log.d(TAG, "✅ Text '$targetText' still on screen, executing return")
            appendLog(context, "Text found, executing $clickCount return action(s)")
            performReturnActions(context, clickCount, responseDelay)
            return true
        } else {
            Log.d(TAG, "❌ Text '$targetText' NOT on screen, skip return")
            appendLog(context, "Text not found (HA auto-cleared), skip return")
            return false
        }
    }
    
    /**
     * 执行返回操作（支持多次）
     */
    private fun performReturnActions(
        context: Context,
        clickCount: Int,
        responseDelay: Int
    ) {
        val a11yService = VoiceAccessibilityService.getInstance()
        
        for (i in 1..clickCount) {
            if (i > 1) {
                Log.d(TAG, "Delay before action $i: ${responseDelay}ms")
                Thread.sleep(responseDelay.toLong())
            }
            
            // 优先使用无障碍全局返回
            if (a11yService?.performGlobalBack() == true) {
                Log.d(TAG, "Global back action $i/$clickCount executed")
                appendLog(context, "Return action $i/$clickCount (system back)")
            } else {
                // 降级为模拟点击
                Log.w(TAG, "Global back failed, fallback to simulate click")
                (context as? MainActivity)?.let { activity ->
                    MainActivity.simulateClickBack(activity)
                    appendLog(context, "Return action $i/$clickCount (simulate click)")
                }
            }
            
            // 多次点击之间的间隔
            if (i < clickCount) {
                Thread.sleep(100)
            }
        }
    }
}
