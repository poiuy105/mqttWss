@echo off
chcp 65001 >nul
echo ========================================
echo 从 GitHub Actions 下载编译的 APK
echo ========================================
echo.

set DOWNLOAD_DIR=%~dp0Downloaded_APKs
if not exist "%DOWNLOAD_DIR%" mkdir "%DOWNLOAD_DIR%"

echo 下载地址: %DOWNLOAD_DIR%
echo.

echo 请访问以下页面手动下载 APK：
echo https://github.com/poiuy105/mqttWss/actions
echo.
echo 步骤：
echo 1. 点击最新的 "Android CI - Dual TTS Build" 工作流
echo 2. 在页面底部的 Artifacts 部分
echo 3. 下载 "app-offline-debug" (包含 ChineseSimpleTTS)
echo 4. 下载 "app-standard-debug" (不含 ChineseSimpleTTS)
echo 5. 将下载的 ZIP 文件解压到: %DOWNLOAD_DIR%
echo.

pause

echo.
echo 解压后，APK 文件位置：
echo 离线版: %DOWNLOAD_DIR%\app-offline-debug\*.apk
echo 标准版: %DOWNLOAD_DIR%\app-standard-debug\*.apk
echo.

explorer "%DOWNLOAD_DIR%"
pause
