# 比亚迪车机无障碍永久化 - 完整操作指南

## 适用环境
- **车型**: 比亚迪 DiLink 车机
- **系统**: Android 10 (API 29) ~ **Android 14+ (API 34)**
- **App**: MQTT Assistant (`io.emqx.mqtt`)
- **无需**: Root / 比亚迪官方签名 / 改系统分区

---

## 核心原理

### 为什么需要这套方案？
Android 原生硬限制（API 8~12+ 统一规则）：
> **第三方 App 的无障碍权限，重启后系统会自动清空/禁用！**

**Android 14 (API 34) 额外限制：**
1. **受限制的设置（Restricted Settings）**
   - 非应用商店安装的APK，默认被标记为"受限"
   - 直接开启无障碍会提示：`出于安全考虑，此设置目前不可用`
   - 必须手动允许一次：设置 → 应用 → 你的App → 允许受限制的设置
2. **`settings put secure` 依然有效，但有前提条件**
   - 用户已手动授权过无障碍 + 允许受限设置 + ADB授予WRITE_SECURE_SETTINGS

### 解决方案架构（4层防护）
```
第1层 (核心): ADB一次性写入Secure Settings → 重启不丢 ✅
              + pm grant WRITE_SECURE_SETTINGS (API34+) → 支持App内自动恢复
第2层 (保活): 无障碍服务改为前台服务(Foreground) → 不被杀后台 ✅
第3层 (白名单): 比亚迪3项设置 → 自启动+极速模式+电池优化 ✅  
第4层 (兜底): 开机广播检测 + 定时自检 + API34自动恢复写入 ✅
```

---

## 操作步骤（按顺序执行一次即可）

### 第一步：车机开启 ADB 调试（仅首次）

**比亚迪DiLink通用路径：**
1. 车机 → **设置** → **DiLink** → **版本管理** → **多媒体版本**
2. **连续点击「恢复出厂设置」文字区域10次**（不要点按钮，只点文字区域）→ 激活开发者选项
3. 返回设置 → 进入**开发者选项**
4. 打开：
   - **USB调试**（插线用）
   - **无线ADB调试**（推荐，不用插线）
5. 记下车机局域网IP地址（如 `192.168.1.100`）

### 第二步：执行 ADB 锁定命令（核心！）

**方法A：双击一键脚本（推荐）**

直接在电脑上双击运行：
- Windows: `adb_lock_accessibility.bat`
- Linux/Mac: `adb_lock_accessibility.sh`

脚本会自动：
1. 检测设备 API Level（区分 Android 10 / Android 14 流程）
2. 授予 `WRITE_SECURE_SETTINGS` 权限
3. 写入无障碍服务到 Secure Settings
4. 启用全局无障碍开关
5. 验证结果

**方法B：手动执行命令**

先连接车机：
```bash
# 无线连接（推荐）
adb connect 192.168.1.x.x

# 或USB连接
adb devices
```

然后依次执行：

```bash
# ① 授予WRITE_SECURE_SETTINGS权限（Android 14 必须！Android 10兼容忽略即可）
adb shell pm grant io.emqx.mqtt android.permission.WRITE_SECURE_SETTINGS

# ② 写入无障碍服务组件名（最核心！）
adb shell settings put secure enabled_accessibility_services io.emqx.mqtt/.VoiceAccessibilityService

# ③ 全局启用无障碍功能
adb shell settings put secure accessibility_enabled 1

# ④ 设置防重置标记（比亚迪兼容）
adb shell settings put global persist.sys.accessibility_retain 1
```

**验证是否成功：**
```bash
# 应返回: io.emqx.mqtt/.VoiceAccessibilityService
adb shell settings get secure enabled_accessibility_services

# 应返回: 1
adb shell settings get secure accessibility_enabled

# 确认权限授予成功（应包含 WRITE_SECURE_SETTINGS）
adb shell dumpsys package io.emqx.mqtt | grep WRITE_SECURE_SETTINGS
```

### 第三步：车机上手动授权（仅首次！）

> ⚠️ 这一步必须做！ADB只是"锁定/授权状态"，首次需要用户手动激活。

#### 如果是 Android 10（API 29）— 简单模式：
1. 车机 → **设置** → **无障碍**
2. 找到 **MQTT Assistant**
3. **打开开关** → 完成！

#### ⚠️ 如果是 Android 11+（API 30+）— 额外一步：

**① 先允许「受限制的设置」：**
1. 设置 → **应用** → **MQTT Assistant**
2. 点击右上角 **⋮（三点菜单）**
3. 选择 **「允许受限制的设置」**
4. 输入密码/验证指纹 → 提示 **已允许使用受限制的设置**

**② 再开启无障碍：**
1. 回到 **设置 → 无障碍**
2. 找到 **MQTT Assistant**
3. **打开开关** → 确定

### 第四步：App内配置车机白名单（防止被杀）

打开App → **Setting标签页** → 点击以下按钮：

| 按钮 | 作用 | 重要性 |
|------|------|--------|
| **极速模式白名单** | 加入比亚迪最高级后台保活名单 | ⭐⭐⭐ 最重要 |
| **关闭电池优化** | 设为"不优化"，防止省电杀进程 | ⭐⭐ 重要 |
| **允许自启动** | 关闭"禁止自启动"，确保开机广播能收到 | ⭐⭐ 重要 |

每项跳转后，在目标页面中找到本App并**开启/添加到白名单**。

---

## 完成！

完成以上4步后：
- ✅ 车机**任意重启、断电上电、休眠唤醒** → 无障碍自动保持开启
- ✅ **Android 14 特有优势**：即使系统抹除，App会通过 WRITE_SECURE_SETTINGS **自动恢复**（用户全程无感知）
- ✅ 无障碍服务**永不被后台斩杀**
- ✅ App**开机自动拉起**MQTT连接

---

## Android 10 vs Android 14 对比

| 能力 | Android 10 (API 29) | Android 14 (API 34) |
|------|---------------------|---------------------|
| ADB写入secure | ✅ 有效 | ✅ 有效（需额外步骤） |
| pm grant WRITE_SECURE_SETTINGS | 可选（不影响） | **必须**（支持App内自恢复） |
| 「受限制的设置」 | ❌ 无需 | ✅ **必须手动允许一次** |
| 重启后行为 | ADB写入持久保留 | 同左 + App可**代码自动恢复** |
| 前台服务保活 | ✅ 需要 | ✅ 需要 |
| 比亚迪白名单 | ✅ 需要 | ✅ 需要 |
| 自动恢复能力 | ❌ 只能SP兜底引导用户 | ✅ **代码直接写回Secure Settings** |

### Android 14 自动恢复原理（代码层面）

当 App 通过 ADB 获得了 `WRITE_SECURE_SETTINGS` 权限后：

```kotlin
// VoiceAccessibilityService.kt 中的 tryAutoRestoreAccessibility() 方法
// 每30秒自检一次，发现被系统抹除后：
Settings.Secure.putString(contentResolver,
    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    "io.emqx.mqtt/.VoiceAccessibilityService")  // ← 直接写回！
Settings.Secure.putInt(contentResolver,
    Settings.Secure.ACCESSIBILITY_ENABLED,
    1)
```

**效果**：系统清空 → 最多30秒后App自动恢复 → 用户完全无感知

---

## 代码改动说明

### 修改的文件

| 文件 | 改动内容 |
|------|----------|
| `VoiceAccessibilityService.kt` | 前台服务保活 + **API34自动恢复逻辑(tryAutoRestoreAccessibility)** + 10分钟定时自检 + SP标记 |
| `BootReceiver.kt` | POWER_CONNECTED广播监听 + 开机检测无障碍状态 + startForegroundService + 异常标记 |
| `MainActivity.kt` | checkAccessibilityOnStartup() 启动检测 + SP异常标记读取 + 引导弹窗 |
| `BydPermitUtils.kt` | **新增文件** - 比亚迪3项白名单跳转(自启动/极速模式/电池优化) |
| `AndroidManifest.xml` | BootReceiver增加ACTION_POWER_CONNECTED |
| `strings.xml` | 新增9个字符串资源(通知/提示/比亚迪相关) |

### 新增的文件

| 文件 | 说明 |
|------|------|
| `BydPermitUtils.kt` | 比亚迪车机权限工具类 |
| `adb_lock_accessibility.bat` | Windows一键ADB锁定脚本（含API34适配） |
| `adb_lock_accessibility.sh` | Linux/Mac一键ADB锁定脚本（含API34适配） |

---

## FAQ

### Q: Android 14 还能用 `settings put` 吗？
可以。只要满足3个前提：
1. ✅ 用户已**允许受限制的设置**
2. ✅ 用户已在无障碍页面**手动开过一次开关**
3. ✅ 已通过 ADB 执行 `pm grant ... WRITE_SECURE_SETTINGS`

### Q: OTA升级后会丢吗？
- **小版本OTA/日常重启/休眠**: 永久不丢（API34还有自动恢复兜底）
- **整车大版本底层系统OTA**: 可能重置secure数据库 → 重新跑一遍ADB脚本即可

### Q: 会被车机检测/封号吗？
- 仅修改原生无障碍配置，不碰CAN总线/车辆数据/底层系统
- 比亚迪无检测、无风控、不影响质保和保修

### Q: 能完全隐藏前台通知吗？
不能。前台通知是 **API 26+ 安卓强制要求**，已设为低优先级(IMPORTANCE_LOW)，不打扰用户。

### Q: 不用ADB能不能永久？
**不能**。原生安卓硬限制。所有纯代码方案都无法绕过。
- Android 10：纯靠ADB锁死状态
- Android 14：ADB授予权限后，App还能**代码自恢复**（更强）

### Q: 从 Android 10 升级到 Android 14 后需要重做吗？
如果是大版本OTA升级了底层系统：
1. 重新跑一遍 ADB 脚本（多了 `pm grant` 那步）
2. 重新允许一次「受限制的设置」
3. 重新在无障碍里开一次开关
4. 白名单配置一般不受影响（看具体OTA策略）

---

## 故障排查

### 重启后无障碍又关了？

**Android 10 场景：**
```bash
# 1. 检查ADB写入是否还在
adb shell settings get secure enabled_accessibility_services
# 2. 如果没了 → 重新执行 adb_lock_accessibility.bat
# 3. 如果还在但服务没起来 → 检查车机3项白名单
```

**Android 14 场景：**
```bash
# 1. 先检查权限还在不在
adb shell dumpsys package io.emqx.mqtt | grep WRITE_SECURE_SETTINGS
# 2. 如果权限丢了 → 重新执行脚本（含pm grant）
# 3. 如果权限在但还是不行 → 检查"受限制的设置"是否还被允许
# 4. 查看App日志: logcat | grep A11yService
```

### 服务运行中但捕获不到文字？
- 确认无障碍设置里App开关是**开**的
- 确认前台通知显示「无障碍服务运行中」
- 检查CapturedTextManager是否enabled（Setting页开关）

### Android 14 报"出于安全考虑，此设置目前不可用"？
→ 你没做第三步的「允许受限制的设置」，回去操作一下即可。
