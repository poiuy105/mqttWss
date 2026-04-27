# ChineseTtsTflite 模型文件下载指南

## 当前状态

由于网络限制，无法通过脚本自动下载模型文件。请按照以下步骤手动获取。

## 方法一：从 GitHub Releases 下载 APK 并提取（推荐）

### 步骤：

1. **下载官方APK**
   ```
   https://github.com/benjaminwan/ChineseTtsTflite/releases/download/0.5.0/ChineseTtsTflite-0.5.0-release.apk
   ```

2. **重命名APK为ZIP**
   ```powershell
   # PowerShell
   Rename-Item "ChineseTtsTflite-0.5.0-release.apk" "ChineseTtsTflite-0.5.0-release.zip"
   ```

3. **解压ZIP文件**
   ```powershell
   Expand-Archive -Path "ChineseTtsTflite-0.5.0-release.zip" -DestinationPath "extracted"
   ```

4. **复制模型文件到项目**
   从 `extracted/assets/` 目录复制以下文件到项目的 `app/src/main/assets/` 目录：
   - `baker_mapper.json` (约3.5KB)
   - `fastspeech2_quan.tflite` (约18MB)
   - `mb_melgan.tflite` (约22MB)
   - `tacotron2_quan.tflite` (可选，约15MB)

## 方法二：使用 Git LFS 克隆仓库

```bash
# Git Bash
git clone https://github.com/benjaminwan/ChineseTtsTflite.git
cd ChineseTtsTflite
git lfs pull
cp app/src/main/assets/*.json app/src/main/assets/*.tflite /e/Android/mqttWss/kotlin_temp/app/src/main/assets/
```

## 方法三：从 Hugging Face 下载（如果可用）

访问：https://huggingface.co/models?search=chinese+tts

查找包含 FastSpeech2 和 MB-MelGAN 模型的仓库。

## 需要的文件清单

| 文件名 | 大小 | 必需性 | 说明 |
|--------|------|--------|------|
| baker_mapper.json | ~3.5KB | ✅ 必需 | 音素映射表 |
| fastspeech2_quan.tflite | ~18MB | ✅ 必需 | FastSpeech2量化模型 |
| mb_melgan.tflite | ~22MB | ✅ 必需 | MB-MelGAN声码器 |
| tacotron2_quan.tflite | ~15MB | ⚠️ 可选 | Tacotron2备用模型 |

**总计**: 约43-58MB

## 验证文件

下载完成后，在Git Bash中运行：

```bash
cd /e/Android/mqttWss/kotlin_temp/app/src/main/assets
ls -lh *.json *.tflite
file *.tflite
```

应该看到：
- `.tflite` 文件显示为 "data" 类型（二进制文件）
- 文件大小与上述清单匹配

## 完成后的下一步

1. 确保文件已复制到正确位置：
   ```
   e:\Android\mqttWss\kotlin_temp\app\src\main\assets\
   ├── baker_mapper.json
   ├── fastspeech2_quan.tflite
   └── mb_melgan.tflite
   ```

2. 重新编译APK：
   ```bash
   cd /e/Android/mqttWss/kotlin_temp
   ./gradlew assembleChinesettsDebug
   ```

3. 安装并测试

## 注意事项

⚠️ **重要提示**：
- 模型文件较大（总共约40-60MB），请确保有足够的存储空间
- 不要将这些大文件提交到Git仓库（已在.gitignore中排除）
- 每个版本的APK都会包含这些模型文件，导致APK体积增大
- 如果只需要Standard版本，可以不下载模型文件

## 故障排除

### 问题1：编译时找不到模型文件
**解决**：确保文件在 `app/src/main/assets/` 目录下

### 问题2：运行时TTS失败
**解决**：
1. 检查日志确认模型加载成功
2. 验证文件格式是否正确（应该是二进制.tflite文件，不是HTML）
3. 确认文件大小合理（不应该只有几KB）

### 问题3：内存不足
**解决**：
- ChineseTtsTflite需要较多内存
- 确保设备至少有512MB可用内存
- 考虑关闭其他应用

## 替代方案

如果无法获取模型文件，可以考虑：

1. **使用云端TTS**：Edge-TTS、百度TTS等
2. **使用系统TTS**：讯飞TTS或其他已安装的TTS引擎
3. **等待网络改善**：稍后重试下载

---

**最后更新**: 2026-04-27
**相关Issue**: 网络限制导致无法自动下载模型文件
