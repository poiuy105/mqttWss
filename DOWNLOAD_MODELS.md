# 手动下载 KittenTTS 模型文件

由于网络限制，请手动下载以下文件并放置到指定目录：

## 📥 下载地址

### 选项 1: Nano 模型（推荐，25MB）
- **kitten_tts.onnx**: https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8/resolve/main/kitten_tts_nano_v0_8_int8.onnx
- **voices.npz**: https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8/resolve/main/voices.npz
- **config.json**: https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8/resolve/main/config.json

### 选项 2: Nano 模型（56MB，质量更好）
- **kitten_tts.onnx**: https://huggingface.co/KittenML/kitten-tts-nano-0.8/resolve/main/kitten_tts_nano_v0_8.onnx
- **voices.npz**: https://huggingface.co/KittenML/kitten-tts-nano-0.8/resolve/main/voices.npz
- **config.json**: https://huggingface.co/KittenML/kitten-tts-nano-0.8/resolve/main/config.json

## 📁 放置位置

将下载的 3 个文件放到：
```
app/src/full/assets/model/
├── kitten_tts.onnx
├── voices.npz
└── config.json
```

## 🔧 验证

下载完成后，运行以下命令验证：
```bash
cd e:\Android\mqttWss\kotlin_temp\app\src\full\assets\model
dir
```

应该看到 3 个文件，总大小约 25-60 MB。

## 💡 提示

如果 HuggingFace 访问缓慢，可以尝试：
1. 使用代理或镜像站
2. 使用 Git LFS 克隆整个仓库
3. 从其他镜像源下载
