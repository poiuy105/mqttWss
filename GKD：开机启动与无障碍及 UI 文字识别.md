# GKD：开机启动与无障碍及 UI 文字识别

我直接给你**最核心、最精简、可直接对应源码**的分析：
**GKD 开机自启动 → 启动无障碍 → 识别 UI 文字** 的完整关键代码 \+ 版本适配。

我只讲**你要的链路**，不废话、不掺无关内容。

---

# 一、整体链路（一句话）

**开机广播 → 启动前台服务 → 检测无障碍 → 启动无障碍服务 → 监听窗口变化 → 获取页面节点 → 提取 UI 文字 / 控件**

---

# 二、关键代码 \+ 版本适配（逐环节）

## 1\. 开机自启动（入口）

### 文件：`BootReceiver\.kt`

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 开机广播触发
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Android 8.0+ 必须用 startForegroundService
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(Intent(context, GkdService::class.java))
            } else {
                context.startService(Intent(context, GkdService::class.java))
            }
        }
    }
}
```

**适配版本**：

- Android 8\.0\+\(API 26\)：必须用 `startForegroundService`

- 低于 8\.0：`startService`

---

## 2\. 前台服务保活（必须）

### 文件：`GkdService\.kt`

```kotlin
override fun onCreate() {
    super.onCreate()
    // Android 8.0+ 必须启动前台通知
    if (Build.VERSION.SDK_INT >= 26) {
        startForeground(1, notification)
    }
    // 启动无障碍检测
    AccessibilityManager.startMonitor(this)
}
```

**作用**：防止服务被系统杀死，保证无障碍能正常运行。

---

## 3\. 无障碍服务声明（能识别 UI 的前提）

### 文件：`AndroidManifest\.xml`

```xml
<service
    android:name=".A11yService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService"/>
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service"/>
</service>
```

### 文件：`res/xml/accessibility\_service\.xml`（关键权限）

```xml
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:canRetrieveWindowContent="true"  <!-- 允许读取页面文字/节点 -->
    android:accessibilityFlags="flagIncludeNotImportantViews|flagReportViewIds"
/>
```

**核心权限**：
`canRetrieveWindowContent=\&\#34;true\&\#34;`
→ **允许无障碍读取 UI 上的所有文字、控件、内容**

**适配版本**：

- 全版本 Android 4\.0\+ 都支持

- 但 Android 11\+ 限制更严格

---

## 4\. 无障碍服务：监听页面打开（核心触发）

### 文件：`A11yService\.kt`

```kotlin
class A11yService : AccessibilityService() {

    // 页面打开/切换时回调
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 只监听窗口变化（页面切换、弹窗弹出）
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            // 获取当前页面根节点
            val rootNode = rootInActiveWindow

            // 开始递归识别 UI 文字
            if (rootNode != null) {
                parseNodeTree(rootNode)
            }
        }
    }
}
```

**作用**：
每当 APP 页面打开 / 弹窗出现 → 系统通知无障碍 → GKD 开始解析文字。

---

## 5\. 递归遍历节点 \+ 提取 UI 文字（最核心！）

### 文件：`A11yService\.kt`

```kotlin
private fun parseNodeTree(node: AccessibilityNodeInfo) {
    // ======================
    // 提取当前控件的文字
    // ======================
    val text = node.text?.toString()         // 按钮/文本文字
    val id = node.viewIdResourceName         // 控件 ID
    val className = node.className          // 控件类型 Button/TextView

    // 如果有文字，就可以匹配规则（例如“跳过”“关闭”“同意”）
    if (!text.isNullOrBlank()) {
        // 这里就是 GKD 识别 UI 文字的地方
        RuleMatcher.match(text, id, className)
    }

    // ======================
    // 递归遍历子节点
    // ======================
    for (i in 0 until node.childCount) {
        val child = node.getChild(i)
        if (child != null) {
            parseNodeTree(child)  // 递归
            child.recycle()      // 必须释放，避免内存泄漏
        }
    }
}
```

### 这就是：

**GKD 识别屏幕上所有文字的核心代码**

- 读取 `node\.text` → 拿到 UI 文字

- 递归遍历所有子控件 → 拿到页面全部内容

---

# 三、版本适配总结（最重要）

|功能|关键代码|最低 Android 版本|
|---|---|---|
|开机自启动|BOOT\_COMPLETED|1\.0\+|
|前台服务保活|startForeground|8\.0 \(API 26\)|
|读取 UI 文字|canRetrieveWindowContent|4\.0 \(API 14\)|
|监听页面变化|TYPE\_WINDOW\_STATE\_CHANGED|4\.0\+|
|递归获取节点|AccessibilityNodeInfo|4\.1\+|

---

# 四、最精简核心结论（你要的重点）

1. **GKD 自启动靠：开机广播 \+ 前台服务**

2. **能识别 UI 文字靠：无障碍服务 \+ canRetrieveWindowContent=true**

3. **文字识别核心代码**：

    ```kotlin
    val text = node.text.toString()
    ```

4. **页面遍历靠：递归遍历 AccessibilityNodeInfo**

5. **Android 8\.0 是分水岭**，必须用前台服务，否则无法后台保活，无障碍会失效

---

如果你需要，我可以直接给你：
**可直接运行的最小 Demo 代码**
（开机自启动 \+ 无障碍 \+ 自动识别屏幕文字）

你要吗？我可以直接给你完整代码。

> （注：文档部分内容可能由 AI 生成）
