# MQTT Android 客户端增加 WS/WSS 支持计划

## 目标
为 APK 增加 WebSocket (WS) 和 WebSocket Secure (WSS) 连接方式，支持自定义路径和 TLS 配置。

## 实施步骤

### 1. 修改数据模型 - Connection.kt
- 添加 `protocol` 参数（TCP, SSL, WS, WSS）
- 添加 `path` 参数（WebSocket 路径，默认 `/mqtt`）
- 根据协议类型构建正确的 URI：
  - TCP: `tcp://host:port`
  - SSL: `ssl://host:port`
  - WS: `ws://host:port/path`
  - WSS: `wss://host:port/path`

### 2. 修改界面布局 - fragment_connection.xml
- 添加协议选择 RadioGroup（TCP / SSL / WS / WSS）
- 添加 WebSocket 路径输入框 EditText（默认 `/mqtt`）
- 调整布局顺序

### 3. 修改连接逻辑 - ConnectionFragment.kt
- 添加协议 RadioGroup 和路径 EditText 变量
- 添加协议选择监听器，根据协议自动设置默认端口：
  - TCP: 1883
  - SSL: 8883
  - WS: 8083
  - WSS: 8084
- 传递协议类型和路径到 Connection 类
- 路径输入框根据协议选择显示/隐藏

### 4. 添加字符串资源（必要时）
- 添加协议相关字符串

## 技术说明
- Paho MQTT 库原生支持 WebSocket，通过 `ws://` 和 `wss://` URI scheme
- WSS 需要配置 SSL socket factory（复用现有 SSLUtils）
