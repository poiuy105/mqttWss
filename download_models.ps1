# 下载ChineseTtsTflite模型文件的PowerShell脚本
# 使用方法: .\download_models.ps1

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "下载 ChineseTtsTflite 模型文件" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 设置目录
$AssetsDir = "E:\Android\mqttWss\kotlin_temp\app\src\main\assets"
if (-not (Test-Path $AssetsDir)) {
    New-Item -ItemType Directory -Force -Path $AssetsDir | Out-Null
    Write-Host "创建目录: $AssetsDir" -ForegroundColor Green
}

Set-Location $AssetsDir

# 方法1: 从 GitHub Releases 下载 APK 并提取
Write-Host "方法1: 从 GitHub Releases 下载官方APK并提取模型" -ForegroundColor Yellow
Write-Host "URL: https://github.com/benjaminwan/ChineseTtsTflite/releases/download/0.5.0/ChineseTtsTflite-0.5.0-release.apk" -ForegroundColor Gray
Write-Host ""
Write-Host "请手动下载上述APK文件，然后：" -ForegroundColor Yellow
Write-Host "1. 将APK重命名为 .zip" -ForegroundColor White
Write-Host "2. 解压ZIP文件" -ForegroundColor White
Write-Host "3. 从 assets/ 目录复制以下文件到当前目录:" -ForegroundColor White
Write-Host "   - baker_mapper.json" -ForegroundColor White
Write-Host "   - fastspeech2_quan.tflite" -ForegroundColor White
Write-Host "   - mb_melgan.tflite" -ForegroundColor White
Write-Host ""

# 方法2: 使用 Git LFS
Write-Host "方法2: 使用 Git LFS 克隆仓库" -ForegroundColor Yellow
Write-Host "在Git Bash中运行:" -ForegroundColor Gray
Write-Host "git clone https://github.com/benjaminwan/ChineseTtsTflite.git" -ForegroundColor White
Write-Host "cd ChineseTtsTflite" -ForegroundColor White
Write-Host "git lfs pull" -ForegroundColor White
Write-Host "cp app/src/main/assets/*.json app/src/main/assets/*.tflite $AssetsDir/" -ForegroundColor White
Write-Host ""

# 检查现有文件
Write-Host "当前目录文件:" -ForegroundColor Cyan
Get-ChildItem -Path $AssetsDir -Filter "*.json" -ErrorAction SilentlyContinue | Select-Object Name, @{Name="Size(KB)";Expression={[math]::Round($_.Length/1KB,2)}}
Get-ChildItem -Path $AssetsDir -Filter "*.tflite" -ErrorAction SilentlyContinue | Select-Object Name, @{Name="Size(MB)";Expression={[math]::Round($_.Length/1MB,2)}}

Write-Host ""
Write-Host "期望的文件大小:" -ForegroundColor Cyan
Write-Host "baker_mapper.json       ~3.5 KB" -ForegroundColor Gray
Write-Host "fastspeech2_quan.tflite ~18 MB" -ForegroundColor Gray
Write-Host "mb_melgan.tflite        ~22 MB" -ForegroundColor Gray

Write-Host ""
Write-Host "验证文件格式..." -ForegroundColor Yellow
$tfliteFiles = Get-ChildItem -Path $AssetsDir -Filter "*.tflite" -ErrorAction SilentlyContinue
foreach ($file in $tfliteFiles) {
    $content = [System.IO.File]::ReadAllBytes($file.FullName)
    if ($content.Length -lt 1000) {
        Write-Host "⚠️ 警告: $($file.Name) 只有 $($content.Length) 字节，可能不是有效的模型文件" -ForegroundColor Red
    } else {
        Write-Host "✓ $($file.Name): $([math]::Round($content.Length/1MB,2)) MB" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "完成后请重新编译APK" -ForegroundColor Cyan
Write-Host "cd E:\Android\mqttWss\kotlin_temp" -ForegroundColor White
Write-Host ".\gradlew assembleChinesettsDebug" -ForegroundColor White
Write-Host "==========================================" -ForegroundColor Cyan
