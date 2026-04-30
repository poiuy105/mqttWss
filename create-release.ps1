# MQTT Wss Android App - 创建Release脚本
# 使用方法: .\create-release.ps1 -Version "1.0.0" -Message "首次发布"

param(
    [Parameter(Mandatory=$true)]
    [string]$Version,
    
    [Parameter(Mandatory=$false)]
    [string]$Message = "Release version $Version"
)

# 检查是否在正确的目录
if (-not (Test-Path "app\build.gradle")) {
    Write-Error "错误: 请在项目根目录运行此脚本"
    exit 1
}

Write-Host "🚀 开始创建 Release v$Version" -ForegroundColor Green

# 1. 更新版本号
Write-Host "📝 更新版本号..." -ForegroundColor Yellow
$buildGradlePath = "app\build.gradle"
$content = Get-Content $buildGradlePath -Raw
$content = $content -replace 'versionCode \d+', "versionCode $($Version.Replace('.', ''))"
$content = $content -replace 'versionName "[^"]*"', "versionName `"$Version`""
Set-Content $buildGradlePath -Value $content -NoNewline

# 2. 提交版本更新
Write-Host "💾 提交版本更新..." -ForegroundColor Yellow
git add app/build.gradle
git commit -m "Bump version to $Version"

# 3. 创建标签
Write-Host "🏷️  创建标签 v$Version..." -ForegroundColor Yellow
git tag -a "v$Version" -m "$Message"

# 4. 推送代码和标签
Write-Host "📤 推送到GitHub..." -ForegroundColor Yellow
git push origin master
git push origin "v$Version"

Write-Host "✅ Release v$Version 创建成功！" -ForegroundColor Green
Write-Host "🔗 GitHub Actions将自动构建并创建Release" -ForegroundColor Cyan
Write-Host "📱 请前往 https://github.com/poiuy105/mqttWss/releases 查看Release状态" -ForegroundColor Cyan
