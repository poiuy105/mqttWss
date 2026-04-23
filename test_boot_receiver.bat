@echo off
chcp 65001 >nul
echo ========================================
echo 测试 BootReceiver 是否正常工作
echo ========================================
echo.

REM 检查设备连接
adb devices | findstr "device" >nul
if errorlevel 1 (
    echo [错误] 未检测到设备，请确保设备已连接
    pause
    exit /b 1
)

echo [步骤 1] 检查应用是否安装...
adb shell pm list packages | findstr "io.emqx.mqtt" >nul
if errorlevel 1 (
    echo [错误] 应用未安装
    pause
    exit /b 1
)
echo [成功] 应用已安装

echo.
echo [步骤 2] 检查是否有保存的配置...
adb shell run-as io.emqx.mqtt cat shared_prefs/config.xml 2>nul | findstr "host" >nul
if errorlevel 1 (
    echo [警告] 可能没有保存的 MQTT 配置
    echo         BootReceiver 需要至少配置一次 MQTT 才会自动启动
) else (
    echo [成功] 检测到保存的配置
)

echo.
echo [步骤 3] 清除日志...
adb logcat -c
echo [成功] 日志已清除

echo.
echo [步骤 4] 开始监听 BootReceiver 日志（10秒）...
start "" cmd /k "adb logcat -s BootReceiver:D MainActivity:D | findstr /i "BootReceiver""

timeout /t 2 /nobreak >nul

echo.
echo [步骤 5] 发送 BOOT_COMPLETED 广播...
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p io.emqx.mqtt

echo.
echo ========================================
echo 请在另一个窗口查看日志输出
echo 如果看到 "Received broadcast: android.intent.action.BOOT_COMPLETED"
echo 说明 BootReceiver 正常工作
echo ========================================
echo.
echo [提示] 如果没有收到广播，可能的原因：
echo   1. 应用被强制停止过（需要在设置中重新启用）
echo   2. 自启动权限被关闭（需要在系统设置中允许）
echo   3. 电池优化限制了后台活动
echo   4. 厂商ROM限制了广播接收
echo.
pause
