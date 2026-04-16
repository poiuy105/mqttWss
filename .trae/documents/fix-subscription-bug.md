# MQTT WSS Bug修复计划 - Subscription订阅不生效

## 问题描述
点击subscription界面的加号按钮，填写topic为test001，qos0，点击subscribe后弹窗消失，但subscription界面没有新增订阅行。

## 根本原因
MainActivity.kt 的 `subscribe()` 方法接收了 `listener` 参数但没有使用它：
```kotlin
mClient?.subscribe(subscription.topic, subscription.qos)  // 缺少listener参数
```

正确的调用应该是：
```kotlin
mClient?.subscribe(subscription.topic, subscription.qos, null, listener)
```

这导致订阅成功/失败的回调永远不会被触发。

---

## 修复步骤

### 步骤1: 修复MainActivity.kt的subscribe方法
**文件**: `kotlin_temp/app/src/main/java/io/emqx/mqtt/MainActivity.kt`

将第305行修改为使用listener参数：
```kotlin
mClient?.subscribe(subscription.topic, subscription.qos, null, listener)
```

### 步骤2: GitHub推送和编译验证
**操作**:
1. Git add/commit/push
2. `gh run list` 查看编译结果
3. 下载APK并重命名

---

## 修改文件清单

| 文件路径 | 操作 |
|---------|------|
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/MainActivity.kt` | 修改 - 修复subscribe方法 |

---

## 验证标准
1. GitHub Actions编译成功
2. 订阅成功后列表立即显示新订阅项
3. Toast提示"Subscribed: xxx"