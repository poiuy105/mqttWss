# MQTT WSS Message/Voice 界面增强计划

## 任务概述

1. 在 "Tap app name to exclude..." 下方增加 "Only capture frame" 区域，长按可删除
2. 长按某一行 log，可弹出保存到 Only capture frame
3. Only capture frame 下增加前缀和后缀输入框
4. 将导航栏 "message" 改成 "voice"

***

## 详细步骤

### 步骤1: 修改 strings.xml - 将 message 改成 voice

**文件**: `kotlin_temp/app/src/main/res/values/strings.xml`

修改：

```xml
<string name="message">Voice</string>
```

### 步骤2: 修改 CapturedTextManager.kt - 添加 Only Capture Frame 功能

**文件**: `kotlin_temp/app/src/main/java/io/emqx/mqtt/CapturedTextManager.kt`

添加：

* `onlyCaptureFramePrefix` 属性（SharedPreferences 保存）

* `onlyCaptureFrameSuffix` 属性（SharedPreferences 保存）

* `onlyCaptureFrames` 列表（存储只捕获的 frame 数据）

* `addOnlyCaptureFrame()`, `removeOnlyCaptureFrame()`, `getOnlyCaptureFrames()` 方法

* `setOnlyCapturePrefix()`, `getOnlyCapturePrefix()` 方法

* `setOnlyCaptureSuffix()`, `getOnlyCaptureSuffix()` 方法

### 步骤3: 修改 fragment\_message\_list.xml - 添加 Only Capture Frame UI

**文件**: `kotlin_temp/app/src/main/res/layout/fragment_message_list.xml`

添加（在 app\_filter\_container 下方）：

* "Only Capture Frame" 标题（带折叠/展开功能）

* Prefix 输入框（EditText）

* Suffix 输入框（EditText）

* Only Capture Frame 列表（横向滚动或纵向列表）

### 步骤4: 修改 item\_captured\_text.xml - 添加长按菜单

**文件**: `kotlin_temp/app/src/main/res/layout/item_captured_text.xml`

添加：

* 添加 `android:longClickable="true"`

### 步骤5: 修改 CapturedTextAdapter.kt - 支持长按回调

**文件**: `kotlin_temp/app/src/main/java/io/emqx/mqtt/CapturedTextAdapter.kt`

修改：

* 构造函数添加 `onLongClick: (CapturedText) -> Unit` 回调

* 在 onBindViewHolder 中设置 itemView\.setOnLongClickListener

### 步骤6: 修改 MessageFragment.kt - 集成所有功能

**文件**: `kotlin_temp/app/src/main/java/io/emqx/mqtt/MessageFragment.kt`

修改：

* 添加 Only Capture Frame 的 RecyclerView 和 EditText（prefix/suffix）

* 初始化和恢复 Only Capture Frame 设置

* 长按 log 行时保存到 Only Capture Frame

* 长按 Only Capture Frame 项时删除

* prefix/suffix 改变时保存到 CapturedTextManager

### 步骤7: GitHub 推送和编译验证

**操作**:

1. Git add/commit/push
2. `gh run list` 查看编译结果
3. 下载APK并重命名

***

## 修改文件清单

| 文件路径                                                                | 操作 |
| ------------------------------------------------------------------- | -- |
| `kotlin_temp/app/src/main/res/values/strings.xml`                   | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/CapturedTextManager.kt` | 修改 |
| `kotlin_temp/app/src/main/res/layout/fragment_message_list.xml`     | 修改 |
| `kotlin_temp/app/src/main/res/layout/item_captured_text.xml`        | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/CapturedTextAdapter.kt` | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/MessageFragment.kt`     | 修改 |

***

## 验证标准

1. GitHub Actions编译成功
2. 导航栏显示 "Voice" 而非 "Messages"
3. "Only Capture Frame" 区域显示在界面中
4. 长按 log 行可保存到 Only Capture Frame
5. 长按 Only Capture Frame 项可删除
6. Prefix/Suffix 输入框可保存设置

