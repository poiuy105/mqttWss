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
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun appendLog(context: Context, message: String) {
        (context as? MainActivity)?.appendLog("[HA] $message") ?: Log.d(TAG, message)
    }

    fun sendCommand(context: Context, text: String, callback: (Boolean, String?) -> Unit) {
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
                            
                // 如果启用了"单击替代返回"功能，在延时后模拟点击（无论成功失败）
                val configManager = ConfigManager.getInstance(context)
                if (configManager.haClickBackEnabled) {
                    val clickCount = configManager.haClickCount.coerceAtLeast(1)
                    appendLog(context, "Click back enabled, simulating $clickCount click(s) after delay...")
                    (context as? MainActivity)?.let { activity ->
                        // 根据点击次数执行多次点击
                        for (i in 1..clickCount) {
                            if (i > 1) {
                                // 除了第一次，每次点击前再延迟responseDelay
                                appendLog(context, "Delay before click $i: ${responseDelay}ms")
                                Thread.sleep(responseDelay.toLong())
                            }
                            appendLog(context, "Simulating click $i/$clickCount")
                            MainActivity.simulateClickBack(activity)
                            
                            // 如果不是最后一次点击，等待一小段时间确保点击生效
                            if (i < clickCount) {
                                Thread.sleep(100)  // 点击间隔100ms
                            }
                        }
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
}
