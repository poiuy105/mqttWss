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
