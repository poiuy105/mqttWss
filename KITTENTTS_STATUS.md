# KittenTTS 完整集成状态报告

## 📊 当前进度

### ✅ 已完成的工作

1. **目录结构准备**
   - ✅ 创建 `app/src/full/cpp/` 目录
   - ✅ 创建 `app/src/full/assets/model/` 目录
   - ✅ 创建 `app/src/full/assets/espeak-ng-data/` 目录
   - ✅ 克隆 espeak-ng 源码到 `app/src/full/cpp/espeak-ng/`

2. **Gradle 配置**
   - ✅ 添加 NDK 版本配置（27.0.12077973）
   - ✅ 配置 ABI 过滤器（arm64-v8a, armeabi-v7a）
   - ✅ 设置不压缩 ONNX/NPZ 文件
   - ✅ 添加 ONNX Runtime 依赖（fullImplementation）
   - ✅ 配置 CMake 外部构建

3. **C++ 原生代码框架**
   - ✅ 创建 CMakeLists.txt
   - ✅ 创建 native-lib.cpp（JNI 桥接）
   - ✅ 实现 nativeInitialize、nativeSynthesize、nativeRelease 接口

4. **Kotlin 封装类**
   - ✅ 加载原生库（System.loadLibrary）
   - ✅ 从 assets 复制模型文件到 filesDir
   - ✅ 调用 native 方法初始化
   - ✅ 实现 AudioTrack 音频播放
   - ✅ 完整的生命周期管理（initialize/speak/stop/release）

5. **CloudTTSPlayer 集成**
   - ✅ 添加 KittenTTS 引擎选项
   - ✅ 实现 speakWithKittenTTS 方法
   - ✅ 降级机制（失败时切换到 Edge-TTS）

---

## ⚠️ 待完成的关键步骤

### 🔴 高优先级（必须完成才能工作）

#### 1. 下载模型文件
**状态**: ❌ 未完成  
**原因**: 网络限制，HuggingFace 访问失败  

**需要下载的文件**:
```
app/src/full/assets/model/
├── kitten_tts.onnx      (25-80 MB)
├── voices.npz           (~1 MB)
└── config.json          (< 1 KB)
```

**下载地址**:
- Nano int8 (25MB): https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8
- Nano (56MB): https://huggingface.co/KittenML/kitten-tts-nano-0.8

**手动操作**:
1. 使用浏览器或代理下载上述 3 个文件
2. 放到 `app/src/full/assets/model/` 目录
3. 参考: [DOWNLOAD_MODELS.md](file://e:\Android\mqttWss\kotlin_temp\DOWNLOAD_MODELS.md)

---

#### 2. 编译 espeak-ng 数据文件
**状态**: ❌ 未完成  
**原因**: 需要在 Linux 环境下编译  

**官方步骤** (来自 kittentts-android):
```bash
cd app/src/full/cpp/espeak-ng

# 配置编译
cmake -Bbuild -DCMAKE_BUILD_TYPE=Release \
-DUSE_MBROLA=OFF \
-DUSE_LIBSONIC=OFF \
-DUSE_LIBPCAUDIO=OFF \
-DUSE_ASYNC=OFF \
-DENABLE_TESTS=OFF

# 编译
cmake --build build -j$(nproc)

# 复制数据文件
ESPEAK_BUILD=$(pwd)/build/espeak-ng-data
ASSETS_DIR="../../assets/espeak-ng-data"

mkdir -p "$ASSETS_DIR/lang/gmw"

cp "$ESPEAK_BUILD/phondata" \
"$ESPEAK_BUILD/phonindex" \
"$ESPEAK_BUILD/phontab" \
"$ESPEAK_BUILD/phondata-manifest" \
"$ESPEAK_BUILD/intonations" \
"$ESPEAK_BUILD/en_dict" \
"$ASSETS_DIR/"

cp "$ESPEAK_BUILD"/lang/gmw/en* "$ASSETS_DIR/lang/gmw/"
cp -r "$ESPEAK_BUILD/voices" "$ASSETS_DIR/"
```

**Windows 替代方案**:
- 使用 WSL2 (Windows Subsystem for Linux)
- 使用 Docker 容器
- 在 Linux 虚拟机中编译后复制文件

---

#### 3. 实现 ONNX 推理逻辑
**状态**: ❌ 占位符代码  

**当前 native-lib.cpp**:
```cpp
JNIEXPORT jfloatArray JNICALL
Java_io_emqx_mqtt_KittenTTSEngine_nativeSynthesize(...) {
    // TODO: 实际实现
    return env->NewFloatArray(0);  // 返回空数组
}
```

**需要实现**:
1. 加载 ONNX 模型文件
2. 创建 OrtSession
3. 文本预处理（使用 espeak-ng）
4. 准备输入张量
5. 运行推理
6. 获取输出音频数据
7. 返回 FloatArray

**参考实现**: 
查看官方仓库的完整代码：https://github.com/gyanendra-baghel/kittentts-android

---

### 🟡 中优先级（优化项）

#### 4. 安装 Android NDK
**状态**: ⚠️ 需要确认  

**检查方法**:
```bash
# 在 Android Studio 中
File > Settings > Appearance & Behavior > System Settings > Android SDK > SDK Tools
```

**需要安装**:
- NDK (Side by side) 27.0.12077973
- CMake 3.22.1

---

#### 5. 测试编译
**状态**: ❌ 未测试  

**编译命令**:
```bash
# 编译 full 版本
./gradlew assembleFullDebug

# 预期问题:
# - 缺少模型文件会导致运行时错误
# - espeak-ng 数据缺失会影响文本预处理
# - ONNX 推理未实现会返回空音频
```

---

## 📝 下一步行动建议

### 方案 A: 分步实施（推荐）

1. **第一步**: 手动下载模型文件
   - 使用代理或镜像下载 3 个文件
   - 放到正确目录
   - 验证文件大小

2. **第二步**: 准备 espeak-ng 数据
   - 使用 WSL2 或 Docker 编译
   - 或者从已编译的版本复制

3. **第三步**: 实现 ONNX 推理
   - 参考官方仓库的 C++ 代码
   - 实现完整的推理流程
   - 测试离线播报

4. **第四步**: 测试和优化
   - 编译 full 版本 APK
   - 在设备上测试离线 TTS
   - 调试和性能优化

---

### 方案 B: 等待官方 SDK

如果完整集成太复杂，可以：
- 保持当前框架代码
- 等待官方发布 AAR 包或简化版 SDK
- 届时只需添加依赖即可

---

## 🎯 关键文件清单

### 已创建的文件
- ✅ `app/build.gradle` - Gradle 配置
- ✅ `app/src/full/cpp/CMakeLists.txt` - CMake 配置
- ✅ `app/src/full/cpp/native-lib.cpp` - JNI 桥接
- ✅ `app/src/full/java/io/emqx/mqtt/KittenTTSEngine.kt` - Kotlin 封装
- ✅ `app/src/standard/java/io/emqx/mqtt/KittenTTSEngine.kt` - 标准版空实现
- ✅ `DOWNLOAD_MODELS.md` - 模型下载指南
- ✅ `KITTENTTS_INTEGRATION.md` - 集成文档

### 需要的文件（待添加）
- ❌ `app/src/full/assets/model/kitten_tts.onnx`
- ❌ `app/src/full/assets/model/voices.npz`
- ❌ `app/src/full/assets/model/config.json`
- ❌ `app/src/full/assets/espeak-ng-data/*` (多个文件)

---

## 💡 技术要点总结

### 架构设计
```
用户调用 speak()
    ↓
KittenTTSEngine (Kotlin)
    ↓
JNI Bridge (native-lib.cpp)
    ↓
ONNX Runtime (推理引擎)
    ↓
espeak-ng (文本预处理)
    ↓
AudioTrack (音频播放)
```

### 离线能力
- ✅ 模型文件打包在 APK 中（assets）
- ✅ 运行时复制到 filesDir
- ✅ 无需网络连接
- ✅ 完全本地推理

### 性能考虑
- 模型大小: 25-80 MB（影响 APK 体积）
- 推理速度: CPU 上约 1-3 秒（取决于文本长度）
- 内存占用: 约 100-200 MB（运行时）

---

## 📚 参考资料

1. **官方仓库**: https://github.com/gyanendra-baghel/kittentts-android
2. **KittenTTS Python**: https://github.com/KittenML/KittenTTS
3. **ONNX Runtime Android**: https://onnxruntime.ai/docs/get-started/with-java.html
4. **espeak-ng**: https://github.com/espeak-ng/espeak-ng
5. **HuggingFace 模型**: https://huggingface.co/KittenML

---

**最后更新**: 2026-04-27  
**集成状态**: 框架完成 60%，等待模型文件和推理实现  
**预计完成时间**: 取决于模型下载和 C++ 实现进度
