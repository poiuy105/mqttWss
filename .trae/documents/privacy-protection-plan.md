# Setting 页面隐私保护功能计划

## 任务概述

Setting 页面所有输入框增加隐私保护功能：

* 已保存的字符仅展示前2个字符，之后的字符用星号

* 点击输入框后面的眼睛图标时，显示原本字符

* 再点击眼睛时恢复成隐私保护模式

* 默认为隐私保护模式

## 需要添加隐私保护的输入框

1. host
2. port
3. path
4. clientid
5. username
6. password

## 实现方案

### 1. 修改 fragment\_connection.xml

* 为每个输入框的 TextInputLayout 添加 `app:endIconMode="custom"` 和 `app:endIconDrawable="@drawable/ic_eye_off"`

* 需要创建眼睛图标资源（ic\_eye\_off 和 ic\_eye\_on）

### 2. 创建眼睛图标资源

* 创建 `ic_eye_off.xml`（关闭状态 - 隐私模式）

* 创建 `ic_eye_on.xml`（打开状态 - 显示明文）

### 3. 修改 SettingFragment.kt

* 为每个输入框创建对应的原始值存储变量

* 创建辅助方法来处理隐私显示逻辑

* 为每个 TextInputLayout 设置 endIconOnClickListener 来切换显示/隐藏

* 加载配置时应用隐私模式

* 保存配置时获取原始值（而不是隐私显示的值）

## 隐私显示逻辑

```kotlin
fun maskText(text: String): String {
    if (text.length <= 2) return text
    return text.take(2) + "*".repeat(text.length - 2)
}
```

## 开关逻辑

* 点击眼睛图标 → 切换显示模式

* 更新 endIconDrawable（ic\_eye\_off ↔ ic\_eye\_on）

* 更新 EditText 显示内容（masked ↔ original）

## 修改文件清单

| 文件路径                                                            | 操作                  |
| --------------------------------------------------------------- | ------------------- |
| `kotlin_temp/app/src/main/res/drawable/ic_eye_off.xml`          | 新建                  |
| `kotlin_temp/app/src/main/res/drawable/ic_eye_on.xml`           | 新建                  |
| `kotlin_temp/app/src/main/res/layout/fragment_connection.xml`   | 修改 - 为输入框添加 endIcon |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/SettingFragment.kt` | 修改 - 添加隐私保护逻辑       |

## 验证标准

1. GitHub Actions 编译成功
2. 所有输入框默认显示隐私模式（前2个字符 + 星号）
3. 点击眼睛图标可以切换显示/隐藏
4. 保存配置时正确获取原始值
5. 能够下载到 APK 文件

