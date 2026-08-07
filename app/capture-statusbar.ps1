# Captures the real status bar strip from the device and saves it as the
# preview background (res/drawable-nodpi/statusbar_native.png).
# Run after applying a new padding or changing the wallpaper, then rebuild.
# Usage: ./capture-statusbar.ps1

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$Adb = "C:\Android\sdk\platform-tools\adb.exe"
$Temp = Join-Path $env:TEMP "opencode\sb.png"
$Out = Join-Path $Root "app\src\main\res\drawable-nodpi\statusbar_native.png"
$OutDir = Split-Path $Out

Write-Host "==> Capturing screen..." -ForegroundColor Cyan
& $Adb shell "su -c 'screencap -p /sdcard/sb.png'"
& $Adb pull /sdcard/sb.png $Temp | Out-Null

Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile($Temp)
if ($src.Width -ne 1080) {
    $src.Dispose()
    throw "Unexpected screen width $($src.Width); crop height is tuned for 1080px."
}
$h = 104
$dst = New-Object System.Drawing.Bitmap 1080, $h
$g = [System.Drawing.Graphics]::FromImage($dst)
$srcRect = New-Object System.Drawing.Rectangle(0, 0, 1080, $h)
$g.DrawImage($src, 0, 0, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
$dst.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$dst.Dispose()
$src.Dispose()
Write-Host "==> Saved $Out" -ForegroundColor Green
Write-Host "==> Rebuild with: ./build-and-run.ps1" -ForegroundColor Green
