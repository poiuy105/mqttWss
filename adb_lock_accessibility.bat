@echo off
chcp 65001 >nul
echo ============================================================
echo   比亚迪车机 - 无障碍永久锁定脚本 (ADB)
echo   兼容: Android 10 (API 29) ~ Android 14+ (API 34)
echo   包名: io.emqx.mqtt
echo ============================================================
echo.

:: 检查adb是否可用
adb version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到adb命令！请确保:
    echo   1. 已安装 Android SDK Platform Tools
    echo   2. adb已加入系统PATH
    echo.
    pause
    exit /b 1
)

:: 显示当前连接的设备
echo [步骤0] 检查设备连接...
adb devices
echo.

:: 检测系统版本（用于区分Android10 vs Android14流程）
for /f "tokens=2 delims=" %%v in ('adb shell getprop ro.build.version.sdk 2^>nul') do set SDK_VER=%%v
echo [信息] 检测到 API Level: %SDK_VER%
echo.

:: 提示用户确认
echo 请确认上面显示的车机设备正确，然后按任意键继续...
pause >nul
echo.

:: ====== 步骤1: 授予WRITE_SECURE_SETTINGS权限（Android 14 必须！）======
echo [步骤1] 授予App写入系统设置权限...
echo        （Android 10兼容：无此命令不影响功能）
adb shell pm grant io.emqx.mqtt android.permission.WRITE_SECURE_SETTINGS
if %errorlevel% equ 0 (
    echo [成功] 已授予 WRITE_SECURE_SETTINGS 权限
) else (
    echo [警告] 授予失败（非致命错误），继续执行...
)
echo.

:: ====== 步骤2: 写入无障碍服务组件名（核心！）======
echo [步骤2] 写入无障碍服务组件名到Secure Settings...
adb shell settings put secure enabled_accessibility_services io.emqx.mqtt/.VoiceAccessibilityService
if %errorlevel% neq 0 (
    echo [失败] 无法写入！请检查ADB权限
    pause
    exit /b 1
)
echo [成功] 已写入

:: ====== 步骤3: 全局启用无障碍功能 ======
echo [步骤3] 启用全局无障碍开关...
adb shell settings put secure accessibility_enabled 1
echo [成功] 已启用

:: ====== 步骤4: 防系统覆盖（比亚迪DiLink兼容）======
echo [步骤4] 设置防重置标记...
adb shell settings put global persist.sys.accessibility_retain 1
echo [成功] 已设置（部分ROM可能不支持此key，不影响核心功能）

echo.
echo ============================================================
echo   验证写入结果...
echo ============================================================
echo.

echo [验证] 当前启用的无障碍服务:
adb shell settings get secure enabled_accessibility_services
echo.
echo [验证] 无障碍全局开关:
adb shell settings get secure accessibility_enabled
echo.
echo [验证] WRITE_SECURE_SETTINGS权限:
adb shell dumpsys package io.emqx.mqtt | findstr "WRITE_SECURE_SETTINGS"
echo.

echo ============================================================
echo   ✅ 全部完成！
echo ============================================================
echo.
echo   ==========================================================
echo   ⚠️  如果你的车机是 Android 11+ (API 30+)，还需要：
echo   ==========================================================
echo.
echo   在车机上手动完成以下操作（仅首次）：
echo.
echo   ① 【允许受限制的设置】
echo      设置 → 应用 → MQTT Assistant → 右上角菜单(⋮)
echo      → 「允许受限制的设置」→ 验证密码/指纹 → 确认
echo.
echo   ② 【开启无障碍服务】
echo      设置 → 无障碍 → MQTT Assistant → 打开开关
echo.
echo   ==========================================================
echo.
echo   通用后续操作（在车机上完成）：
echo   3. 打开App → 设置页 → 点击「车机保活设置」按钮
echo      - 极速模式白名单 ← 最重要！
echo      - 关闭电池优化
echo      - 允许自启动
echo.
echo   完成以上步骤后，车机重启/断电/休眠唤醒，
echo   无障碍权限将永久保持开启（Android 14支持自动恢复）。
echo.
pause
