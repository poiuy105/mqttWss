# MQTT WSS Client - 中英对照文档 / Bilingual Documentation

---

## 📱 应用简介 / Application Overview

**中文**: 这是一款专为车机设计的Android MQTT客户端，支持WSS加密连接、云端TTS语音播报、浮动窗口消息显示，以及Home Assistant智能语音控制集成。  
**English**: This is an Android MQTT client designed for car head units, supporting WSS encrypted connections, cloud TTS voice announcements, floating window message display, and Home Assistant smart voice control integration.

---

## 🚀 快速开始 / Quick Start

### 1. 安装应用 / Install the App

**中文**: 从GitHub Releases下载最新APK并安装到Android设备。  
**English**: Download the latest APK from GitHub Releases and install it on your Android device.

### 2. 配置MQTT连接 / Configure MQTT Connection

**中文**: 
- 打开应用，进入"连接"页面
- 输入MQTT服务器地址（支持wss://协议）
- 设置端口、客户端ID、用户名和密码
- 选择协议类型（WSS/SSL/TCP）
- 点击"连接"按钮

**English**:
- Open the app and go to the "Connection" page
- Enter MQTT server address (supports wss:// protocol)
- Set port, client ID, username, and password
- Select protocol type (WSS/SSL/TCP)
- Click the "Connect" button

### 3. 订阅主题 / Subscribe to Topics

**中文**:
- 切换到"订阅"页面
- 点击"+"添加订阅主题
- 输入主题名称（如：homeassistant/status）
- 点击"确定"完成订阅
- 当该主题收到消息时，会自动触发TTS播报和浮动窗口

**English**:
- Switch to the "Subscription" page
- Click "+" to add a subscription topic
- Enter topic name (e.g., homeassistant/status)
- Click "OK" to complete subscription
- When messages arrive on this topic, TTS announcement and floating window will be triggered automatically

### 4. 发布消息 / Publish Messages

**中文**:
- 切换到"发布"页面
- 点击"+"添加发布配置
- 设置主题、消息内容和QoS等级
- 点击配置项即可发送消息

**English**:
- Switch to the "Publish" page
- Click "+" to add publish configuration
- Set topic, message content, and QoS level
- Click the configuration item to send message

### 5. 启用无障碍服务 / Enable Accessibility Service

**中文**:
- 进入手机"设置" > "无障碍" > "已下载的服务"
- 找到"Voice Accessibility Service"并启用
- 此服务用于捕获屏幕文字和Home Assistant语音响应

**English**:
- Go to phone "Settings" > "Accessibility" > "Downloaded Services"
- Find "Voice Accessibility Service" and enable it
- This service is used to capture screen text and Home Assistant voice responses

### 6. 配置Home Assistant / Configure Home Assistant

**中文**:
- 在"设置"页面输入Home Assistant URL和访问令牌
- 启用"智能返回监控"功能
- 通过语音指令控制智能家居设备
- 响应结果会自动TTS播报

**English**:
- Enter Home Assistant URL and access token in "Settings" page
- Enable "Smart Return Monitor" feature
- Control smart home devices via voice commands
- Response results will be announced via TTS automatically

---

## 🔧 技术路线 / Technical Architecture

### 核心架构 / Core Architecture

**中文**: 采用Service + EventBus解耦架构，确保后台消息处理能力。  
**English**: Adopts Service + EventBus decoupled architecture to ensure background message processing capability.

```
┌─────────────────────────────────────────────┐
│           MqttService (前台服务)              │
│     MqttService (Foreground Service)         │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │  MQTT Client (Paho)                  │   │
│  │  - WSS/SSL/TCP 连接                   │   │
│  │  - WSS/SSL/TCP Connection            │   │
│  └──────────────────────────────────────┘   │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │  TtsFloatWindowManager               │   │
│  │  - 独立于UI生命周期                    │   │
│  │  - Independent from UI lifecycle     │   │
│  │  - Cloud TTS Player                  │   │
│  │  - Float Window Manager              │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
         ▲                    ▲
         │                    │
    EventBus            Direct Call
         │                    │
┌────────┴────────┐  ┌───────┴──────────┐
│  MainActivity   │  │CapturedTextMgr   │
│  & Fragments    │  │(Accessibility)   │
└─────────────────┘  └──────────────────┘
```

### 关键技术栈 / Key Technology Stack

#### 1. MQTT通信 / MQTT Communication

**中文**:
- **库**: Eclipse Paho MQTT Client
- **协议**: 支持WSS (WebSocket Secure)、SSL、TCP
- **特性**: 
  - 自动重连机制（指数退避算法）
  - Last Will遗嘱消息
  - QoS 0/1/2支持
  - 持久会话管理

**English**:
- **Library**: Eclipse Paho MQTT Client
- **Protocol**: Supports WSS (WebSocket Secure), SSL, TCP
- **Features**:
  - Auto-reconnect mechanism (exponential backoff algorithm)
  - Last Will testament message
  - QoS 0/1/2 support
  - Persistent session management

#### 2. TTS语音播报 / TTS Voice Announcement

**中文**:
- **云端TTS**: Edge-TTS API（微软免费服务）
- **本地TTS**: Android系统TTS引擎
- **管理器**: CloudTTSPlayer（异步下载+队列播放）
- **特性**:
  - 多引擎自动降级
  - 音频缓存机制（最多50MB）
  - 强制播报模式（打断当前播放）
  - 主线程安全调用

**English**:
- **Cloud TTS**: Edge-TTS API (Microsoft free service)
- **Local TTS**: Android system TTS engine
- **Manager**: CloudTTSPlayer (async download + queue playback)
- **Features**:
  - Multi-engine automatic fallback
  - Audio cache mechanism (max 50MB)
  - Force announcement mode (interrupt current playback)
  - Main thread safe invocation

#### 3. 浮动窗口 / Floating Window

**中文**:
- **权限**: SYSTEM_ALERT_WINDOW (悬浮窗权限)
- **类型**: TYPE_APPLICATION_OVERLAY (Android 8.0+)
- **管理器**: FloatWindowManager
- **特性**:
  - 右上角显示
  - 自动消失（5秒后）
  - 非触摸、非焦点
  - 主线程创建（Handler切换）

**English**:
- **Permission**: SYSTEM_ALERT_WINDOW (overlay permission)
- **Type**: TYPE_APPLICATION_OVERLAY (Android 8.0+)
- **Manager**: FloatWindowManager
- **Features**:
  - Display at top-right corner
  - Auto-dismiss (after 5 seconds)
  - Non-touchable, non-focusable
  - Main thread creation (Handler switch)

#### 4. 事件总线 / Event Bus

**中文**:
- **实现**: MqttEventBus (LiveData + SharedFlow混合)
- **用途**: 
  - MQTT消息分发
  - 连接状态通知
  - 解耦UI与业务逻辑
- **优势**:
  - 生命周期感知
  - 线程安全
  - 粘性事件支持

**English**:
- **Implementation**: MqttEventBus (LiveData + SharedFlow hybrid)
- **Purpose**:
  - MQTT message distribution
  - Connection status notification
  - Decouple UI from business logic
- **Advantages**:
  - Lifecycle-aware
  - Thread-safe
  - Sticky event support

#### 5. 无障碍服务 / Accessibility Service

**中文**:
- **服务**: VoiceAccessibilityService
- **功能**:
  - 屏幕文字捕获
  - Home Assistant响应识别
  - 全局返回操作模拟
- **管理器**: CapturedTextManager
- **节流机制**: 防止重复触发（500ms间隔）

**English**:
- **Service**: VoiceAccessibilityService
- **Functions**:
  - Screen text capture
  - Home Assistant response recognition
  - Global back action simulation
- **Manager**: CapturedTextManager
- **Throttling**: Prevent duplicate triggers (500ms interval)

#### 6. Home Assistant集成 / Home Assistant Integration

**中文**:
- **API**: Conversation API (/api/conversation/process)
- **认证**: Bearer Token
- **功能**:
  - 自然语言处理
  - 设备控制
  - 状态查询
  - 智能返回监控（检测文本消失自动返回）

**English**:
- **API**: Conversation API (/api/conversation/process)
- **Authentication**: Bearer Token
- **Features**:
  - Natural language processing
  - Device control
  - Status query
  - Smart return monitor (auto-return when text disappears)

### 后台保活策略 / Background Persistence Strategy

**中文**:
1. **前台服务**: MqttService使用startForeground()保持运行
2. **通知栏**: 显示持久化通知，防止系统杀死
3. **开机自启**: BootReceiver监听BOOT_COMPLETED广播
4. **无障碍服务**: 提高进程优先级
5. **电池优化**: 请求忽略电池优化（需用户授权）

**English**:
1. **Foreground Service**: MqttService uses startForeground() to keep running
2. **Notification**: Display persistent notification to prevent system kill
3. **Auto-start on Boot**: BootReceiver listens to BOOT_COMPLETED broadcast
4. **Accessibility Service**: Increase process priority
5. **Battery Optimization**: Request ignore battery optimization (requires user authorization)

### 数据安全 / Data Security

**中文**:
- **传输加密**: WSS/SSL协议保护MQTT通信
- **证书验证**: 支持标准CA证书和自签名证书
- **allowUntrusted开关**: 灵活适配不同服务器配置
- **令牌存储**: Home Assistant Token保存在SharedPreferences

**English**:
- **Transport Encryption**: WSS/SSL protocol protects MQTT communication
- **Certificate Verification**: Supports standard CA certificates and self-signed certificates
- **allowUntrusted Switch**: Flexibly adapt to different server configurations
- **Token Storage**: Home Assistant Token saved in SharedPreferences

---

## 📂 项目结构 / Project Structure

```
kotlin_temp/
├── app/src/main/java/io/emqx/mqtt/
│   ├── MainActivity.kt              # 主Activity / Main Activity
│   ├── MqttService.kt               # MQTT前台服务 / MQTT Foreground Service
│   ├── TtsFloatWindowManager.kt     # TTS和弹窗管理器 / TTS & Float Window Manager
│   ├── CloudTTSPlayer.kt            # 云端TTS播放器 / Cloud TTS Player
│   ├── FloatWindowManager.kt        # 浮动窗口管理器 / Float Window Manager
│   ├── MqttEventBus.kt              # 事件总线 / Event Bus
│   ├── CapturedTextManager.kt       # 文字捕获管理器 / Text Capture Manager
│   ├── VoiceAccessibilityService.kt # 无障碍服务 / Accessibility Service
│   ├── HomeAssistantIntegration.kt  # HA集成 / HA Integration
│   ├── Connection.kt                # MQTT连接配置 / MQTT Connection Config
│   ├── BaseFragment.kt              # Fragment基类 / Fragment Base Class
│   ├── HomeFragment.kt              # 首页 / Home Page
│   ├── SubscriptionFragment.kt      # 订阅页 / Subscription Page
│   ├── PublishFragment.kt           # 发布页 / Publish Page
│   ├── SettingFragment.kt           # 设置页 / Settings Page
│   └── ...
├── app/src/main/res/                # 资源文件 / Resource Files
├── build.gradle                     # 构建配置 / Build Configuration
└── README.md                        # 本文档 / This Document
```

---

## 🔍 常见问题 / FAQ

### Q1: 为什么后台收不到消息？ / Why no messages in background?

**中文**: 确保已授予以下权限：悬浮窗权限、无障碍服务、忽略电池优化。检查MqttService是否在前台运行（通知栏可见）。  
**English**: Ensure the following permissions are granted: overlay permission, accessibility service, ignore battery optimization. Check if MqttService is running in foreground (notification visible).

### Q2: TTS没有声音怎么办？ / What if TTS has no sound?

**中文**: 检查系统音量、TTS引擎是否安装、网络连接（云端TTS需要）。查看logcat日志排查错误。  
**English**: Check system volume, whether TTS engine is installed, network connection (cloud TTS requires). Check logcat logs for errors.

### Q3: 浮动窗口不显示？ / Floating window not showing?

**中文**: 确认已授予悬浮窗权限（设置 > 应用 > 特殊权限 > 悬浮窗）。检查日志中是否有"Can't create handler"错误。  
**English**: Confirm overlay permission is granted (Settings > Apps > Special permissions > Display over other apps). Check logs for "Can't create handler" error.

### Q4: 如何调试？ / How to debug?

**中文**: 使用ADB查看日志：`adb logcat | grep -E "MqttService|TtsFloatWindowManager|MainActivity"`  
**English**: Use ADB to view logs: `adb logcat | grep -E "MqttService|TtsFloatWindowManager|MainActivity"`

---

## 📄 许可证 / License

**中文**: 本项目基于EMQX官方MQTT Android示例修改，遵循原项目许可证。  
**English**: This project is modified based on EMQX official MQTT Android example, following the original project license.

---

## 🙏 致谢 / Acknowledgments

**中文**: 
- EMQX团队提供的MQTT Android示例
- 微软Edge-TTS免费服务
- Home Assistant开源社区
- Eclipse Paho MQTT客户端库

**English**:
- MQTT Android example provided by EMQX team
- Microsoft Edge-TTS free service
- Home Assistant open source community
- Eclipse Paho MQTT client library

---

## 📞 联系方式 / Contact

**中文**: 如有问题或建议，请在GitHub Issues中反馈。  
**English**: If you have questions or suggestions, please provide feedback in GitHub Issues.

---

**最后更新 / Last Updated**: 2026-04-30  
**版本 / Version**: v2.0 (独立管理器架构 / Independent Manager Architecture)
