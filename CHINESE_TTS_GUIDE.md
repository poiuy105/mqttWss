# ChineseTtsTflite 集成说明

## 概述

本项目现已支持三种不同的APK版本，每种版本针对不同的使用场景：

### 1. Standard版本（标准版）
- **包名**: `io.emqx.mqtt`
- **特点**: 体积最小，不包含任何离线TTS引擎
- **适用场景**: 仅使用云端TTS（Edge-TTS、百度、有道）
- **APK大小**: 约5-8MB

### 2. Full版本（完整版）
- **包名**: `io.emqx.mqtt.full`
- **特点**: 包含KittenTTS离线引擎
- **适用场景**: 需要高质量离线TTS，支持多种语言
- **APK大小**: 约50-80MB（含模型文件）

### 3. ChineseTTS版本（中文版）🆕
- **包名**: `io.emqx.mqtt.chinesetts`
- **特点**: 包含ChineseTtsTflite离线中文TTS引擎
- **适用场景**: 车机环境，完全离线，专为中文优化
- **APK大小**: 约60-90MB（含模型文件）
- **优势**: 
  - ✅ 完全离线，无需网络
  - ✅ 无需注册，无API限制
  - ✅ 专为中文优化，发音自然
  - ✅ 基于TensorFlow Lite，性能优秀

## GitHub Actions 自动编译

每次推送到master分支时，GitHub Actions会自动编译三个版本的APK：

1. **app-standard-debug.apk** - 标准版
2. **app-full-debug.apk** - 完整版（KittenTTS）
3. **app-chinesetts-debug.apk** - 中文版（ChineseTtsTflite）

## 下载编译好的APK

### 方法1：使用PowerShell脚本（推荐）

```powershell
# 在项目根目录运行
.\download_apks.ps1
```

脚本会自动：
- 从GitHub Actions获取最新编译的APK
- 下载到带时间戳的新文件夹
- 自动解压并显示APK位置

### 方法2：手动下载

1. 访问: https://github.com/poiuy105/mqttWss/actions
2. 点击最新的workflow run
3. 在"Artifacts"部分下载所需的APK
4. 解压下载的zip文件

### 方法3：使用Git Bash脚本

```bash
# 在项目根目录运行
./download_apks.sh
```

## ChineseTtsTflite 模型文件

⚠️ **重要提示**: ChineseTTS版本的APK需要模型文件才能工作。

### 获取模型文件

模型文件需要从 ChineseTtsTflite 项目下载：

**下载地址**: https://gitcode.com/gh_mirrors/ch/ChineseTtsTflite

**需要的文件**:
1. `baker_mapper.json` - 音素映射表 (~50KB)
2. `fastspeech2_quan.tflite` - FastSpeech2量化模型 (~15MB)
3. `mb_melgan.tflite` - 声码器模型 (~8MB)
4. `tacotron2_quan.tflite` - Tacotron2量化模型 (~20MB)

### 放置位置

将模型文件复制到:
```
app/src/main/assets/
├── baker_mapper.json
├── fastspeech2_quan.tflite
├── mb_melgan.tflite
└── tacotron2_quan.tflite
```

### 使用Git Bash下载模型文件

```bash
cd app/src/main/assets

# 从ChineseTtsTflite项目下载
curl -L -o baker_mapper.json "https://github.com/benjaminwan/ChineseTtsTflite/raw/main/app/src/main/assets/baker_mapper.json"
curl -L -o fastspeech2_quan.tflite "https://github.com/benjaminwan/ChineseTtsTflite/raw/main/app/src/main/assets/fastspeech2_quan.tflite"
curl -L -o mb_melgan.tflite "https://github.com/benjaminwan/ChineseTtsTflite/raw/main/app/src/main/assets/mb_melgan.tflite"
curl -L -o tacotron2_quan.tflite "https://github.com/benjaminwan/ChineseTtsTflite/raw/main/app/src/main/assets/tacotron2_quan.tflite"
```

## TTS引擎对比

| 特性 | Standard | Full (KittenTTS) | ChineseTTS |
|------|----------|------------------|------------|
| 离线能力 | ❌ 需要网络 | ✅ 完全离线 | ✅ 完全离线 |
| 中文支持 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 多语言支持 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ |
| APK大小 | 小 (5-8MB) | 中 (50-80MB) | 大 (60-90MB) |
| 需要注册 | ❌ | ❌ | ❌ |
| 音质 | 依赖网络 | 优秀 | 良好 |
| 延迟 | 高（网络） | 低 (<200ms) | 低 (<200ms) |
| 车机适用性 | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

## 选择建议

### 推荐使用 ChineseTTS 版本的场景：
- ✅ 车机环境，网络不稳定
- ✅ 主要使用中文播报
- ✅ 需要完全离线功能
- ✅ 不希望有任何注册或API限制

### 推荐使用 Full 版本的场景：
- ✅ 需要多语言支持
- ✅ 对音质要求极高
- ✅ 设备存储空间充足

### 推荐使用 Standard 版本的场景：
- ✅ 网络环境稳定
- ✅ 希望APK体积最小
- ✅ 不介意使用云端TTS

## 技术细节

### ChineseTtsTflite 架构

```
ChineseTtsEngine.kt
    ↓
TensorFlow Lite Interpreter
    ↓
FastSpeech2 Model (文本 → 梅尔频谱)
    ↓
MB-MelGAN Model (梅尔频谱 → 音频波形)
    ↓
AudioTrack (播放音频)
```

### 关键特性

1. **完全离线**: 所有计算在本地完成
2. **异步处理**: 不阻塞UI线程
3. **自动降级**: 如果ChineseTtsTflite未就绪，自动切换到云端TTS
4. **资源管理**: 正确释放TensorFlow Lite资源

## 常见问题

### Q: ChineseTTS版本APK太大怎么办？
A: 可以只保留`fastspeech2_quan.tflite`和`mb_melgan.tflite`，删除`tacotron2_quan.tflite`，可减少约20MB。

### Q: 如何切换TTS引擎？
A: 在应用的设置页面，TTS设置下拉菜单中选择所需的引擎。

### Q: ChineseTtsTflite初始化失败怎么办？
A: 检查模型文件是否正确放置在`assets`目录，查看logcat日志获取详细错误信息。

### Q: 可以同时安装多个版本吗？
A: 可以！三个版本的包名不同，可以同时安装在同一设备上。

## 更新日志

### 2026-04-27
- ✅ 添加ChineseTtsTflite支持
- ✅ 创建chinesetts产品风味
- ✅ GitHub Actions同时编译三个版本
- ✅ 提供自动化下载脚本

## 相关资源

- **ChineseTtsTflite项目**: https://gitcode.com/gh_mirrors/ch/ChineseTtsTflite
- **TensorFlow Lite**: https://www.tensorflow.org/lite
- **本项目GitHub**: https://github.com/poiuy105/mqttWss
