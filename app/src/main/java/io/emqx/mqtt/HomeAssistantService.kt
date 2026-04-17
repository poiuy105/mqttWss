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

    fun sendCommand(context: Context, text: String, callback: (Boolean, String?) -> Unit) {
        val configManager = ConfigManager.getInstance(context)
        val haAddress = configManager.haAddress
        val haToken = configManager.haToken
        val haLanguage = configManager.haLanguage
        val useHttps = configManager.haHttps

        if (haAddress.isBlank() || haToken.isBlank()) {
            callback(false, "Home Assistant configuration is incomplete")
            return
        }

        val protocol = if (useHttps) "https" else "http"
        val url = "$protocol://$haAddress/api/conversation/process"

        val json = JSONObject()
        json.put("text", text)
        json.put("language", haLanguage)

        val requestBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $haToken")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to send command to Home Assistant: ${e.message}")
                callback(false, "Failed to connect to Home Assistant: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val speech = jsonResponse.optJSONObject("response")
                            ?.optJSONObject("speech")
                            ?.optJSONObject("plain")
                            ?.optString("speech")
                        callback(true, speech)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse Home Assistant response: ${e.message}")
                        callback(false, "Failed to parse Home Assistant response: ${e.message}")
                    }
                } else {
                    Log.e(TAG, "Home Assistant returned error: ${response.code} - ${responseBody}")
                    callback(false, "Home Assistant returned error: ${response.code}")
                }
            }
        })
    }
}
