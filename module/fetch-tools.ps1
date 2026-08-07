# Downloads the toolchain required to build Paddington:
#   - Android build-tools (aapt2, zipalign, apksigner, d8)
#   - Android platform android.jar (for aapt2 manifest linking)
#   - XposedBridge API jar (api-82.jar)
#
# Everything is cached under .tools/ in this directory. Run once before
# building; the files are gitignored so they are not committed.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\module\fetch-tools.ps1

$ErrorActionPreference = "Stop"

$moduleDir = $PSScriptRoot
$toolsDir  = Join-Path (Split-Path $PSScriptRoot -Parent) ".tools"
$libDir    = Join-Path $moduleDir "lib"

$buildToolsVersion = "34.0.0"
$platformVersion   = "android-35"

New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
New-Item -ItemType Directory -Path $libDir -Force | Out-Null

function Download {
    param([string]$Url, [string]$OutFile, [string]$Sha256)
    if (Test-Path $OutFile) {
        $existing = (Get-FileHash $OutFile -Algorithm SHA256).Hash
        if ($existing -eq $Sha256) { Write-Host "  cached: $OutFile"; return }
    }
    Write-Host "  downloading $Url"
    Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 600 -OutFile $OutFile
    $actual = (Get-FileHash $OutFile -Algorithm SHA256).Hash
    if ($actual -ne $Sha256) {
        Remove-Item $OutFile -Force
        throw "Checksum mismatch for $Url. Expected $Sha256, got $actual"
    }
}

# --- XposedBridge API 82 ------------------------------------------------------
Write-Host "XposedBridge API 82"
$apiJar = Join-Path $libDir "api-82.jar"
Download `
    -Url "https://artifactory.appodeal.com/appodeal-public/de/robv/android/xposed/api/82/api-82.jar" `
    -OutFile $apiJar `
    -Sha256 "F48C635F1C7469FDEC0E00AD2EA0B7A6B2F5B55065784A35B7CA3A84615E8E25"

# --- Android platform (android.jar) -------------------------------------------
Write-Host "Android platform $platformVersion"
$platformZip = Join-Path $toolsDir "platform-$platformVersion.zip"
Download `
    -Url "https://dl.google.com/android/repository/platform-35_r02.zip" `
    -OutFile $platformZip `
    -Sha256 "0988CACAD01B38A18A47BAC14A0695F246BC76C1B06C0EEB8EB0DC825AB0C8E0"
if (-not (Test-Path (Join-Path $toolsDir "$platformVersion\android.jar"))) {
    Write-Host "  extracting $platformZip"
    Expand-Archive -LiteralPath $platformZip -DestinationPath $toolsDir -Force
}

# --- Android build-tools -------------------------------------------------------
Write-Host "Android build-tools $buildToolsVersion"
$btZip = Join-Path $toolsDir "build-tools-$buildToolsVersion.zip"
Download `
    -Url "https://dl.google.com/android/repository/build-tools_r34-windows.zip" `
    -OutFile $btZip `
    -Sha256 "9BE665AD74EF22BF0E489B37DB465074484DF79B95F26D759945D5C25A47B326"
$btDir = Join-Path $toolsDir "build-tools\$buildToolsVersion"
if (-not (Test-Path $btDir)) {
    Write-Host "  extracting $btZip"
    $tmp = Join-Path $toolsDir "bt-tmp"
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    Expand-Archive -LiteralPath $btZip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Path $btDir -Force | Out-Null
    Get-ChildItem (Join-Path $tmp "android-14") -Force | Copy-Item -Destination $btDir -Recurse -Force
    Remove-Item -Recurse -Force $tmp
}

Write-Host ""
Write-Host "Done. Toolchain ready under $toolsDir"
