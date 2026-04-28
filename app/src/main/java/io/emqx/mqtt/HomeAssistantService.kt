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

        // ⭐ 新增：POST发出后立即启动智能返回监控（不等待response）
        val clickCount = configManager.haClickCount.coerceAtLeast(1)
        if (configManager.haClickBackEnabled) {
            appendLog(context, "🚀 Starting smart return monitor immediately after POST")
            startSmartReturnMonitor(context, text, clickCount, responseDelay)
        }

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                appendLog(context, "HTTP Request FAILED: ${e.message}")
                callback(false, "Failed to connect to Home Assistant: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                appendLog(context, "HTTP Response received - Status: ${response.code}")
                val responseBody = response.body?.string()
                appendLog(context, "Response Body: $responseBody")
                                            
                // ⭐ 删除：不再在response后执行返回操作（已提前到POST发出时）
                            
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
     * ⭐ 新增：立即启动智能返回监控线程
     * 在POST发出后立即开始，每次返回前都检查UI状态
     */
    private fun startSmartReturnMonitor(
        context: Context,
        targetText: String,
        clickCount: Int,
        delayMs: Int
    ) {
        Thread {
            try {
                Log.d(TAG, "🎯 Smart return monitor started: clickCount=$clickCount, delay=${delayMs}ms")
                
                for (i in 1..clickCount) {
                    // ⭐ 第1次立即执行，后续每次先延迟再检查（延迟时间由Setting界面的response delay控制）
                    if (i > 1) {
                        Log.d(TAG, "⏱️ Waiting ${delayMs}ms before action $i/$clickCount")
                        Thread.sleep(delayMs.toLong())
                    }
                    
                    // ⭐ 每次返回前都检查屏幕状态
                    val shouldReturn = checkScreenAndDecide(context, targetText)
                    
                    if (shouldReturn) {
                        Log.d(TAG, "✅ Executing return action $i/$clickCount")
                        performSingleReturnAction(context)
                        
                        // 记录日志
                        appendLog(context, "Return action $i/$clickCount executed")
                    } else {
                        Log.d(TAG, "❌ Text cleared by HA, skip return action $i/$clickCount but continue monitoring")
                        appendLog(context, "Screen cleared, skip action $i/$clickCount (continue monitoring)")
                        // ⭐ 不break，继续循环以检测文本是否重新出现
                    }
                }
                
                Log.d(TAG, "🏁 Smart return monitor completed")
            } catch (e: Exception) {
                Log.e(TAG, "Smart return monitor error: ${e.message}", e)
                appendLog(context, "Smart return error: ${e.message}")
            }
        }.start()
    }
    
    /**
     * ⭐ 新增：检查屏幕并决定是否执行返回
     * @return true=应该执行返回，false=跳过（屏幕已清空）
     */
    private fun checkScreenAndDecide(context: Context, targetText: String): Boolean {
        // 如果没有目标文本，直接返回true（执行返回）
        if (targetText.isBlank()) {
            Log.d(TAG, "No target text, perform return anyway")
            return true
        }
        
        // 获取无障碍服务实例
        val a11yService = VoiceAccessibilityService.getInstance()
        if (a11yService == null) {
            Log.w(TAG, "Accessibility service not available, perform return anyway")
            return true
        }
        
        // ⭐ 关键检查：屏幕上是否还有目标文本
        val textFound = a11yService.isTextOnScreen(targetText, maxDepth = 5)
        
        if (textFound) {
            Log.d(TAG, "✅ Text '$targetText' still on screen, should return")
            return true
        } else {
            Log.d(TAG, "❌ Text '$targetText' NOT on screen, skip return")
            return false
        }
    }
    
    /**
     * ⭐ 新增：执行单次返回操作
     */
    private fun performSingleReturnAction(context: Context) {
        val a11yService = VoiceAccessibilityService.getInstance()
        
        // 优先使用无障碍全局返回
        if (a11yService?.performGlobalBack() == true) {
            Log.d(TAG, "Global back action executed")
        } else {
            // 降级为模拟点击
            Log.w(TAG, "Global back failed, fallback to simulate click")
            (context as? MainActivity)?.let { activity ->
                MainActivity.simulateClickBack(activity)
            }
        }
    }
}
