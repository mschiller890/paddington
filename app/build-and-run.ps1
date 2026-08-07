# Builds, installs, and launches Paddington on the connected device.
# Usage:
#   ./build-and-run.ps1                # build release + install + launch
#   ./build-and-run.ps1 -BuildOnly     # build only, skip install/launch
#   ./build-and-run.ps1 -RestartSystemUi  # also kill SystemUI after install (applies saved padding)
#   ./build-and-run.ps1 -Debug -VersionCode 2 -VersionName 1.1

param(
    [switch]$BuildOnly,
    [switch]$Debug,
    [switch]$RestartSystemUi,
    [string]$VersionCode = "1",
    [string]$VersionName = "1.0"
)

$ErrorActionPreference = "Stop"

$Root = $PSScriptRoot
$Adb = "C:\Android\sdk\platform-tools\adb.exe"
$LocalProps = Join-Path $Root "local.properties"
if (-not (Test-Path $Adb) -and (Test-Path $LocalProps)) {
    $sdk = (Get-Content $LocalProps | Where-Object { $_ -match "^sdk\.dir=" }) -replace "^sdk\.dir=", ""
    $sdk = $sdk -replace '\\:', ':'
    $Adb = Join-Path $sdk "platform-tools\adb.exe"
}
if (-not (Test-Path $Adb)) {
    Write-Error "adb not found. Set the adb path at the top of $($MyInvocation.MyCommand.Name)."
}

$Variant = if ($Debug) { "debug" } else { "release" }
$Apk = Join-Path $Root "app\build\outputs\apk\$Variant\app-$Variant.apk"

Write-Host "==> Building $Variant (v$VersionName / code $VersionCode)..." -ForegroundColor Cyan
Push-Location $Root
try {
    & "$Root\gradlew.bat" ":app:assemble$($Variant.Substring(0,1).ToUpper())$($Variant.Substring(1))" "-PversionName=$VersionName" "-PversionCode=$VersionCode"
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)." }
} finally {
    Pop-Location
}

if ($BuildOnly) {
    Write-Host "==> Build complete: $Apk" -ForegroundColor Green
    exit 0
}

if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }

Write-Host "==> Installing $Apk ..." -ForegroundColor Cyan
& $Adb install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed (exit $LASTEXITCODE)." }

if ($RestartSystemUi) {
    Write-Host "==> Restarting System UI to apply padding..." -ForegroundColor Cyan
    & $Adb shell "su -c 'pkill -f com.android.systemui'" 2>$null
    Start-Sleep -Seconds 5
}

Write-Host "==> Launching app..." -ForegroundColor Cyan
& $Adb shell "am start -n com.mschiller890.paddington/.MainActivity"
Write-Host "==> Done." -ForegroundColor Green
