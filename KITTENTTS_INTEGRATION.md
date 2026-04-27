# KittenTTS 集成指南

## 📌 当前状态

本项目已预留 KittenTTS 集成框架，但**尚未完整实现**。原因如下：

1. **依赖复杂**：需要 ONNX Runtime + espeak-ng C++ 库
2. **资源庞大**：模型文件约 25-80 MB
3. **编译困难**：需要 NDK 27.0.12077973 + CMake 3.22.1
4. **官方状态**：kittentts-android 仍处于开发预览阶段

## 🎯 Gradle Flavors 配置

项目已配置两个构建变体：

### standard（标准版）
- **不包含** KittenTTS
- APK 较小
- Application ID: `io.emqx.mqtt`
- 版本后缀: `-standard`

### full（完整版）
- **包含** KittenTTS 框架代码
- 预留接口，等待官方 SDK
- Application ID: `io.emqx.mqtt.full`
- 版本后缀: `-full`

## 🔧 编译命令

```bash
# 编译标准版
./gradlew assembleStandardDebug

# 编译完整版
./gradlew assembleFullDebug

# 编译所有版本
./gradlew assembleDebug
```

## 📦 完整集成步骤（未来）

当 KittenTTS 发布稳定的 Android SDK 后，按以下步骤集成：

### 1. 添加依赖

在 `app/build.gradle` 的 `fullImplementation` 中添加：

```gradle
fullImplementation 'com.github.gyanendra-baghel:kittentts-android:x.x.x'
```

### 2. 下载模型文件

从 HuggingFace 下载模型到 `app/src/full/assets/model/`：

```bash
mkdir -p app/src/full/assets/model
cd app/src/full/assets/model

# Nano 模型（推荐，25MB）
wget https://huggingface.co/KittenML/kitten-tts-nano-0.8/resolve/main/kitten_tts_nano_v0_8.onnx -O kitten_tts.onnx
wget https://huggingface.co/KittenML/kitten-tts-nano-0.8/resolve/main/voices.npz
wget https://huggingface.co/KittenML/kitten-tts-nano-0.8/resolve/main/config.json
```

### 3. 准备 espeak-ng 数据

参考官方仓库：https://github.com/gyanendra-baghel/kittentts-android

### 4. 实现 KittenTTSEngine

替换 `app/src/full/java/io/emqx/mqtt/KittenTTSEngine.kt` 中的 TODO 部分：

```kotlin
// 加载 ONNX 模型
val modelPath = context.assets.open("model/kitten_tts.onnx")
// 初始化推理会话
// 实现文本预处理
// 实现音频播放
```

## 📚 参考资料

- **官方仓库**: https://github.com/gyanendra-baghel/kittentts-android
- **KittenTTS Python**: https://github.com/KittenML/KittenTTS
- **HuggingFace 模型**: https://huggingface.co/KittenML

## ⚠️ 注意事项

1. **当前状态**：full 版本的 KittenTTS 仅返回空结果，不会真正播报
2. **降级机制**：如果 KittenTTS 不可用，会自动降级到 Edge-TTS
3. **日志输出**：可通过 Home 页面的 Debug Log 查看详细信息

## 🚀 替代方案

如果需要立即使用轻量级离线 TTS，建议：

1. **继续使用讯飞 TTS**（已集成，离线可用）
2. **等待 KittenTTS 发布 AAR 包**
3. **考虑其他轻量级 TTS 引擎**（如 eSpeak NG）

---

**最后更新**: 2026-04-27  
**状态**: 框架已就绪，等待完整实现
