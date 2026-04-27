@echo off
chcp 65001 >nul
echo ========================================
echo 推送到 GitHub 并触发编译
echo ========================================
echo.

cd /d "%~dp0"

echo [1/3] 检查 Git 状态...
git status --short
echo.

echo [2/3] 推送到 GitHub...
"C:\Program Files\Git\bin\git.exe" push origin master

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo ✅ 推送成功！
    echo ========================================
    echo.
    echo 请访问以下地址查看编译进度：
    echo https://github.com/poiuy105/mqttWss/actions
    echo.
    echo 编译完成后，APK 将在 Artifacts 中提供下载
) else (
    echo.
    echo ========================================
    echo ❌ 推送失败
    echo ========================================
    echo.
    echo 请检查网络连接或代理设置
    echo 或者手动执行: git push origin master
)

pause
