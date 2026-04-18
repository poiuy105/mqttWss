package io.emqx.mqtt

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 比亚迪DiLink本地车况数据API工具类
 *
 * 通过车机内网127.0.0.1访问DiLink自带的后台服务获取实时车辆数据。
 * 不需要官方签名、不需要OIP、不需要无障碍、不需要Root、不需要ADB。
 * 仅需普通网络权限(INTERNET) + 明文HTTP支持。
 *
 * 支持的端口（按优先级自动切换）：
 *   8081 → DiLink 4G/5G 主接口（汉/唐/宋/秦/元全系，近3年默认）
 *   8080 → 老款DiLink 4.0 安卓10 备用
 *   9000 → 新款DiLink 5.0 安卓14 专用
 */
object BydLocalCarApi {

    private const val TAG = "BydLocalCarApi"

    /** 日志回调（由HomeFragment设置，输出到Debug Log容器） */
    var logCallback: ((String) -> Unit)? = null

    private fun log(msg: String) {
        Log.d(TAG, msg)
        try { logCallback?.invoke(msg) } catch (_: Exception) {}
    }

    /** 可用端口列表（按优先级排序） */
    val PORTS = intArrayOf(8081, 8080, 9000)

    /** 当前使用的端口 */
    @Volatile
    private var currentPort: Int = PORTS[0]

    /** 基础路径映射 */
    private val PATH_MAP = mapOf(
        8081 to "/dilink/realCarData",
        8080 to "/car/status",
        9000 to "/vehicle/all"
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    // ========== 数据模型 ==========

    data class CarData(
        var speed: Float = 0f,           // 车速 km/h
        var soc: Int = 0,                // 电池SOC %
        var mileage: Int = 0,            // 总里程 km
        var elecRemain: Int = 0,         // 纯电续航 km
        var fuelRemain: Int = 0,         // 燃油续航 km
        var outTemp: Float = 0f,         // 室外温度 ℃
        var inTemp: Float = 0f,          // 车内温度 ℃
        var gear: String = "-",          // 档位 P/R/N/D
        var powerMode: String = "-",     // 上电状态 READY/OFF等
        var avgElec: Float = 0f,         // 百公里电耗
        var avgFuel: Float = 0f,         // 百公里油耗
        var acTemp: Int = 0,             // 空调设定温度 ℃
        var acOn: Boolean = false,       // 空调开关
        var rawJson: String = "",        // 原始JSON（调试用）
        var timestamp: Long = 0L,        // 数据时间戳
        var success: Boolean = false,    // 是否成功获取
        var errorMsg: String = ""        // 错误信息
    ) {
        fun formatSpeed(): String = if (speed < 0.01f) "0" else String.format("%.0f", speed)
        fun formatSoc(): String = "$soc%"
        fun formatGear(): String = gear.ifEmpty { "-" }
        fun formatElecRemain(): String = "$elecRemain km"
        fun formatFuelRemain(): String = "$fuelRemain km"
        fun formatOutTemp(): String = if (outTemp == 0f && !success) "-" else String.format("%.0f℃", outTemp)
        fun formatInTemp(): String = if (inTemp == 0f && !success) "-" else String.format("%.0f℃", inTemp)
        fun formatAcStatus(): String = if (acOn) "$acTemp℃ ON" else "OFF"

        /** 生成一行摘要用于日志输出 */
        fun toSummary(): String = buildString {
            append("speed=${formatSpeed()} soc=${soc}% gear=$gear")
            append(" elec=${elecRemain}km fuel=${fuelRemain}km")
            append(" out=${formatOutTemp()} in=${formatInTemp()}")
            append(" ac=${formatAcStatus()} mode=$powerMode")
            if (mileage > 0) append(" mileage=${mileage}km")
        }
    }

    // ========== 核心请求方法 ==========

    /**
     * 同步获取一次车况数据（在后台线程调用）
     * 自动尝试所有端口，返回第一个成功的结果
     */
    fun fetchCarData(): CarData {
        log("[CarAPI] ===== 开始获取车况数据 =====")
        for ((index, port) in PORTS.withIndex()) {
            val startTime = System.currentTimeMillis()
            log("[CarAPI] 尝试端口 $port (${index + 1}/${PORTS.size})...")
            try {
                val result = tryPort(port)
                val elapsed = System.currentTimeMillis() - startTime
                if (result.success) {
                    currentPort = port
                    log("[CarAPI] ✓ 端口$port 成功! 耗时${elapsed}ms | ${result.toSummary()}")
                    return result
                } else {
                    log("[CarAPI] ✗ 端口$port 失败 (${elapsed}ms): ${result.errorMsg}")
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                log("[CarAPI] ✗ 端口$port 异常 (${elapsed}ms): ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        log("[CarAPI] ===== 所有端口均失败! =====")
        return CarData().apply { success = false; errorMsg = "所有端口均无法连接" }
    }

    /**
     * 异步获取车况数据（回调在主线程）
     */
    fun fetchCarDataAsync(callback: (CarData) -> Unit) {
        Thread {
            val result = fetchCarData()
            mainHandler.post { callback(result) }
        }.start()
    }

    /**
     * 尝试单个端口
     */
    private fun tryPort(port: Int): CarData {
        val path = PATH_MAP[port] ?: "/dilink/realCarData"
        val url = "http://127.0.0.1:$port$path"

        log("[CarAPI] 请求: $url")

        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val bodyPreview = (response.body?.string() ?: "").take(200)
            log("[CarAPI] HTTP ${response.code}: $bodyPreview")
            return CarData().apply {
                success = false
                errorMsg = "HTTP ${response.code}"
                rawJson = bodyPreview
            }
        }

        val bodyStr = response.body?.string() ?: ""
        log("[CarAPI] 响应(${bodyStr.length}B): ${bodyStr.take(300)}")
        return parseJson(bodyStr).apply {
            rawJson = bodyStr
            this.timestamp = System.currentTimeMillis()
        }
    }

    /**
     * 解析JSON响应为CarData
     * 兼容不同版本的接口字段差异
     */
    private fun parseJson(json: String): CarData {
        return try {
            val obj = JSONObject(json)
            val carData = CarData(success = true)

            // 基础字段 — 直接取值，缺失时安全降级
            carData.speed = safeGetFloat(obj, "speed")
            carData.soc = safeGetInt(obj, "soc", "batteryLevel", "battery_soc")
            carData.mileage = safeGetInt(obj, "mileage", "totalMileage", "odometer")
            carData.elecRemain = safeGetInt(obj, "elecRemain", "evRange", "electric_range")
            carData.fuelRemain = safeGetInt(obj, "fuelRemain", "fuelRange", "fuel_range")
            carData.outTemp = safeGetFloat(obj, "outTemp", "outsideTemp", "ambient_temp")
            carData.inTemp = safeGetFloat(obj, "inTemp", "insideTemp", "in_cabin_temp")
            carData.gear = safeGetString(obj, "gear", "gearPosition", "gear_shift") ?: "-"
            carData.powerMode = safeGetString(obj, "powerMode", "power_mode", "vehicleState") ?: "-"
            carData.avgElec = safeGetFloat(obj, "avgElec", "avgPowerConsumption")
            carData.avgFuel = safeGetFloat(obj, "avgFuel", "avgFuelConsumption")

            // AC子对象或扁平字段
            if (obj.has("ac")) {
                val acObj = obj.getJSONObject("ac")
                carData.acTemp = safeGetInt(acObj, "temp", "setTemp", "targetTemp")
                carData.acOn = acObj.optBoolean("on", acObj.optBoolean("acOn", false))
                log("[CarAPI] AC对象: temp=${carData.acTemp} on=${carData.acOn}")
            } else {
                carData.acTemp = safeGetInt(obj, "acTemp", "ac_set_temp")
                carData.acOn = obj.optBoolean("acOn", obj.optBoolean("ac_on", false))
                log("[CarAPI] AC扁平: temp=${carData.acTemp} on=${carData.acOn}")
            }

            // Door子对象
            if (obj.has("door")) {
                log("[CarAPI] 检测到door字段: ${obj.get("door").toString().take(200)}")
            }

            // 记录所有解析到的原始字段名（调试用）
            val keys = obj.keys().asSequence().toList()
            log("[CarAPI] JSON字段列表: ${keys.joinToString(", ")}}")

            carData
        } catch (e: Exception) {
            log("[CarAPI] JSON解析异常! ${e.javaClass.simpleName}: ${e.message}")
            log("[CarAPI] 原始响应: $json")
            CarData().apply {
                success = false
                errorMsg = "解析异常: ${e.javaClass.simpleName}"
                rawJson = json
            }
        }
    }

    // ========== 安全读取方法 ==========

    private fun safeGetFloat(obj: JSONObject, vararg keys: String): Float {
        for (key in keys) {
            if (obj.has(key)) return obj.getDouble(key).toFloat()
        }
        return 0f
    }

    private fun safeGetInt(obj: JSONObject, vararg keys: String): Int {
        for (key in keys) {
            if (obj.has(key)) return obj.getInt(key)
        }
        return 0
    }

    private fun safeGetString(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (obj.has(key)) return obj.getString(key)
        }
        return null
    }

    /** 获取当前生效的端口 */
    fun getCurrentPort(): Int = currentPort

    /** 测试连通性（快速检测） */
    fun testConnection(port: Int = currentPort): Boolean {
        return try {
            val path = PATH_MAP[port] ?: "/dilink/realCarData"
            val url = "http://127.0.0.1:$port$path"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Connection test failed on port $port: ${e.message}")
            false
        }
    }
}
