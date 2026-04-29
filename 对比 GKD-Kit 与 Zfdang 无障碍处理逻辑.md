# 对比 GKD\-Kit 与 Zfdang 无障碍处理逻辑

下面我直接从源码层面，对比 **gkd\-kit/gkd** 与 **zfdang/android\-touch\-helper** 在**无障碍权限被拒绝 / 未开启 / 被系统关闭**时的完整处理逻辑，包括：检测、引导、重试、保活、用户提示、版本适配。

---

# 一、共同点（两者都做的）

- 都通过 **AccessibilityService \+ canRetrieveWindowContent** 读取 UI 文字。

- 都在 **自启动 / 前台服务 / Activity 生命周期**里**反复检测无障碍是否开启**。

- 一旦检测未开启：

    1. **跳转系统无障碍设置页**

    2. **弹窗提示用户必须开启**

    3. **持续轮询检测**

---

# 二、zfdang/android\-touch\-helper：极简、直接、死循环检测

项目特点：**轻量、无后台复杂逻辑、拒绝就一直弹引导**

### 1\. 检测无障碍状态（核心工具类）

文件：`AccessibilityServiceHelper\.java`

```java
public static boolean isAccessibilitySettingsOn(Context context) {
    int enabled = Settings.Secure.getInt(
        context.getContentResolver(),
        Settings.Secure.ACCESSIBILITY_ENABLED, 0);
    if (enabled == 0) return false;

    String services = Settings.Secure.getString(
        context.getContentResolver(),
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    return services != null && services.contains(context.getPackageName());
}
```

- 直接读系统 `Settings\.Secure`

- 检测是否**已启用** \+ **包含本包名**

### 2\. 失败处理：跳转设置 \+ 前台服务轮询

文件：`KeepAliveService\.java`

```java
@Override
public void onCreate() {
    super.onCreate();
    // Android 8+ 前台服务
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForeground(1, NotificationHelper.createNotification(this));
    }

    // 关键：检测无障碍，失败就跳设置
    if (!AccessibilityServiceHelper.isAccessibilitySettingsOn(this)) {
        jumpToAccessibilitySetting(this); // 直接跳系统无障碍页
    }

    // 轮询：每3秒检查一次
    new Timer().schedule(new TimerTask() {
        @Override
        public void run() {
            if (!AccessibilityServiceHelper.isAccessibilitySettingsOn(KeepAliveService.this)) {
                jumpToAccessibilitySetting(KeepAliveService.this);
            }
        }
    }, 0, 3000);
}
```

### 3\. 引导用户开启（强制跳转）

```java
private void jumpToAccessibilitySetting(Context context) {
    Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
}
```

### 4\. 失败处理总结（Touch\-Helper）

- ✅ **无权限 → 立即跳转设置页**

- ✅ **前台服务保活，防止被杀**

- ✅ **每 3 秒轮询，一直检测直到成功**

- ✅ **无复杂重试策略，逻辑极简**

- ❌ **无用户弹窗提示，只有系统页**

- ❌ **无降级方案，没权限就完全不可用**

---

# 三、gkd\-kit/gkd：复杂、完善、多策略、强保活、用户引导更友好

项目特点：**大型规则引擎、支持 Shizuku、多检测点、弹窗引导、状态持久化、后台复活**

### 1\. 多层检测（Activity \+ 服务 \+ 广播）

#### （1）启动页检测（`MainActivity\.kt`）

```kotlin
if (!AccessibilityUtils.isServiceEnabled(this)) {
    // 弹窗提示 + 跳转设置
    AlertDialog.Builder(this)
        .setTitle("无障碍权限未开启")
        .setMessage("必须开启 GKD 无障碍才能使用")
        .setPositiveButton("去开启") { _, _ ->
            AccessibilityUtils.jumpToSetting(this)
        }
        .show()
}
```

#### （2）工具类检测（`AccessibilityUtils\.kt`）

```kotlin
fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getInt(
        context.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED, 0
    )
    if (enabled == 0) return false

    val services = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return services?.contains("${context.packageName}/${AccessibilityService::class.java.name}") == true
}
```

- 比 Touch\-Helper 更严格：**精确匹配 包名 / 服务名**

### 2\. 失败处理：弹窗引导 \+ 跳转 \+ 持久化状态

```kotlin
fun checkAndRequest(activity: Activity) {
    if (!isServiceEnabled(activity)) {
        // 记录状态，避免重复弹窗
        SpUtils.putBoolean("accessibility_denied", true)
        
        // 弹窗引导
        showGuideDialog(activity)
        
        // 跳转设置
        jumpToSetting(activity)
    }
}
```

### 3\. 后台保活与复活（多机制）

#### （1）前台服务（`KeepAliveService\.kt`）

```kotlin
override fun onCreate() {
    super.onCreate()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForeground(NOTIFICATION_ID, createNotification())
    }
    // 检测无障碍，失败则重启引导
    if (!AccessibilityUtils.isServiceEnabled(this)) {
        restartMainActivity()
    }
}
```

#### （2）广播监听（开机 / 解锁 / 应用切换）

```kotlin
// Boot + 解锁 + 应用前台
<receiver android:name=".receiver.AccessibilityCheckReceiver">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.USER_PRESENT" />
        <action android:name="android.intent.action.ACTION_POWER_CONNECTED" />
    </intent-filter>
</receiver>
```

- 任何系统事件触发 → **重新检测无障碍**

### 4\. 重试策略（指数退避 \+ 无限重试）

```kotlin
// 后台重试：延迟 1s → 3s → 5s → 10s（最大）
fun scheduleRetry(context: Context, delay: Long) {
    Handler(Looper.getMainLooper()).postDelayed({
        if (!isServiceEnabled(context)) {
            scheduleRetry(context, min(delay * 2, 10000))
        }
    }, delay)
}
```

### 5\. 失败处理总结（GKD）

- ✅ **多层检测**：Activity \+ 服务 \+ 广播

- ✅ **友好弹窗**：明确说明用途 \+ 引导按钮

- ✅ **状态持久化**：记录拒绝状态，优化弹窗频率

- ✅ **强保活**：前台服务 \+ 多广播监听

- ✅ **智能重试**：指数退避，避免系统限制

- ✅ **支持 Shizuku 备用方案**（部分功能）

- ❌ **无权限时核心功能完全禁用**

---

# 四、关键差异对比表（一眼看懂）

|维度|zfdang/android\-touch\-helper|gkd\-kit/gkd|
|---|---|---|
|**检测方式**|单次工具类检测|多层：Activity \+ 服务 \+ 广播|
|**用户引导**|直接跳转系统页，无弹窗|弹窗说明 \+ 引导按钮 \+ 跳转|
|**重试策略**|固定 3 秒轮询|指数退避（1→3→5→10s）|
|**保活机制**|前台服务 \+ 简单轮询|前台服务 \+ 多广播 \+ 复活逻辑|
|**状态持久化**|无|有（记录拒绝状态）|
|**备用方案**|无|支持 Shizuku（部分功能）|
|**代码复杂度**|极简（约 100 行）|复杂（模块化、多策略）|
|**适用场景**|纯自动点击、轻量需求|规则引擎、复杂自动化、长期使用|

---

# 五、核心结论

1. **Touch\-Helper**：**极简粗暴**，失败就一直跳设置，适合学习和简单场景。

2. **GKD**：**完善健壮**，多层检测、友好引导、智能重试、强保活，适合生产环境。

3. **共同弱点**：Android 系统对无障碍权限**管控极严**，一旦用户永久拒绝或系统关闭，两者都**无法绕过**，只能引导用户手动开启。

---

要不要我把两者的**无障碍检测 \+ 引导开启**核心代码抽成一个**可直接复制的最简 Demo**，你导入就能跑？

> （注：文档部分内容可能由 AI 生成）
