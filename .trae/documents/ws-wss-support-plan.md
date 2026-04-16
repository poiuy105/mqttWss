# MQTT WSS 应用修改计划

## 任务概述
1. 仅保留竖屏UI，完全移除横屏UI
2. Setting页面增加show debug log开关（默认关闭）
3. Tools下的3个按钮移动到disconnect下方，移除空的tools元素
4. 清空GitHub代码并推送本地代码编译验证

---

## 详细步骤

### 步骤1: 移除横屏UI
**目标**: 删除所有横屏布局相关代码，强制竖屏显示

**修改文件**:
- `kotlin_temp/app/src/main/AndroidManifest.xml`
  - 在MainActivity中添加 `android:screenOrientation="portrait"`

- `kotlin_temp/app/src/main/res/layout-land/activity_main.xml`
  - 删除此文件（横屏布局）

### 步骤2: 添加Show Debug Log开关
**目标**: 在Settings页面添加开关控制Debug Log区域的显示

**修改文件**:
- `kotlin_temp/app/src/main/res/layout/fragment_connection.xml`
  - 在Features区域（Auto Start下方）添加"Show Debug Log"开关
  - 默认值: false (关闭)
  - Debug Log区域（底部200dp的LinearLayout）添加ID以便控制可见性

- `kotlin_temp/app/src/main/java/io/emqx/mqtt/SettingFragment.kt`
  - 添加 `mShowDebugLogSwitch` 变量
  - 在 `setUpView()` 中初始化开关并设置监听器
  - 根据开关状态控制 `mLogText` 父布局的可见性

### 步骤3: 移动Tools按钮到Disconnect下方
**目标**: 将3个测试按钮（test_tts, test_popup, test_overlay_permission）移动到connect/disconnect按钮下方

**修改文件**:
- `kotlin_temp/app/src/main/res/layout/fragment_connection.xml`
  - 将Tools section的3个按钮（test_tts, test_popup, test_overlay_permission）移动到btn_disconnect按钮下方
  - 删除Tools标题和空的Tools容器
  - 调整按钮布局间距

### 步骤4: GitHub推送和编译验证
**目标**: 清空GitHub远程仓库，本地代码推送并验证编译成功

**操作**:
1. 检查Git远程仓库配置
2. 使用 `git push --force` 推送本地kotlin_temp目录下的代码到GitHub
3. 等待GitHub Actions编译完成
4. 使用 `gh run list` 查看编译结果
5. 下载APK到本地并重命名

---

## 修改文件清单

| 文件路径 | 操作 |
|---------|------|
| `kotlin_temp/app/src/main/AndroidManifest.xml` | 修改 - 添加screenOrientation |
| `kotlin_temp/app/src/main/res/layout-land/activity_main.xml` | 删除 |
| `kotlin_temp/app/src/main/res/layout/fragment_connection.xml` | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/SettingFragment.kt` | 修改 |

---

## 验证标准
1. GitHub Actions编译成功（显示绿色勾）
2. 能够下载到app-debug.apk文件
3. 应用安装后仅支持竖屏方向
4. Settings页面有Show Debug Log开关且默认关闭
5. 3个测试按钮位于Disconnect按钮下方