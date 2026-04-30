# 🔐 Release签名密钥生成与配置指南

## 📋 概述

本文档指导您如何生成release签名密钥，并配置到GitHub Secrets中，确保所有release版本的APK使用相同的签名。

---

## 🚀 快速开始（推荐方案）

### **步骤1：触发Keystore生成Workflow**

1. 访问您的GitHub仓库：
   ```
   https://github.com/您的用户名/mqttWss/actions
   ```

2. 在左侧找到 **"Generate Release Keystore"** workflow

3. 点击 **"Run workflow"** 按钮

4. 选择分支：**master**

5. 点击绿色的 **"Run workflow"** 按钮

---

### **步骤2：等待编译完成**

Workflow会执行以下操作：
- ✅ 生成release-keystore.jks文件
- ✅ 转换为Base64编码
- ✅ 在日志中输出Base64内容
- ✅ 上传keystore文件作为Artifact

**预计耗时**：1-2分钟

---

### **步骤3：复制Base64内容**

1. 点击刚完成的workflow运行记录

2. 展开 **"Convert to Base64"** 步骤

3. 找到类似这样的输出：
   ```
   ==========================================
   📋 COPY THIS TO GITHUB SECRETS:
   ==========================================
   
   Secret Name: RELEASE_KEYSTORE_BASE64
   Secret Value:
   UE9zdGdyZVN...（很长的Base64字符串）...==
   
   ==========================================
   Other secrets to add manually:
   RELEASE_KEYSTORE_PASSWORD: MqttRelease2024
   RELEASE_KEY_ALIAS: mqtt-release
   RELEASE_KEY_PASSWORD: MqttRelease2024
   ==========================================
   ```

4. **复制整个Base64字符串**（从`UE9z`开始到`==`结束）

---

### **步骤4：配置GitHub Secrets**

1. 进入仓库设置：
   ```
   https://github.com/您的用户名/mqttWss/settings/secrets/actions
   ```

2. 点击 **"New repository secret"**

3. 添加以下4个Secrets：

   #### Secret 1: RELEASE_KEYSTORE_BASE64
   - **Name**: `RELEASE_KEYSTORE_BASE64`
   - **Value**: 粘贴刚才复制的Base64字符串
   - 点击 **"Add secret"**

   #### Secret 2: RELEASE_KEYSTORE_PASSWORD
   - **Name**: `RELEASE_KEYSTORE_PASSWORD`
   - **Value**: `MqttRelease2024`
   - 点击 **"Add secret"**

   #### Secret 3: RELEASE_KEY_ALIAS
   - **Name**: `RELEASE_KEY_ALIAS`
   - **Value**: `mqtt-release`
   - 点击 **"Add secret"**

   #### Secret 4: RELEASE_KEY_PASSWORD
   - **Name**: `RELEASE_KEY_PASSWORD`
   - **Value**: `MqttRelease2024`
   - 点击 **"Add secret"**

---

### **步骤5：验证配置**

1. 推送任意代码修改到master分支

2. 查看GitHub Actions中的 **"Android CI"** workflow

3. 检查日志中是否有：
   ```
   Setup release signing
   ...
   Build Release APK
   ...
   Upload Release APK
   ```

4. 下载生成的 `app-release.apk` artifact

5. 验证APK可以正常安装和覆盖更新

---

## 🔧 备选方案

### **方案A：本地生成（如果您有Java环境）**

```bash
# Windows PowerShell
keytool -genkey -v `
  -keystore release-keystore.jks `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -alias mqtt-release `
  -storepass MqttRelease2024 `
  -keypass MqttRelease2024 `
  -dname "CN=MQTT Client,OU=Development,O=EMQX,L=Shenzhen,S=Guangdong,C=CN"

# 转换为Base64
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-keystore.jks")) | Out-File -Encoding ASCII keystore-base64.txt
```

```bash
# Linux/Mac
keytool -genkey -v \
  -keystore release-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias mqtt-release \
  -storepass MqttRelease2024 \
  -keypass MqttRelease2024 \
  -dname "CN=MQTT Client,OU=Development,O=EMQX,L=Shenzhen,S=Guangdong,C=CN"

# 转换为Base64
base64 -w 0 release-keystore.jks > keystore-base64.txt
```

然后手动复制keystore-base64.txt的内容到GitHub Secrets。

---

### **方案B：在线工具生成**

1. 访问：https://www.devglan.com/online-tools/keystore-generator

2. 填写参数：
   - **Alias**: `mqtt-release`
   - **Password**: `MqttRelease2024`
   - **Validity**: `10000` days
   - **Algorithm**: `RSA`
   - **Key Size**: `2048`
   - **First and Last Name**: `MQTT Client`
   - **Organizational Unit**: `Development`
   - **Organization**: `EMQX`
   - **City**: `Shenzhen`
   - **State**: `Guangdong`
   - **Country**: `CN`

3. 点击下载 `.jks` 文件

4. 使用PowerShell或Linux命令转换为Base64（见方案A）

---

## 📊 签名信息汇总

| 参数 | 值 | 说明 |
|------|-----|------|
| **Keystore文件** | release-keystore.jks | Java密钥库文件 |
| **Alias** | mqtt-release | 密钥别名 |
| **Store Password** | MqttRelease2024 | 密钥库密码 |
| **Key Password** | MqttRelease2024 | 密钥密码 |
| **算法** | RSA | 加密算法 |
| **密钥长度** | 2048 bits | 密钥强度 |
| **有效期** | 10000天 (~27年) | 证书有效期 |
| **CN** | MQTT Client | 通用名称 |
| **OU** | Development | 组织单位 |
| **O** | EMQX | 组织 |
| **L** | Shenzhen | 城市 |
| **S** | Guangdong | 省份 |
| **C** | CN | 国家 |

---

## ⚠️ 重要提示

### **安全注意事项**

1. **妥善保管密码**：
   - 密码：`MqttRelease2024`
   - 建议记录在密码管理器中
   - 不要提交到代码库

2. **备份Keystore文件**：
   - 从GitHub Actions下载的Artifact只保留1天
   - 建议下载到本地并妥善备份
   - 丢失keystore将无法更新已发布的APK

3. **不要泄露Secrets**：
   - GitHub Secrets只对仓库管理员可见
   - 不要在日志中打印完整密码
   - 定期轮换密码（可选）

---

### **常见问题**

#### Q1: 为什么需要统一的签名？
**A**: Android要求更新APK时必须使用相同的签名，否则系统会拒绝安装。统一签名确保用户可以无缝升级应用。

#### Q2: 如果忘记密码怎么办？
**A**: 无法恢复！必须重新生成keystore，但这会导致：
- 已安装的用户无法直接更新
- 需要卸载旧版本后重新安装
- Play Store上架的应用无法更新

**建议**：将密码保存在安全的地方（如密码管理器）。

#### Q3: Base64字符串太长，复制不完整怎么办？
**A**: 
- 方法1：从GitHub Actions下载keystore artifact，然后在本地转换
- 方法2：使用在线Base64编码工具
- 方法3：分多次复制，确保没有遗漏字符

#### Q4: 可以在多个仓库使用同一个keystore吗？
**A**: 可以，但不推荐。每个项目应该有独立的签名密钥，以提高安全性。

---

## 🎯 下一步

配置完成后，您可以：

1. **修改主workflow**以同时编译debug和release版本
2. **测试release APK**是否可以正常安装和覆盖更新
3. **删除临时的generate-keystore.yml** workflow（可选）

如需帮助，请参考项目根目录的README文档。

---

**最后更新**: 2024-04-30  
**维护者**: MQTT WSS Team
