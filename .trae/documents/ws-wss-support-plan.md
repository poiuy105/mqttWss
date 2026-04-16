# MQTT WSS 应用修改计划 v2

## 任务概述

1. Setting界面，把5个按钮压缩到1行内，尺寸一致
2. Setting界面，connect/disconnect按钮增加状态监控和不可点击限制
3. Subscription界面，增加订阅历史记录，左滑显示删除/播报按钮
4. Subscription界面，右上角加号按钮，点击弹出订阅界面

***

## 详细步骤

### 步骤1: Setting界面 - 5个按钮压缩到1行

**目标**: 将 test\_tts、test\_popup、test\_overlay\_permission、btn\_connect、btn\_disconnect 共5个按钮压缩到1行内，尺寸一致

**修改文件**:

* `kotlin_temp/app/src/main/res/layout/fragment_connection.xml`

  * 将 btn\_connect 和 btn\_disconnect 移到一起（已经是1行）

  * 将 test\_tts、test\_popup、test\_overlay\_permission 三个按钮改为横向排列在1行

  * 5个按钮统一使用 weight=1，尺寸一致

### 步骤2: Setting界面 - Connect/Disconnect 状态监控

**目标**: Connect按钮在连接中/已连接时不可点击，Disconnect按钮在未连接时不可点击

**修改文件**:

* `kotlin_temp/app/src/main/java/io/emqx/mqtt/MainActivity.kt`

  * 添加 `isConnected` 属性或方法让外部判断连接状态

* `kotlin_temp/app/src/main/java/io/emqx/mqtt/SettingFragment.kt`

  * 在 `setUpView()` 中获取 MQTT 连接状态

  * Connect按钮：未连接时可点击，连接中/已连接时不可点击

  * Disconnect按钮：已连接时可点击，未连接时不可点击

  * 添加 MQTT 状态监听器 `setOnMqttStatusChangedListener`

### 步骤3: Subscription界面 - 左滑显示删除/播报按钮

**目标**: 每个subscription条目左滑时显示删除和播报按钮

**修改文件**:

* `kotlin_temp/app/src/main/res/layout/item_subscription.xml`

  * 修改布局为支持左滑操作的ItemView

  * 添加删除和播报按钮（默认隐藏）

* `kotlin_temp/app/src/main/java/io/emqx/mqtt/SubscriptionRecyclerViewAdapter.kt`

  * 修改为支持左滑的 Adapter

  * 实现滑动显示操作按钮

  * 点击删除：删除订阅数据

  * 点击播报：TTS播报最新消息

### 步骤4: Subscription界面 - 加号按钮弹出订阅

**目标**: 右上角增加加号按钮，点击弹出对话框输入topic和qos进行订阅

**修改文件**:

* `kotlin_temp/app/src/main/res/layout/fragment_subscription_list.xml`

  * 添加右上角加号按钮

  * 移除原来的 topic输入框、QoS单选按钮、Subscribe按钮（移至对话框内）

  * 列表高度改为 match\_parent

* `kotlin_temp/app/src/main/java/io/emqx/mqtt/SubscriptionFragment.kt`

  * 添加加号按钮点击事件，打开AlertDialog

  * Dialog内包含topic输入、QoS选择、订阅按钮

  * 订阅成功后添加到历史记录

* 需要修改 `Subscription` 类添加 `lastMessage` 字段存储最新消息

### 步骤5: 持久化订阅历史

**目标**: 订阅历史永久保存到 SharedPreferences

**修改文件**:

* `kotlin_temp/app/src/main/java/io/emqx/mqtt/ConfigManager.kt`

  * 添加 `subscriptionHistory` 字段用于存储完整订阅历史（包括最新消息）

* `kotlin_temp/app/src/main/java/io/emqx/mqtt/SubscriptionFragment.kt`

  * 保存订阅时同时保存最新消息

  * 加载时恢复完整历史

### 步骤6: GitHub推送和编译验证

**操作**:

1. Git add/commit/push 到 GitHub
2. 使用 `gh run list` 查看编译结果
3. 下载APK并重命名

***

## 修改文件清单

| 文件路径                                                                            | 操作 |
| ------------------------------------------------------------------------------- | -- |
| `kotlin_temp/app/src/main/res/layout/fragment_connection.xml`                   | 修改 |
| `kotlin_temp/app/src/main/res/layout/fragment_subscription_list.xml`            | 修改 |
| `kotlin_temp/app/src/main/res/layout/item_subscription.xml`                     | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/SettingFragment.kt`                 | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/SubscriptionFragment.kt`            | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/SubscriptionRecyclerViewAdapter.kt` | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/Subscription.kt`                    | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/ConfigManager.kt`                   | 修改 |

***

## 验证标准

1. GitHub Actions编译成功（显示绿色勾）
2. 能够下载到APK文件
3. Setting页面5个按钮在1行内，尺寸一致
4. Connect/Disconnect按钮状态正确（根据连接状态启用/禁用）
5. Subscription列表左滑显示删除/播报按钮
6. 点击加号按钮弹出订阅对话框

