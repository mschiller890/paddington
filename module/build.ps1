# Builds the Paddington LSPosed module APK.
#
# Requirements:
#   - JDK 17+ on PATH (javac, java, jar)
#   - Run .\module\fetch-tools.ps1 once so .tools/ and module/lib/api-82.jar exist
#
# Output: dist/paddington-<version>.apk (signed), plus intermediate artifacts
#         under .build/
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\module\build.ps1
#   powershell -ExecutionPolicy Bypass -File .\module\build.ps1 -SkipSign

param(
    [switch]$SkipSign
)

$ErrorActionPreference = "Stop"

# Windows PowerShell 5.1 (.NET Framework) ignores CompressionLevel.NoCompression
# when writing zip entries, which breaks the mandatory uncompressed resources.arsc.
# Re-launch under PowerShell 7 (pwsh) when available.
if ($PSVersionTable.PSEdition -eq "Desktop") {
    $pwsh = Get-Command pwsh -ErrorAction SilentlyContinue
    if ($pwsh) {
        Write-Host "Detected Windows PowerShell 5.1; re-launching under pwsh 7..."
        & $pwsh.Source -NoProfile -ExecutionPolicy Bypass -File $MyInvocation.MyCommand.Path @PSBoundParameters
        exit $LASTEXITCODE
    }
    throw "This script requires PowerShell 7 (pwsh). Install it from https://aka.ms/pscore6 and re-run."
}

$root     = Split-Path $PSScriptRoot -Parent
$moduleDir = $PSScriptRoot
$toolsDir = Join-Path $root ".tools"
$buildDir = Join-Path $root ".build"

$apiJar    = Join-Path $moduleDir "lib\api-82.jar"
$manifest  = Join-Path $moduleDir "AndroidManifest.xml"
$initFile  = Join-Path $moduleDir "assets\xposed_init"
$srcDir    = Join-Path $moduleDir "src"

$platformJar = Join-Path $toolsDir "android-35\android.jar"
$aapt2    = Join-Path $toolsDir "build-tools\34.0.0\aapt2.exe"
$d8       = Join-Path $toolsDir "build-tools\34.0.0\d8.bat"
$zipalign = Join-Path $toolsDir "build-tools\34.0.0\zipalign.exe"
$apksigner = Join-Path $toolsDir "build-tools\34.0.0\apksigner.bat"

$versionCode = "1"
$versionName = "1.0"
$distDir    = Join-Path $root "dist"

# -- sanity checks ---------------------------------------------------------
foreach ($p in @($apiJar, $manifest, $initFile, $platformJar, $aapt2, $d8, $zipalign, $apksigner)) {
    if (-not (Test-Path $p)) {
        throw "Missing required file: $p`nRun .\module\fetch-tools.ps1 first (and make sure you have a JDK on PATH)."
    }
}
$javac = Get-Command javac -ErrorAction SilentlyContinue
if (-not $javac) { throw "javac not found on PATH. Install a JDK (17+) and add its bin directory to PATH." }

# -- 1. compile java sources to classes -------------------------------------
New-Item -ItemType Directory -Path "$buildDir\classes" -Force | Out-Null
Remove-Item -Recurse -Force "$buildDir\classes\*" -ErrorAction SilentlyContinue
Write-Host "== javac =="
& javac --release 8 -cp $apiJar -d "$buildDir\classes" (Get-ChildItem -Recurse $srcDir -Filter *.java | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "== jar =="
& jar cf "$buildDir\classes.jar" -C "$buildDir\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Write-Host "== d8 =="
& $d8 --release --min-api 28 --output $buildDir "$buildDir\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
if (-not (Test-Path "$buildDir\classes.dex")) { throw "d8 did not produce classes.dex" }

# -- 2. assemble APK resources (manifest only; no res/ dir) ------------------
Write-Host "== aapt2 link =="
& $aapt2 link -o "$buildDir\base.apk" --manifest $manifest -I $platformJar `
    --min-sdk-version 28 --target-sdk-version 35 `
    --version-code $versionCode --version-name $versionName --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# -- 3. add dex + assets into the APK ---------------------------------------
Write-Host "== add classes.dex + assets =="
Remove-Item "$buildDir\module-unsigned.apk" -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$src = [System.IO.Compression.ZipFile]::Open("$buildDir\base.apk", [System.IO.Compression.ZipArchiveMode]::Read)
$dst = [System.IO.Compression.ZipFile]::Open("$buildDir\module-unsigned.apk", [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($e in $src.Entries) {
        # Android 11+ requires resources.arsc and the manifest to be stored
        # uncompressed and 4-byte aligned; aapt2 already stored resources.arsc,
        # keep it that way and also store the manifest uncompressed.
        $comp = if ($e.FullName -eq "resources.arsc" -or $e.FullName -eq "AndroidManifest.xml") {
            [System.IO.Compression.CompressionLevel]::NoCompression
        } else {
            [System.IO.Compression.CompressionLevel]::Optimal
        }
        $ne = $dst.CreateEntry($e.FullName, $comp)
        $is = $e.Open(); $os = $ne.Open()
        $is.CopyTo($os); $os.Dispose(); $is.Dispose()
    }
    $ne = $dst.CreateEntry("classes.dex", [System.IO.Compression.CompressionLevel]::NoCompression)
    $os = $ne.Open(); [System.IO.File]::OpenRead("$buildDir\classes.dex").CopyTo($os); $os.Dispose()
    $ne = $dst.CreateEntry("assets/xposed_init", [System.IO.Compression.CompressionLevel]::Optimal)
    $os = $ne.Open(); [System.IO.File]::OpenRead($initFile).CopyTo($os); $os.Dispose()
} finally {
    $dst.Dispose(); $src.Dispose()
}

# -- 4. zipalign + sign ------------------------------------------------------
Write-Host "== zipalign =="
& $zipalign -f 4 "$buildDir\module-unsigned.apk" "$buildDir\module-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

New-Item -ItemType Directory -Path $distDir -Force | Out-Null
$outApk = Join-Path $distDir "paddington-$versionName.apk"

if ($SkipSign) {
    Copy-Item "$buildDir\module-aligned.apk" $outApk -Force
    Write-Host "== unsigned (SkipSign) =="
    Write-Host "Output: $outApk"
    exit 0
}

# Use an existing keystore if present, otherwise generate one.
$keystore = Join-Path $root "paddington.keystore"
$ksPass   = "paddington"
if (-not (Test-Path $keystore)) {
    Write-Host "== generating keystore =="
    & keytool -genkeypair -keystore $keystore -storepass $ksPass -keypass $ksPass `
        -alias paddington -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Paddington, OU=paddington, O=paddington"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
    Write-Host "Keystore created at $keystore (password: $ksPass). Keep this file safe!"
}

Write-Host "== apksigner =="
& $apksigner sign --ks $keystore --ks-pass "pass:$ksPass" --key-pass "pass:$ksPass" `
    --out $outApk "$buildDir\module-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& $apksigner verify $outApk
Write-Host "== done =="
Write-Host "Output: $outApk"
