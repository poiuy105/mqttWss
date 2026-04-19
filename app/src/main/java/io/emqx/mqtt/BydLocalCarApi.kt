package io.emqx.mqtt

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.*
import java.util.concurrent.TimeUnit

/**
 * 比亚迪DiLink本地车况数据API — 多策略探测版
 *
 * 针对2021款宋PLUS (DiLink 3.0/4.0, Android 10 API 29) 车机环境，
 * 采用4种策略按优先级尝试获取车辆实时数据：
 *
 * 策略1: 官方BYDAuto SDK反射调用（需要系统签名或共享UID）
 *   - BYDAutoSpeedDevice.getCurrentSpeed() → 车速
 *   - BYDAutoStatisticDevice.getElecPercentageValue() → SOC
 *   - BYDAutoBodyworkDevice.getDoorState() → 门状态
 *
 * 策略2: ADB shell命令读取系统服务（需要ADB权限/shell权限）
 *   - dumpsys byd_vehicle / dumpsys car_service 等
 *
 * 策略3: 广谱HTTP端口扫描（当前策略的增强版）
 *   - 扩大端口范围：8080-8090, 9000-9010, 8888, 9999, 3000, 5000, 5555
 *   - 多种路径尝试：/dilink/path, /car/path, /vehicle/path, /api/path, /status, /
 *   - 增加超时容忍度：连接5秒，读取8秒
 *
 * 策略4: ContentProvider查询（车机内部可能暴露的数据接口）
 *   - com.byd.* / com.bydauto.* 相关ContentProvider
 *
 * 所有探测过程均通过 logCallback 输出到Debug Log容器，
 * 方便实车测试定位哪个策略可用。
 */
object BydLocalCarApi {

    private const val TAG = "BydLocalCarApi"

    /** 日志回调（由HomeFragment设置，输出到Debug Log容器） */
    var logCallback: ((String) -> Unit)? = null

    /** 应用Context（需在初始化时设置） */
    var appContext: Context? = null

    private fun log(msg: String) {
        Log.d(TAG, msg)
        try { logCallback?.invoke(msg) } catch (_: Exception) {}
    }

    // ========== 策略1: 官方SDK类名列表 ==========
    /** 比亚迪官方SDK设备类（按功能分组） */
    private val SDK_CLASSES = mapOf(
        "speed" to listOf(
            "com.byd.vehicle.sdk.BYDAutoSpeedDevice",
            "com.bydauto.vehicle.BYDAutoSpeedDevice",
            "com.byd.dilink.sdk.BYDAutoSpeedDevice",
            "byd.com.vehicle.sdk.BYDAutoSpeedDevice"
        ),
        "statistic" to listOf(
            "com.byd.vehicle.sdk.BYDAutoStatisticDevice",
            "com.bydauto.vehicle.BYDAutoStatisticDevice",
            "com.byd.dilink.sdk.BYDAutoStatisticDevice"
        ),
        "bodywork" to listOf(
            "com.byd.vehicle.sdk.BYDAutoBodyworkDevice",
            "com.bydauto.vehicle.BYDAutoBodyworkDevice",
            "com.byd.dilink.sdk.BYDAutoBodyworkDevice"
        ),
        "energy" to listOf(
            "com.byd.vehicle.sdk.BYDAutoEnergyDevice",
            "com.bydauto.vehicle.BYDAutoEnergyDevice"
        ),
        "ac" to listOf(
            "com.byd.vehicle.sdk.BYDAutoAcDevice",
            "com.bydauto.vehicle.BYDAutoAcDevice"
        )
    )

    // ========== 策略2: ADB shell命令模板 ==========
    private val SHELL_COMMANDS = arrayOf(
        "dumpsys byd_vehicle",
        "dumpsys bydvehicleservice",
        "dumpsys carservice",
        "dumpsys car_service",
        "dumpsys vehicle_service",
        "dumpsys vehicleservice",
        "dumpsys | grep -i byd",
        "dumpsys | grep -i vehicle",
        "dumpsys | grep -i car",
        "service list | grep -i byd",
        "service list | grep -i vehicle",
        "service list | grep -i car",
        "getprop | grep -i byd",
        "getprop ro.product.model",
        "getprop ro.build.display.id",
        "cat /data/data/com.byd.dilink/shared_prefs/*.xml 2>/dev/null; cat '/data/data/com.bydauto.*/shared_prefs/*.xml' 2>/dev/null",
        "settings get system byd_vehicle_info 2>/dev/null",
        "content query --uri 'content://com.byd.*/vehicle' 2>/dev/null",
        "content query --uri 'content://com.bydauto.*/car' 2>/dev/null"
    )

    // ========== 策略3: 广谱端口+路径扫描 ==========
    /** 扫描端口列表（覆盖已知比亚迪端口 + 常见车载端口） */
    val SCAN_PORTS = intArrayOf(
        8081, 8080, 8082, 8083, 8084, 8085,
        9000, 9001, 9002,
        8888, 9999, 3000, 5000, 5555, 6666, 7777,
        8000, 8001, 8002, 8090,
        443, 80, 8086, 8087, 8088,
        9090, 9091, 3001, 5001, 5002,
        4000, 4001, 6000, 7000, 10000
    )

    /** 扫描路径列表（覆盖已知API路径 + 常见RESTful路径） */
    val SCAN_PATHS = arrayOf(
        "/dilink/realCarData",
        "/dilink/vehicle/status",
        "/dilink/carData",
        "/car/status",
        "/car/info",
        "/car/data",
        "/vehicle/status",
        "/vehicle/info",
        "/vehicle/all",
        "/vehicle/realtime",
        "/api/vehicle",
        "/api/car",
        "/api/status",
        "/api/data",
        "/v1/vehicle",
        "/v1/car",
        "/status",
        "/info",
        "/data",
        "/health",
        "/ping",
        "/version",
        "/",
        "/json",
        "/vehicle.json",
        "/carData.json"
    )

    // ========== 策略4: ContentProvider ==========
    private val CONTENT_URIS = arrayOf(
        "content://com.byd.dilink.provider/vehicle",
        "content://com.byd.dilink.provider/car",
        "content://com.byd.dilink.provider/status",
        "content://com.bydauto.provider/vehicle",
        "content://com.bydauto.provider/car",
        "content://com.byd.vehicle.provider/info",
        "content://com.byd.vehicle.provider/data",
        "content://com.byd.carinfo.provider/status",
        "content://com.byd.carinfo.provider/data",
        "content://com.byd.system.provider/vehicle"
    )

    // ========== 当前使用的端口 ==========
    @Volatile
    private var currentPort: Int = 8081

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    // ========== 数据模型 ==========

    data class CarData(
        var speed: Float = 0f,
        var soc: Int = 0,
        var mileage: Int = 0,
        var elecRemain: Int = 0,
        var fuelRemain: Int = 0,
        var outTemp: Float = 0f,
        var inTemp: Float = 0f,
        var gear: String = "-",
        var powerMode: String = "-",
        var avgElec: Float = 0f,
        var avgFuel: Float = 0f,
        var acTemp: Int = 0,
        var acOn: Boolean = false,
        var rawJson: String = "",
        var timestamp: Long = 0L,
        var success: Boolean = false,
        var errorMsg: String = "",
        /** 数据来源标记 */
        var dataSource: String = ""
    ) {
        fun formatSpeed(): String = if (speed < 0.01f) "0" else String.format("%.0f", speed)
        fun formatSoc(): String = "$soc%"
        fun formatGear(): String = gear.ifEmpty { "-" }
        fun formatElecRemain(): String = "$elecRemain km"
        fun formatFuelRemain(): String = "$fuelRemain km"
        fun formatOutTemp(): String = if (outTemp == 0f && !success) "-" else String.format("%.0f℃", outTemp)
        fun formatInTemp(): String = if (inTemp == 0f && !success) "-" else String.format("%.0f℃", inTemp)
        fun formatAcStatus(): String = if (acOn) "$acTemp℃ ON" else "OFF"

        fun toSummary(): String = buildString {
            append("speed=${formatSpeed()} soc=${soc}% gear=$gear")
            append(" elec=${elecRemain}km fuel=${fuelRemain}km")
            append(" out=${formatOutTemp()} in=${formatInTemp()}")
            append(" ac=${formatAcStatus()} mode=$powerMode")
            if (mileage > 0) append(" mileage=${mileage}km")
            append(" [${dataSource}]")
        }
    }

    // ========== 核心请求方法 ==========

    /**
     * 同步获取一次车况数据（在后台线程调用）
     * 按4个策略依次尝试，返回第一个成功的结果
     */
    fun fetchCarData(): CarData {
        log("[CarAPI] ==================== 开始获取车况数据 ====================")

        // 策略1: 尝试官方SDK反射
        log("[CarAPI] --- 策略1/4: 官方BYD SDK反射 ---")
        val sdkResult = trySdkReflection()
        if (sdkResult.success) {
            log("[CarAPI] ✓ SDK反射成功! ${sdkResult.toSummary()}")
            return sdkResult
        }
        log("[CarAPI] ✗ SDK反射失败: ${sdkResult.errorMsg}")

        // 策略2: 尝试ADB shell命令
        log("[CarAPI] --- 策略2/4: ADB Shell命令 ---")
        val adbResult = tryAdbShell()
        if (adbResult.success) {
            log("[CarAPI] ✓ Shell命令成功! ${adbResult.toSummary()}")
            return adbResult
        }
        log("[CarAPI] ✗ Shell命令失败: ${adbResult.errorMsg}")

        // 策略3: HTTP广谱扫描
        log("[CarAPI] --- 策略3/4: HTTP广谱端口扫描 (${SCAN_PORTS.size}端口 x ${SCAN_PATHS.size}路径) ---")
        val httpResult = tryHttpScan()
        if (httpResult.success) {
            log("[CarAPI] ✓ HTTP扫描成功! ${httpResult.toSummary()}")
            return httpResult
        }
        log("[CarAPI] ✗ HTTP扫描全部失败")

        // 策略4: ContentProvider查询
        log("[CarAPI] --- 策略4/4: ContentProvider查询 ---")
        val cpResult = tryContentProvider()
        if (cpResult.success) {
            log("[CarAPI] ✓ ContentProvider成功! ${cpResult.toSummary()}")
            return cpResult
        }
        log("[CarAPI] ✗ ContentProvider失败: ${cpResult.errorMsg}")

        log("[CarAPI] ==================== 全部4种策略均失败! ====================")
        return CarData().apply {
            success = false
            errorMsg = "所有策略均无法获取车辆数据(SDK反射/Shell命令/HTTP扫描/CP查询)"
            dataSource = "NONE"
        }
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

    // ========== 策略1: 官方SDK反射 ==========

    /**
     * 通过反射调用比亚迪官方BYDAuto SDK获取车辆数据
     * 这些SDK类是比亚迪车机系统的核心组件，
     * 但通常需要系统签名或与系统应用共享UID才能调用
     */
    private fun trySdkReflection(): CarData {
        val ctx = appContext ?: return fail("appContext未设置")
        val data = CarData().apply { dataSource = "SDK反射" }

        log("[SDK] 开始尝试反射调用BYD官方SDK...")
        log("[SDK] appContext状态: ${if (ctx != null) "OK" else "NULL"}")

        try {
            // 尝试获取车速
            log("[SDK] --- 尝试获取车速 ---")
            var speedFound = false
            for (className in SDK_CLASSES["speed"]!!) {
                try {
                    log("[SDK] 尝试类: $className")
                    val clazz = Class.forName(className)
                    log("[SDK]   ✓ 类加载成功")
                    val instance = clazz.getMethod("getInstance", Context::class.java).invoke(null, ctx)
                    log("[SDK]   ✓ 获取实例成功")
                    val speed = clazz.getMethod("getCurrentSpeed").invoke(instance)
                    val speedVal = when (speed) {
                        is Double -> speed.toFloat()
                        is Float -> speed
                        is Number -> speed.toFloat()
                        else -> 0f
                    }
                    if (speedVal > 0 || speed != null) {
                        data.speed = speedVal
                        speedFound = true
                        log("[SDK]   ✓ getCurrentSpeed() = $speedVal km/h")
                    } else {
                        log("[SDK]   ✗ 返回值为null或0")
                    }
                } catch (e: ClassNotFoundException) {
                    log("[SDK]   ✗ 类不存在: $className")
                } catch (e: NoSuchMethodException) {
                    log("[SDK]   ✗ 方法不存在: ${e.message}")
                } catch (e: Exception) {
                    log("[SDK]   ✗ 调用异常: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            if (!speedFound) log("[SDK]   ⚠ 所有车速类均失败")

            // 尝试获取SOC/电量
            log("[SDK] --- 尝试获取SOC/电量/续航 ---")
            var socFound = false
            for (className in SDK_CLASSES["statistic"]!!) {
                try {
                    log("[SDK] 尝试类: $className")
                    val clazz = Class.forName(className)
                    log("[SDK]   ✓ 类加载成功")
                    val instance = clazz.getMethod("getInstance", Context::class.java).invoke(null, ctx)
                    log("[SDK]   ✓ 获取实例成功")

                    // 电量百分比
                    try {
                        val soc = clazz.getMethod("getElecPercentageValue").invoke(instance)
                        data.soc = when (soc) {
                            is Double -> soc.toInt()
                            is Int -> soc
                            is Number -> soc.toInt()
                            else -> 0
                        }
                        if (data.soc > 0) {
                            socFound = true
                            log("[SDK]   ✓ getElecPercentageValue() = ${data.soc}%")
                        } else {
                            log("[SDK]   ✗ getElecPercentageValue() 返回0")
                        }
                    } catch (e: Exception) {
                        log("[SDK]   ✗ getElecPercentageValue() 失败: ${e.javaClass.simpleName}")
                    }

                    // 电续航
                    try {
                        val elecRange = clazz.getMethod("getElecDrivingRangeValue").invoke(instance)
                        data.elecRemain = when (elecRange) {
                            is Int -> elecRange
                            is Number -> elecRange.toInt()
                            else -> 0
                        }
                        if (data.elecRemain > 0) {
                            log("[SDK]   ✓ getElecDrivingRangeValue() = ${data.elecRemain}km")
                        }
                    } catch (e: Exception) {
                        log("[SDK]   ✗ getElecDrivingRangeValue() 失败")
                    }

                    // 燃油续航
                    try {
                        val fuelRange = clazz.getMethod("getFuelDrivingRangeValue").invoke(instance)
                        data.fuelRemain = when (fuelRange) {
                            is Int -> fuelRange
                            is Number -> fuelRange.toInt()
                            else -> 0
                        }
                        if (data.fuelRemain > 0) {
                            log("[SDK]   ✓ getFuelDrivingRangeValue() = ${data.fuelRemain}km")
                        }
                    } catch (e: Exception) {
                        log("[SDK]   ✗ getFuelDrivingRangeValue() 失败")
                    }

                    // 总里程
                    try {
                        val mileage = clazz.getMethod("getTotalMileageValue").invoke(instance)
                        data.mileage = when (mileage) {
                            is Int -> mileage
                            is Number -> mileage.toInt()
                            else -> 0
                        }
                        if (data.mileage > 0) {
                            log("[SDK]   ✓ getTotalMileageValue() = ${data.mileage}km")
                        }
                    } catch (e: Exception) {
                        log("[SDK]   ✗ getTotalMileageValue() 失败")
                    }
                } catch (e: ClassNotFoundException) {
                    log("[SDK]   ✗ 类不存在: $className")
                } catch (e: Exception) {
                    log("[SDK]   ✗ 初始化失败: ${e.javaClass.simpleName}")
                }
            }
            if (!socFound) log("[SDK]   ⚠ 所有统计类均失败")

            // 尝试获取车身状态（档位等）
            log("[SDK] --- 尝试获取车身状态 ---")
            for (className in SDK_CLASSES["bodywork"]!!) {
                try {
                    log("[SDK] 尝试类: $className")
                    val clazz = Class.forName(className)
                    log("[SDK]   ✓ 类加载成功")
                    val instance = clazz.getMethod("getInstance", Context::class.java).invoke(null, ctx)
                    log("[SDK]   ✓ 获取实例成功")

                    // 整车状态/电源档位
                    try {
                        val state = clazz.getMethod("getAutoSystemState").invoke(instance)
                        data.powerMode = state?.toString() ?: "-"
                        log("[SDK]   ✓ getAutoSystemState() = $state")
                    } catch (e: Exception) {
                        log("[SDK]   ✗ getAutoSystemState() 失败")
                    }

                    try {
                        val powerLevel = clazz.getMethod("getPowerLevel").invoke(instance)
                        data.gear = powerLevel?.toString() ?: "-"
                        log("[SDK]   ✓ getPowerLevel() = $powerLevel")
                    } catch (e: Exception) {
                        log("[SDK]   ✗ getPowerLevel() 失败")
                    }
                } catch (e: ClassNotFoundException) {
                    log("[SDK]   ✗ 类不存在: $className")
                } catch (e: Exception) {
                    log("[SDK]   ✗ 初始化失败: ${e.javaClass.simpleName}")
                }
            }

            // 尝试获取能量模式
            log("[SDK] --- 尝试获取能量模式 ---")
            for (className in SDK_CLASSES["energy"]!!) {
                try {
                    log("[SDK] 尝试类: $className")
                    val clazz = Class.forName(className)
                    log("[SDK]   ✓ 类加载成功")
                    val instance = clazz.getMethod("getInstance", Context::class.java).invoke(null, ctx)
                    log("[SDK]   ✓ 获取实例成功")
                    try {
                        val mode = clazz.getMethod("getEnergyMode").invoke(instance)
                        data.powerMode = "$data.powerMode/$mode"
                        log("[SDK]   ✓ getEnergyMode() = $mode")
                    } catch (e: Exception) {
                        log("[SDK]   ✗ getEnergyMode() 失败")
                    }
                } catch (e: ClassNotFoundException) {
                    log("[SDK]   ✗ 类不存在: $className")
                } catch (e: Exception) {
                    log("[SDK]   ✗ 初始化失败")
                }
            }

            // 判断是否获取到了有效数据
            if (data.speed > 0 || data.soc > 0 || data.elecRemain > 0 ||
                data.fuelRemain > 0 || data.mileage > 0 || data.powerMode != "-") {
                data.success = true
                data.timestamp = System.currentTimeMillis()
                return data
            }

            return fail("SDK类存在但未返回有效数据(可能缺少系统签名)")
        } catch (e: Exception) {
            return fail("SDK反射异常: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ========== 策略2: ADB Shell ==========

    /**
     * 通过执行shell命令获取车辆信息
     * 需要应用有shell权限或已获得root/shell用户身份
     */
    private fun tryAdbShell(): CarData {
        val data = CarData().apply { dataSource = "Shell命令" }
        var foundUsefulInfo = false

        log("[Shell] 开始执行ADB Shell命令探测...")
        log("[Shell] 共${SHELL_COMMANDS.size}个命令待测试")

        for ((index, cmd) in SHELL_COMMANDS.withIndex()) {
            try {
                log("[Shell] [$((index+1))/${SHELL_COMMANDS.size}] 执行: $cmd")
                val output = execShellCommand(cmd)
                if (output.isNotBlank()) {
                    log("[Shell]   ✓ 输出${output.length}字符")
                    // 只记录前300字符避免刷屏
                    val preview = output.take(300).replace("\n", " ")
                    log("[Shell]   预览: ${preview}...")

                    // 尝试从输出中解析车辆数据
                    val parsed = parseShellOutput(output)
                    var newDataFound = false
                    if (parsed.speed > 0 && data.speed == 0f) {
                        data.speed = parsed.speed
                        log("[Shell]   ✓ 解析到车速: ${data.speed} km/h")
                        newDataFound = true
                    }
                    if (parsed.soc > 0 && data.soc == 0) {
                        data.soc = parsed.soc
                        log("[Shell]   ✓ 解析到SOC: ${data.soc}%")
                        newDataFound = true
                    }
                    if (parsed.elecRemain > 0 && data.elecRemain == 0) {
                        data.elecRemain = parsed.elecRemain
                        log("[Shell]   ✓ 解析到电续航: ${data.elecRemain}km")
                        newDataFound = true
                    }
                    if (parsed.mileage > 0 && data.mileage == 0) {
                        data.mileage = parsed.mileage
                        log("[Shell]   ✓ 解析到里程: ${data.mileage}km")
                        newDataFound = true
                    }
                    if (parsed.gear != "-" && data.gear == "-") {
                        data.gear = parsed.gear
                        log("[Shell]   ✓ 解析到档位: ${data.gear}")
                        newDataFound = true
                    }
                    if (parsed.powerMode != "-" && data.powerMode == "-") {
                        data.powerMode = parsed.powerMode
                        log("[Shell]   ✓ 解析到电源模式: ${data.powerMode}")
                        newDataFound = true
                    }

                    if (newDataFound) foundUsefulInfo = true
                    if (!newDataFound) log("[Shell]   ✗ 未解析到有效车辆数据")
                } else {
                    log("[Shell]   ✗ 无输出")
                }
            } catch (e: Exception) {
                log("[Shell]   ✗ 异常: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        return if (foundUsefulInfo) {
            log("[Shell] ✓ Shell命令探测成功! 获取到${if (data.speed > 0) "车速 " else ""}${if (data.soc > 0) "SOC " else ""}${if (data.elecRemain > 0) "续航" else ""}")
            data.apply {
                success = true
                timestamp = System.currentTimeMillis()
                rawJson = "shell"
            }
        } else {
            log("[Shell] ✗ 所有shell命令均未返回车辆数据")
            fail("所有shell命令未返回车辆数据")
        }
    }

    /**
     * 执行shell命令并返回输出
     */
    private fun execShellCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            val startTime = System.currentTimeMillis()
            while (reader.readLine().also { line = it } != null) {
                if (System.currentTimeMillis() - startTime > 5000) break // 最多读5秒
                output.append(line).append("\n")
            }
            reader.close()
            process.waitFor(5, TimeUnit.SECONDS)
            process.destroy()
            output.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 从shell输出中解析可能的车辆数值
     */
    private fun parseShellOutput(output: String): CarData {
        val data = CarData()

        // 匹配常见的速度格式
        val speedPatterns = arrayOf(
            Regex("""speed[=:\s]*(\d+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""currentSpeed[=:\s]*(\d+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""车速[=:\s]*(\d+\.?\d*)"""),
            Regex("""vehicle_speed[=:\s]*(\d+\.?\d*)""")
        )
        for (p in speedPatterns) {
            p.find(output)?.groupValues?.get(1)?.toFloatOrNull()?.let {
                if (it > 0) data.speed = it
            }
        }

        // 匹配SOC/电量
        val socPatterns = arrayOf(
            Regex("""soc[=:\s]*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""battery[=_\s]*(\d+)\s*%""", RegexOption.IGNORE_CASE),
            Regex("""电量[=:\s]*(\d+)"""),
            Regex("""elecPercentage[=:\s]*(\d+\.?\d*)"""),
            Regex("""percentage[=:\s]*(\d+\.?\d*)""")
        )
        for (p in socPatterns) {
            p.find(output)?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it in 0..100) data.soc = it
            }
        }

        // 匹配续航
        val rangePatterns = arrayOf(
            Regex("""elecRange[=:\s]*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""drivingRange[=:\s]*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""续航[=:\s]*(\d+)"""),
            Regex("""range[=:\s]*(\d+)\s*km""")
        )
        for (p in rangePatterns) {
            p.find(output)?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it > 0) data.elecRemain = it
            }
        }

        // 匹配档位
        val gearPatterns = arrayOf(
            Regex("""gear[=:\s]*([PRND])""", RegexOption.IGNORE_CASE),
            Regex("""gearPosition[=:\s]*(\w+)"""),
            Regex("""档位[=:\s]*(\w+)""")
        )
        for (p in gearPatterns) {
            p.find(output)?.groupValues?.get(1)?.let {
                if (it.matches(Regex("[PRND]"))) data.gear = it.uppercase()
            }
        }

        // 匹配电源模式
        if (output.contains("READY", ignoreCase = true)) data.powerMode = "READY"
        if (output.contains("ACC_ON", ignoreCase = true)) data.powerMode = "ACC"
        if (output.contains("OFF", ignoreCase = true) && data.powerMode == "-") data.powerMode = "OFF"

        return data
    }

    // ========== 策略3: HTTP广谱扫描 ==========

    /**
     * 扫描多个端口的多种路径，寻找可用的车辆数据API
     * 为了性能，只扫描前N个端口的前M个路径就停止（找到即止）
     */
    private fun tryHttpScan(): CarData {
        var attemptCount = 0
        val maxAttempts = 50 // 最多尝试50次组合就放弃（避免太慢）
        var successPort = -1
        var successPath = ""

        log("[HTTP] 开始广谱端口扫描...")
        log("[HTTP] 端口列表: ${SCAN_PORTS.take(10).joinToString(", ")}... (共${SCAN_PORTS.size}个)")
        log("[HTTP] 路径列表: ${SCAN_PATHS.take(5).joinToString(", ")}... (共${SCAN_PATHS.size}个)")
        log("[HTTP] 最大尝试次数: $maxAttempts")

        for (port in SCAN_PORTS) {
            for (path in SCAN_PATHS) {
                if (attemptCount++ >= maxAttempts) {
                    log("[HTTP] 达到最大尝试次数($maxAttempts)，停止扫描")
                    return fail("HTTP扫描达到上限(${maxAttempts}次)无结果")
                }

                try {
                    val url = "http://127.0.0.1:$port$path"
                    val result = trySingleUrl(url, port, path)
                    if (result.success) {
                        successPort = port
                        successPath = path
                        currentPort = port
                        log("[HTTP] ✓ 扫描成功! 端口=$port, 路径=$path")
                        return result
                    }
                } catch (e: Exception) {
                    // 单个URL失败继续下一个
                }
            }
        }

        log("[HTTP] ✗ 全部端口/路径均无响应 (${SCAN_PORTS.size}端口 x ${SCAN_PATHS.size}路径, 实际尝试${maxAttempts}次)")
        return fail("HTTP扫描全部端口/路径均无响应 (${SCAN_PORTS.size}端口 x ${SCAN_PATHS.size}路径)")
    }

    private fun trySingleUrl(url: String, port: Int, path: String): CarData {
        val startTime = System.currentTimeMillis()
    
        val request = Request.Builder().url(url)
            .addHeader("Accept", "*/*")
            .addHeader("User-Agent", "BYDCarClient/1.0")
            .build()
    
        val response = client.newCall(request).execute()
        val elapsed = System.currentTimeMillis() - startTime
    
        if (!response.isSuccessful) {
            if (response.code in 400..499) return fail("HTTP ${response.code}")
            return fail("HTTP ${response.code}") // 继续扫描
        }
    
        val bodyStr = response.body?.string() ?: ""
        val contentType = response.header("Content-Type") ?: ""
    
        log("[HTTP]   ✓ $url → ${bodyStr.length}B type=$contentType (${elapsed}ms)")
    
        // 过滤非JSON响应（HTML页面、图片等）
        if (bodyStr.length < 10 || bodyStr.startsWith("<") || bodyStr.startsWith("%PDF")) {
            log("[HTTP]   ✗ 非JSON响应(${bodyStr.length}B), 跳过")
            return fail("非JSON响应(${bodyStr.length}B)")
        }
    
        log("[HTTP]   内容预览: ${bodyStr.take(200)}")
    
        // 尝试解析为JSON
        return try {
            val obj = JSONObject(bodyStr)
            val carData = parseJsonObject(obj)
            carData.apply {
                rawJson = bodyStr
                timestamp = System.currentTimeMillis()
                dataSource = "HTTP:${port}${path}"
            }
        } catch (e: Exception) {
            // 不是JSON但可能有文本格式的数据
            log("[HTTP]   ✗ JSON解析失败, 尝试文本解析...")
            parseTextResponse(bodyStr).apply {
                rawJson = bodyStr
                timestamp = System.currentTimeMillis()
                dataSource = "HTTP:${port}:${path}"
                if (!success) fail("响应无法解析")
            }
        }
    }

    // ========== 策略4: ContentProvider ==========

    private fun tryContentProvider(): CarData {
        val ctx = appContext ?: return fail("appContext未设置")
        val data = CarData().apply { dataSource = "ContentProvider" }

        log("[CP] 开始ContentProvider查询...")
        log("[CP] 共${CONTENT_URIS.size}个URI待测试")

        for ((index, uriStr) in CONTENT_URIS.withIndex()) {
            try {
                log("[CP] [$((index+1))/${CONTENT_URIS.size}] 查询: $uriStr")
                val uri = Uri.parse(uriStr)
                val cr: ContentResolver = ctx.contentResolver
                val cursor: Cursor? = cr.query(uri, null, null, null, null)

                cursor?.use {
                    val columnCount = it.columnCount
                    log("[CP]   ✓ Cursor获取成功, 列数=$columnCount")
                    if (it.moveToFirst()) {
                        val row = mutableMapOf<String, String>()
                        for (i in 0 until columnCount) {
                            row[it.getColumnName(i)] = it.getString(i) ?: ""
                        }
                        log("[CP]   列名: ${it.columnNames.joinToString(", ")}")
                        log("[CP]   数据: $row")

                        // 尝试从cursor中提取数据
                        var newDataFound = false
                        for ((key, value) in row) {
                            val kLower = key.lowercase()
                            when {
                                kLower.contains("speed") -> {
                                    value.toFloatOrNull()?.let {
                                        if (it > 0) {
                                            data.speed = it
                                            log("[CP]   ✓ 解析到车速: $it")
                                            newDataFound = true
                                        }
                                    }
                                }
                                kLower.contains("soc") || kLower.contains("battery") || kLower.contains("electricity") -> {
                                    value.replace(Regex("[^\\d]"), "").toIntOrNull()?.let {
                                        if (it <= 100) {
                                            data.soc = it
                                            log("[CP]   ✓ 解析到SOC: $it%")
                                            newDataFound = true
                                        }
                                    }
                                }
                                kLower.contains("range") || kLower.contains("remain") -> {
                                    value.replace(Regex("[^\\d]"), "").toIntOrNull()?.let {
                                        if (it > 0) {
                                            data.elecRemain = it
                                            log("[CP]   ✓ 解析到续航: ${it}km")
                                            newDataFound = true
                                        }
                                    }
                                }
                                kLower.contains("mileage") || kLower.contains("odometer") -> {
                                    value.replace(Regex("[^\\d]"), "").toIntOrNull()?.let {
                                        if (it > 0) {
                                            data.mileage = it
                                            log("[CP]   ✓ 解析到里程: ${it}km")
                                            newDataFound = true
                                        }
                                    }
                                }
                                kLower.contains("gear") -> {
                                    data.gear = value.ifEmpty { "-" }
                                    log("[CP]   ✓ 解析到档位: ${data.gear}")
                                    newDataFound = true
                                }
                                kLower.contains("mode") || kLower.contains("power") -> {
                                    data.powerMode = value.ifEmpty { "-" }
                                    log("[CP]   ✓ 解析到模式: ${data.powerMode}")
                                    newDataFound = true
                                }
                                kLower.contains("temp") -> {
                                    value.toFloatOrNull()?.let {
                                        if (it > -50 && it < 80) {
                                            data.outTemp = it
                                            log("[CP]   ✓ 解析到温度: ${it}℃")
                                            newDataFound = true
                                        }
                                    }
                                }
                            }
                        }

                        if (data.speed > 0 || data.soc > 0 || data.elecRemain > 0) {
                            log("[CP] ✓ ContentProvider查询成功!")
                            data.success = true
                            data.timestamp = System.currentTimeMillis()
                            return data
                        }
                        if (!newDataFound) log("[CP]   ✗ 未解析到有效车辆数据")
                    } else {
                        log("[CP]   ✗ 无数据行")
                    }
                } ?: run {
                    log("[CP]   ✗ cursor为null(CP可能不存在或权限不足)")
                }
            } catch (e: SecurityException) {
                log("[CP]   ✗ 权限不足: ${e.message}")
            } catch (e: Exception) {
                log("[CP]   ✗ 异常: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        log("[CP] ✗ 所有ContentProvider均无数据或不可用")
        return fail("所有ContentProvider均无数据或不可用")
    }

    // ========== JSON/文本解析工具方法 ==========

    private fun parseJsonObject(obj: JSONObject): CarData {
        val carData = CarData(success = true)

        // 递归搜索所有字段（处理嵌套JSON）
        extractAllFields(obj, "", carData)

        // 记录发现的顶层字段名
        val keys = obj.keys().asSequence().toList()
        log("[CarHTTP] JSON字段: ${keys.joinToString(", ")}")

        // 如果没有提取到任何有效数据也标记为失败
        if (carData.speed == 0f && carData.soc == 0 && carData.elecRemain == 0 &&
            carData.fuelRemain == 0 && carData.mileage == 0) {
            // 可能是其他类型的JSON（如配置），检查是否有任何数字字段
            carData.success = hasAnyNumericField(obj)
        }

        return carData
    }

    /**
     * 递归提取JSON中所有可能的车辆字段
     */
    private fun extractAllFields(obj: JSONObject, prefix: String, data: CarData) {
        val keys = obj.keys().asSequence().toList()
        for (key in keys) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            try {
                val value = obj.get(key)
                when (value) {
                    is JSONObject -> extractAllFields(value, fullKey, data)
                    is Number -> {
                        val kLower = key.lowercase()
                        val v = value.toDouble()
                        when {
                            kLower.contains("speed") && v >= 0 && v <= 300 -> data.speed = v.toFloat()
                            kLower.contains("soc") && v in 0.0..100.0 -> data.soc = v.toInt()
                            kLower.contains("battery") && v in 0.0..100.0 && data.soc == 0 -> data.soc = v.toInt()
                            kLower.contains("elec") && (kLower.contains("range") || kLower.contains("remain")) && v > 0 -> data.elecRemain = v.toInt()
                            kLower.contains("fuel") && (kLower.contains("range") || kLower.contains("remain")) && v > 0 -> data.fuelRemain = v.toInt()
                            kLower.contains("mileage") || kLower.contains("odometer") -> data.mileage = v.toInt()
                            kLower.contains("outtemp") || kLower.contains("ambient") -> data.outTemp = v.toFloat()
                            kLower.contains("intemp") || kLower.contains("cabin") -> data.inTemp = v.toFloat()
                            kLower.contains("gear") -> data.gear = value.toString()
                            kLower.contains("mode") || kLower.contains("power") || kLower.contains("state") ->
                                if (data.powerMode == "-") data.powerMode = value.toString()
                            kLower.contains("temp") && !kLower.contains("out") && !kLower.contains("in") &&
                                    kLower.contains("ac") -> data.acTemp = v.toInt()
                        }
                    }
                    is Boolean -> {
                        if (key.lowercase().contains("ac") && (key.lowercase().contains("on") || key.lowercase().contains("switch"))) {
                            data.acOn = value
                        }
                    }
                    is String -> {
                        val kLower = key.lowercase()
                        if (kLower.contains("gear") && data.gear == "-") data.gear = value
                        if ((kLower.contains("mode") || kLower.contains("state")) && data.powerMode == "-") data.powerMode = value
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun hasAnyNumericField(obj: JSONObject): Boolean {
        val keys = obj.keys().asSequence().toList()
        for (key in keys) {
            try {
                if (obj.get(key) is Number) return true
                if (obj.get(key) is JSONObject && hasAnyNumericField(obj.getJSONObject(key))) return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * 解析纯文本响应（如 "speed=0,soc=85" 格式）
     */
    private fun parseTextResponse(text: String): CarData {
        val data = CarData()
        // key=value 格式
        val kvPattern = Regex("""(\w+)[=:]\s*(\d+\.?\d*)""")
        kvPattern.findAll(text).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2]
            when {
                key.contains("speed") -> value.toFloatOrNull()?.let { data.speed = it }
                key.contains("soc") || key.contains("battery") -> value.toIntOrNull()?.let { if (it <= 100) data.soc = it }
                key.contains("range") || key.contains("remain") -> value.toIntOrNull()?.let { data.elecRemain = it }
                key.contains("mileage") -> value.toIntOrNull()?.let { data.mileage = it }
                key.contains("gear") -> data.gear = value.uppercase()
            }
        }
        return if (data.speed > 0 || data.soc > 0 || data.elecRemain > 0) {
            data.apply { success = true }
        } else {
            fail("文本响应无可识别的车辆数据")
        }
    }

    // ========== 辅助方法 ==========

    private fun fail(msg: String): CarData {
        return CarData().apply {
            success = false
            errorMsg = msg
        }
    }

    /** 获取当前生效的端口 */
    fun getCurrentPort(): Int = currentPort

    /** 测试连通性 */
    fun testConnection(port: Int = currentPort): Boolean {
        return try {
            val url = "http://127.0.0.1:$port/dilink/realCarData"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Connection test failed on port $port: ${e.message}")
            false
        }
    }

    // ========== 探测工具方法（供调试Activity使用）==========

    /**
     * 运行完整探测并返回详细报告（供UI展示）
     */
    fun runFullDetection(): DetectionReport {
        val report = DetectionReport(timestamp = System.currentTimeMillis())
        val startMs = System.currentTimeMillis()

        // 策略1: SDK反射
        report.sdkResults = try {
            val ctx = appContext
            val results = mutableListOf<String>()
            if (ctx == null) {
                results.add("❌ appContext未设置")
            } else {
                var anyFound = false
                for ((group, classNames) in SDK_CLASSES) {
                    for (cn in classNames) {
                        try {
                            Class.forName(cn)
                            results.add("✓ $cn (类存在)")
                            anyFound = true
                        } catch (e: ClassNotFoundException) {
                            // 不记录每个缺失的类
                        }
                    }
                }
                if (!anyFound) results.add("✗ 所有SDK类均未找到")
            }
            results
        } catch (e: Exception) {
            listOf("异常: ${e.message}")
        }
        report.sdkTime = System.currentTimeMillis() - startMs

        // 策略2: 快速shell检测
        val shellStart = System.currentTimeMillis()
        report.shellResults.clear()
        // 只跑几个快速命令
        val quickCmds = arrayOf(
            "service list 2>&1 | head -30",
            "getprop ro.build.display.id",
            "whoami",
            "id"
        )
        for (cmd in quickCmds) {
            try {
                val out = execShellCommand(cmd)
                report.shellResults.add("$cmd → ${out.take(200)}")
            } catch (e: Exception) {
                report.shellResults.add("$cmd → 错误: ${e.message}")
            }
        }
        report.shellTime = System.currentTimeMillis() - shellStart

        // 策略3: 快速端口扫描（只扫常见端口）
        val httpStart = System.currentTimeMillis()
        report.httpResults.clear()
        val quickPorts = intArrayOf(8080, 8081, 8082, 9000, 8888, 9999, 80, 443, 5555)
        val quickPaths = arrayOf("/", "/status", "/health", "/ping", "/dilink/realCarData",
            "/car/status", "/vehicle/status", "/api/status", "/info", "/data")
        for (port in quickPorts) {
            for (path in quickPaths) {
                try {
                    val url = "http://127.0.0.1:$port$path"
                    val reqStart = System.currentTimeMillis()
                    val request = Request.Builder().url(url).build()
                    val resp = OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .build().newCall(request).execute()
                    val elapsed = System.currentTimeMillis() - reqStart
                    val bodyLen = resp.body?.string()?.length ?: 0
                    if (resp.isSuccessful && bodyLen > 5) {
                        report.httpResults.add("✓ $url → HTTP ${resp.code} (${elapsed}ms, ${bodyLen}B)")
                    } else if (resp.code !in 404..404) {
                        // 非404说明端口开放
                        report.httpResults.add("◐ $url → HTTP ${resp.code} (${elapsed}ms)")
                    }
                } catch (e: java.net.ConnectException) {
                    // 端口未开放，静默跳过
                } catch (e: Exception) {
                    // 其他错误
                }
            }
        }
        if (report.httpResults.isEmpty()) report.httpResults.add("✗ 无任何端口响应")
        report.httpTime = System.currentTimeMillis() - httpStart

        // 策略4: CP检测
        val cpStart = System.currentTimeMillis()
        report.cpResults.clear()
        val ctx = appContext
        if (ctx != null) {
            for (uriStr in CONTENT_URIS) {
                try {
                    val uri = Uri.parse(uriStr)
                    val cursor = ctx.contentResolver.query(uri, null, null, null, null)
                    if (cursor != null) {
                        val count = cursor.count
                        val cols = cursor.columnNames.joinToString(",")
                        cursor.close()
                        report.cpResults.add("✓ $uriStr → ${count}行 [${cols}]")
                    }
                } catch (e: SecurityException) {
                    report.cpResults.add("⚠ $uriStr → 权限不足")
                } catch (e: Exception) {
                    // CP不存在，静默跳过
                }
            }
        }
        if (report.cpResults.isEmpty()) report.cpResults.add("✗ 无可用ContentProvider")
        report.cpTime = System.currentTimeMillis() - cpStart

        report.totalTime = System.currentTimeMillis() - startMs
        return report
    }

    data class DetectionReport(
        var timestamp: Long = 0L,
        var sdkResults: List<String> = emptyList(),
        var sdkTime: Long = 0L,
        var shellResults: MutableList<String> = mutableListOf(),
        var shellTime: Long = 0L,
        var httpResults: MutableList<String> = mutableListOf(),
        var httpTime: Long = 0L,
        var cpResults: MutableList<String> = mutableListOf(),
        var cpTime: Long = 0L,
        var totalTime: Long = 0L
    )
}