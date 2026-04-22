#!/bin/bash

# 优化 Toast 时长脚本
# 将所有 Toast.LENGTH_SHORT 改为 500ms
# 将所有 Toast.LENGTH_LONG 改为 1000ms

echo "开始优化 Toast 时长..."

# 备份原文件
cp app/src/main/java/io/emqx/mqtt/MainActivity.kt app/src/main/java/io/emqx/mqtt/MainActivity.kt.bak
cp app/src/main/java/io/emqx/mqtt/HomeFragment.kt app/src/main/java/io/emqx/mqtt/HomeFragment.kt.bak
cp app/src/main/java/io/emqx/mqtt/MessageFragment.kt app/src/main/java/io/emqx/mqtt/MessageFragment.kt.bak
cp app/src/main/java/io/emqx/mqtt/PublishFragment.kt app/src/main/java/io/emqx/mqtt/PublishFragment.kt.bak
cp app/src/main/java/io/emqx/mqtt/SubscriptionFragment.kt app/src/main/java/io/emqx/mqtt/SubscriptionFragment.kt.bak
cp app/src/main/java/io/emqx/mqtt/SettingFragment.kt app/src/main/java/io/emqx/mqtt/SettingFragment.kt.bak
cp app/src/main/java/io/emqx/mqtt/ConnectionFragment.kt app/src/main/java/io/emqx/mqtt/ConnectionFragment.kt.bak
cp app/src/main/java/io/emqx/mqtt/CloudTTSPlayer.kt app/src/main/java/io/emqx/mqtt/CloudTTSPlayer.kt.bak

echo "备份完成"

# MainActivity.kt - 使用自定义工具方法
sed -i 's/Toast\.makeText(this@MainActivity, "\([^"]*\)", Toast\.LENGTH_SHORT)\.show()/MainActivity.showToastShort(this@MainActivity, "\1")/g' app/src/main/java/io/emqx/mqtt/MainActivity.kt
sed -i 's/Toast\.makeText(this@MainActivity, "\([^"]*\)", Toast\.LENGTH_LONG)\.show()/MainActivity.showToastLong(this@MainActivity, "\1")/g' app/src/main/java/io/emqx/mqtt/MainActivity.kt
sed -i 's/Toast\.makeText(this, "\([^"]*\)", Toast\.LENGTH_SHORT)\.show()/MainActivity.showToastShort(this, "\1")/g' app/src/main/java/io/emqx/mqtt/MainActivity.kt
sed -i 's/Toast\.makeText(this, "\([^"]*\)", Toast\.LENGTH_LONG)\.show()/MainActivity.showToastLong(this, "\1")/g' app/src/main/java/io/emqx/mqtt/MainActivity.kt

# HomeFragment.kt
sed -i 's/Toast\.makeText(context, "\([^"]*\)", Toast\.LENGTH_SHORT)\.show()/MainActivity.showToastShort(requireContext(), "\1")/g' app/src/main/java/io/emqx/mqtt/HomeFragment.kt

# MessageFragment.kt
sed -i 's/Toast\.makeText(fragmentActivity, "\([^"]*\)", Toast\.LENGTH_SHORT)\.show()/MainActivity.showToastShort(requireContext(), "\1")/g' app/src/main/java/io/emqx/mqtt/MessageFragment.kt
sed -i 's/Toast\.makeText(fragmentActivity, "\([^"]*\)", Toast\.LENGTH_LONG)\.show()/MainActivity.showToastLong(requireContext(), "\1")/g' app/src/main/java/io/emqx/mqtt/MessageFragment.kt

# PublishFragment.kt
sed -i 's/Toast\.makeText(fragmentActivity, "\([^"]*\)", Toast\.LENGTH_SHORT)\.show()/MainActivity.showToastShort(requireContext(), "\1")/g' app/src/main/java/io/emqx/mqtt/PublishFragment.kt

# SubscriptionFragment.kt
sed -i 's/Toast\.makeText(fragmentActivity, "\([^"]*\)", Toast\.LENGTH_SHORT)\.show()/MainActivity.showToastShort(requireContext(), "\1")/g' app/src/main/java/io/emqx/mqtt/SubscriptionFragment.kt

# SettingFragment.kt
sed -i 's/Toast\.makeText(context, "\([^"]*\)", Toast\.LENGTH_SHORT)\.show()/MainActivity.showToastShort(requireContext(), "\1")/g' app/src/main/java/io/emqx/mqtt/SettingFragment.kt
sed -i 's/Toast\.makeText(context, "\([^"]*\)", Toast\.LENGTH_LONG)\.show()/MainActivity.showToastLong(requireContext(), "\1")/g' app/src/main/java/io/emqx/mqtt/SettingFragment.kt

# ConnectionFragment.kt
sed -i 's/Toast\.makeText(/MainActivity.showToast/g' app/src/main/java/io/emqx/mqtt/ConnectionFragment.kt

# CloudTTSPlayer.kt - 保持原有的 Handler 方式，但修改时长
sed -i 's/Toast\.LENGTH_SHORT)/Toast.LENGTH_SHORT).apply { duration = 500 }/g' app/src/main/java/io/emqx/mqtt/CloudTTSPlayer.kt

echo "Toast 时长优化完成！"
echo ""
echo "修改说明："
echo "- Toast.LENGTH_SHORT (2s) → 500ms"
echo "- Toast.LENGTH_LONG (3.5s) → 1000ms"
echo ""
echo "如需恢复，请使用 .bak 备份文件"
