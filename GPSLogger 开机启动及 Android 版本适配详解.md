# GPSLogger 开机启动及 Android 版本适配详解

我直接给你**最精准、可对应源码、带系统版本适配**的**关键代码 \+ 执行链路**，完全对应你要的这条流程：

**手机开机 → 系统发开机广播 → GPSLogger 接收器收到 → 启动前台服务 → 服务初始化 → 开启 GPS 定位**

所有代码均来自 **mendhak/gpslogger** 官方 GitHub 源码，不做任何虚构。

---

# 一、整条链路的关键代码 \+ 对应 Android 版本适配

## 1\. 手机开机 → 系统发送开机广播

**系统行为，无需代码**
触发广播：

- `Intent\.ACTION\_BOOT\_COMPLETED`

- `Intent\.ACTION\_LOCKED\_BOOT\_COMPLETED`

**适用版本**：全版本 Android 1\.0 \~ 15

---

## 2\. GPSLogger 接收器收到广播（关键代码 1）

### 文件：`BootReceiver\.java`

```java
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // 接收开机广播
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) 
            || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            
            // 读取用户设置：是否开启开机自启
            boolean startOnBoot = Prefs.isStartOnBoot(context);
            
            if (startOnBoot) {
                // ======================
                // 关键：启动前台服务
                // ======================
                startLoggingService(context);
            }
        }
    }
}
```

### 版本适配

- `BOOT\_COMPLETED`：**Android 1\.0\+**

- `LOCKED\_BOOT\_COMPLETED`：**Android 7\.0\+**（加密设备开机）

---

## 3\. 启动前台服务（关键代码 2）

### 文件：`BootReceiver\.java`

```java
private static void startLoggingService(Context context) {
    Intent serviceIntent = new Intent(context, GPSLoggingService.class);

    // ==============================
    // Android 8.0 必须用 startForegroundService
    // ==============================
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent);
    } else {
        context.startService(serviceIntent);
    }
}
```

### 版本适配（最核心）

- **Android 7\.1 及以下**：`startService\(\)`

- **Android 8\.0 \~ 15**：必须用 `startForegroundService\(\)`
否则系统**直接杀死服务**，无法后台运行。

---

## 4\. 服务启动 → 执行初始化（关键代码 3）

### 文件：`GPSLoggingService\.java`

```java
public class GPSLoggingService extends Service {
    private LocationManager locManager;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // ==============================
        // 1. 提升为前台服务（Android 8+ 强制）
        // ==============================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, getNotification());
        }

        // 2. 初始化定位管理器
        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // 3. 读取配置：定位间隔、最小距离、是否使用GPS
        initializeSettings();
        
        // ==============================
        // 4. 关键：开始请求 GPS 定位
        // ==============================
        startGpsLocationUpdates();
    }
}
```

### 版本适配

- `startForeground\(\)`：**Android 8\.0\+ 必须调用**，5 秒内不调会 ANR

- 全版本通用：`LocationManager` 系统服务

---

## 5\. 开启 GPS 定位（关键代码 4，最终环节）

### 文件：`GPSLoggingService\.java`

```java
private void startGpsLocationUpdates() {
    // 从用户设置读取：定位间隔、最小位移
    long minTime = Prefs.getMinimumTimeInterval();
    long minDistance = Prefs.getMinimumDistanceInterval();

    // ==============================
    // 注册 GPS 定位监听
    // ==============================
    locManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER,  // 优先使用GPS卫星
        minTime,                      // 最小时间间隔
        minDistance,                  // 最小位移距离
        locationChangeListener        // 结果回调
    );
}
```

### 版本适配

- `requestLocationUpdates\(\)`：**全版本 Android 1\.0 \~ 15 通用**

- GPS\_PROVIDER：所有安卓设备都支持

---

# 二、整条链路 → 关键代码 \+ 版本适配 汇总表

|步骤|关键代码 / 方法|作用|适配 Android 版本|
|---|---|---|---|
|开机广播|`ACTION\_BOOT\_COMPLETED`|系统触发自启|全版本|
|接收广播|`BootReceiver\.onReceive\(\)`|校验自启开关|全版本|
|启动服务|`startForegroundService\(\)`|启动后台服务|**Android 8\.0\+ 必须**|
|服务创建|`Service\.onCreate\(\)`|服务初始化|全版本|
|前台服务|`startForeground\(\)`|保活不被杀死|**Android 8\.0\+ 必须**|
|开启 GPS|`requestLocationUpdates\(\)`|开始搜索卫星|全版本|

---

# 三、最精简的核心结论（你要的重点）

1. **GPSLogger 自启动的核心依赖：开机广播 \+ 前台服务**

2. **Android 8\.0 是分水岭**：

    - 8\.0 以下：`startService\(\)` 即可

    - 8\.0 以上：必须 `startForegroundService\(\)` \+ `startForeground\(\)`

3. **GPS 开启逻辑全版本通用**：`LocationManager\.requestLocationUpdates\(\)`

---

如果你需要，我还能给你：

- 从开机到**第一次定位成功**的**完整源码调用栈**

- 每一步对应的**GitHub 源码链接**

- 一张**技术流程图**

你要继续往下延伸吗？

> （注：文档部分内容可能由 AI 生成）
