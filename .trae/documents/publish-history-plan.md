# MQTT WSS Publish History 功能计划

## 任务概述
1. 增加 publish 历史发布的永久保存和展示
2. 点击行明细时显示删除和重新发布按钮
3. 右上角加号按钮，点击弹出发布对话框

---

## 详细步骤

### 步骤1: 修改 ConfigManager.kt 添加 publishHistory
**文件**: `kotlin_temp/app/src/main/java/io/emqx/mqtt/ConfigManager.kt`

添加：
- `KEY_PUBLISH_HISTORY`
- `publishHistory` 属性

### 步骤2: 创建 dialog_add_publish.xml
**文件**: `kotlin_temp/app/src/main/res/layout/dialog_add_publish.xml`

包含：
- EditText: topic
- EditText: payload
- RadioGroup: qos (0, 1, 2)
- RadioGroup: retained (true/false)
- Button: Publish

### 步骤3: 修改 item_publish.xml
**文件**: `kotlin_temp/app/src/main/res/layout/item_publish.xml`

修改为：
- LinearLayout 水平排列
- 左侧：显示 topic、payload 信息
- 右侧：action_buttons (默认gone)，包含 重新发布 和 删除 按钮

### 步骤4: 修改 PublishRecyclerViewAdapter.kt
**文件**: `kotlin_temp/app/src/main/java/io/emqx/mqtt/PublishRecyclerViewAdapter.kt`

修改：
- 构造函数增加 onDeleteClick 和 onRepublishClick 回调
- ViewHolder 增加 action_buttons、btn_republish、btn_delete
- 点击 item 时展开/收起 action_buttons
- onBindViewHolder 设置按钮点击事件

### 步骤5: 修改 fragment_publish_list.xml
**文件**: `kotlin_temp/app/src/main/res/layout/fragment_publish_list.xml`

修改：
- 添加标题行（标题 + 加号按钮）
- 移除原来的 topic/payload/qos/retained/publish 输入控件
- RecyclerView 高度改为 match_parent

### 步骤6: 修改 PublishFragment.kt
**文件**: `kotlin_temp/app/src/main/java/io/emqx/mqtt/PublishFragment.kt`

修改：
- 添加 showAddPublishDialog() 方法
- 添加 publishToTopic() 方法
- 添加 deletePublish() 和 republish() 回调方法
- 修改 savePublishSettings() 和 loadPublishSettings() 为 savePublishHistory() 和 loadPublishHistory()
- publish 成功后添加到历史记录并持久化

### 步骤7: GitHub推送和编译验证
**操作**:
1. Git add/commit/push
2. `gh run list` 查看编译结果
3. 下载APK并重命名

---

## 修改文件清单

| 文件路径 | 操作 |
|---------|------|
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/ConfigManager.kt` | 修改 |
| `kotlin_temp/app/src/main/res/layout/dialog_add_publish.xml` | 新建 |
| `kotlin_temp/app/src/main/res/layout/item_publish.xml` | 修改 |
| `kotlin_temp/app/src/main/res/layout/fragment_publish_list.xml` | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/PublishRecyclerViewAdapter.kt` | 修改 |
| `kotlin_temp/app/src/main/java/io/emqx/mqtt/PublishFragment.kt` | 修改 |

---

## 验证标准
1. GitHub Actions编译成功
2. Publish界面右上角有加号按钮
3. 点击加号弹出发布对话框
4. 发布成功后列表显示历史记录
5. 点击条目展开显示删除/重新发布按钮
6. 删除按钮可删除记录
7. 重新发布按钮可重新发布该消息