# Paddington

An [LSPosed](https://github.com/LSPosed/LSPosed) module that increases the
status bar side padding on Samsung Galaxy devices running One UI 8.5 /
Android 16 (tested on the Galaxy A56, SM-A566B).

By default the padding is rather tight, so icons like the notification icons,
Wi-Fi and battery sit close to the screen edges. Paddington adds **+16 dp** of
extra horizontal padding on each side so the status bar looks less cramped.

<img width="4320" height="430" alt="output" src="https://github.com/user-attachments/assets/a1cacfbf-b3d5-4450-8356-fd63c7cd9aa2" />

## How it works

On One UI 8.5 the horizontal padding of the status bar icon containers is
computed by the "indicator garden" system inside SystemUI. The algorithm
instance is chosen by the display cutout type:

- `IndicatorGardenAlgorithmCenterCutout` (hole-punch / center punch-hole)
- `IndicatorGardenAlgorithmNoCutout`
- `IndicatorGardenAlgorithmSidelingCenterCutout`

Each of these overrides `calculateLeftPadding()` / `calculateRightPadding()`,
and `IndicatorGardenPresenter` calls them through virtual dispatch. Paddington
hooks the concrete classes directly and adds the extra padding to the returned
value.

> Hooking the base class `IndicatorGardenAlgorithm` does **not** work: virtual
> dispatch always reaches the subclass overrides, so base-class hooks never
> fire. This was the key discovery while debugging — see
> [docs/DEBUGGING.md](docs/DEBUGGING.md).

## Features

- Adds +16 dp horizontal padding to each side of the status bar.
- Zero configuration, zero UI. Activate in LSPosed and done.
- Scoped to `com.android.systemui` only; nothing else is touched.

## Requirements

- A device running **LSPosed** (tested with LSPosed + Zygisk on KernelSU).
- Root access.
- A Galaxy device with One UI 8.5 (Android 16). It may work on other Samsung
  builds, but the indicator garden classes are version-specific.

## Install

1. Download the latest `paddington-*.apk` from
   [Releases](../../releases).
2. Open **LSPosed Manager** → Modules → enable **Paddington**.
3. Make sure `System UI` is ticked in the module's scope.
4. Restart the System UI process (or reboot the device).
5. The status bar padding should now be larger.

To remove, disable the module in LSPosed Manager and restart System UI.

## Build from source

You need a JDK 17+ and [PowerShell 7](https://aka.ms/pscore6) on Windows.

```powershell
# 1. Fetch the toolchain (Android build-tools, android.jar, XposedBridge API).
.\module\fetch-tools.ps1

# 2. Build the signed APK into dist\.
.\module\build.ps1
```

The build script compiles with `javac --release 8`, converts to dex with `d8`,
assembles the APK with `aapt2`, stores `resources.arsc` and the manifest
uncompressed (required on Android 11+), zipaligns and signs with a keystore
that is generated automatically on first build (`paddington.keystore`).

See [docs/CREATING-A-MODULE.md](docs/CREATING-A-MODULE.md) for a detailed
walkthrough of how the module is put together and packaged.

## Documentation

- [docs/CREATING-A-MODULE.md](docs/CREATING-A-MODULE.md) — how the module is
  structured, built and packaged by hand (no Gradle required).
- [docs/DEBUGGING.md](docs/DEBUGGING.md) — the debugging journey: how the hook
  target was found and why hooking the base class doesn't work.

## Disclaimer

This module hooks System UI internals of a specific One UI version. It is
provided as-is with no warranty. If System UI misbehaves, disable the module in
LSPosed Manager.

## License

MIT — see [LICENSE](LICENSE).
