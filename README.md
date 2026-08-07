# Paddington

An [LSPosed](https://github.com/LSPosed/LSPosed) module that increases the
status bar side padding on Samsung Galaxy devices running One UI 8.5 /
Android 16 (tested on the Galaxy A56, SM-A566B).

By default the padding is rather tight, so icons like the notification icons,
Wi-Fi and battery sit close to the screen edges. Paddington adds configurable
extra horizontal padding (default **+16 dp**) on each side so the status bar
looks less cramped.

The app has a proper settings screen built with **Jetpack Compose + Material 3
with dynamic color (Material You)**, so it picks up the system color scheme on
Android 12+.

## Features

- Configurable extra status bar padding, from **0 dp (off)** to **64 dp**,
  default **16 dp**.
- A Material 3 settings UI with dynamic color (Material You) on Android 12+.
- "Apply & restart System UI" saves the value and restarts System UI via root
  (`su`) with an `am force-stop` fallback.
- A **live preview**: the app captures the real status bar, hides it while the
  settings screen is open, and renders the adjusted status bar in its place at
  the top of the screen, updating as you drag the slider.
- Scoped to `com.android.systemui` only; nothing else is touched.

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

The configured value is stored in the module's `SharedPreferences` and exposed
to System UI (a different UID) through a `ContentProvider`.

> Hooking the base class `IndicatorGardenAlgorithm` does **not** work: virtual
> dispatch always reaches the subclass overrides, so base-class hooks never
> fire. This was the key discovery while debugging — see
> [docs/DEBUGGING.md](docs/DEBUGGING.md).

## Requirements

- A device running **LSPosed** (tested with LSPosed + Zygisk on KernelSU).
- Root access.
- A Galaxy device with One UI 8.5 (Android 16). It may work on other Samsung
  builds, but the indicator garden classes are version-specific.

## Install

1. Download the latest `app-release.apk` from
   [Releases](../../releases).
2. Open **LSPosed Manager** → Modules → enable **Paddington**.
3. Make sure `System UI` is ticked in the module's scope.
4. Restart the System UI process (or reboot the device).
5. Open the **Paddington** app, set the padding you want, and press
   **Apply & restart System UI**.

To remove, disable the module in LSPosed Manager and restart System UI.

## Build from source

You need a **JDK 17+** and the **Android SDK** (platform 37, build-tools 37)
on Windows, macOS or Linux.

```powershell
# From the project root:
.\app\gradlew.bat :app:assembleRelease
```

The signed APK is written to:

```
app\app\build\outputs\apk\release\app-release.apk
```

The Gradle build:

- compiles the Xposed hook against the bundled XposedBridge API
  (`app/app/libs/api-82.jar`, `compileOnly` — not shipped in the APK),
- signs with `paddington.keystore` (repo root, password `paddington`),
- produces a debug APK (`.gradlew :app:assembleDebug`) and a release APK.

The build uses the version catalog in
[`app/gradle/libs.versions.toml`](app/gradle/libs.versions.toml). Release
versioning comes from the git tag (`v1.2.3` → versionName `1.2.3`, versionCode
`10203`) via the CI workflow; locally you can override with
`-PversionName=1.0 -PversionCode=1`.

See [docs/BUILDING.md](docs/BUILDING.md) for details and troubleshooting.

## Project layout

```
paddington/
├── .github/workflows/build.yml    # CI: build + publish release on tags
├── app/                           # Gradle project
│   ├── app/
│   │   ├── libs/api-82.jar        # XposedBridge API (compileOnly)
│   │   └── src/main/
│   │       ├── assets/xposed_init
│   │       ├── java/.../Hook.java          # LSPosed entry point
│   │       ├── java/.../MainActivity.kt    # Compose settings UI
│   │       ├── java/.../SettingsProvider.java
│   │       └── res/                       # Compose theme, launcher icons
│   └── gradle/libs.versions.toml  # dependency versions
├── docs/                          # development documentation
└── paddington.keystore            # signing key (gitignored, generated)
```

## Documentation

- [docs/BUILDING.md](docs/BUILDING.md) — how the Gradle project is set up and
  built, the LSPosed bits, and troubleshooting.
- [docs/DEBUGGING.md](docs/DEBUGGING.md) — the debugging journey: how the hook
  target was found and why hooking the base class doesn't work.

## Disclaimer

This module hooks System UI internals of a specific One UI version. It is
provided as-is with no warranty. If System UI misbehaves, disable the module in
LSPosed Manager.

## License

MIT — see [LICENSE](LICENSE).
