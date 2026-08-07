# Building Paddington

Paddington is a normal Android app project built with **Gradle + the Android
Gradle Plugin (AGP)**, with the LSPosed bits added on top. This page documents
how it is structured and how to build and debug it.

## What an LSPosed module needs

At a minimum, a module APK contains:

1. **A Java class that implements `IXposedHookLoadPackage`.** This is the
   entry point LSPosed calls when your module is loaded into a target process.
   For Paddington it is
   [`app/app/src/main/java/com/mschiller890/paddington/Hook.java`](../app/app/src/main/java/com/mschiller890/paddington/Hook.java).
2. **`assets/xposed_init`** — a plain text file listing the fully qualified
   class name of that entry point (one per line):
   ```
   com.mschiller890.paddington.Hook
   ```
3. **Manifest metadata** that tells LSPosed Manager the module exists and what
   it does (`xposedmodule`, `xposeddescription`, `xposedminversion`,
   `xposedscope`). See
   [`app/app/src/main/AndroidManifest.xml`](../app/app/src/main/AndroidManifest.xml).

The module also ships a settings screen (`MainActivity.kt`, Compose) and a
`ContentProvider` that exposes the configured padding to System UI, which runs
as a different UID and cannot read the module's private preferences.

## Project layout

```
paddington/
├── .github/workflows/build.yml    # CI: builds on push/PR, publishes on tags
├── app/                           # the Gradle project
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle/libs.versions.toml  # version catalog (AGP, Compose BOM, ...)
│   └── app/                       # the app module
│       ├── libs/api-82.jar        # XposedBridge API (compileOnly)
│       ├── build.gradle.kts
│       └── src/main/
│           ├── assets/xposed_init
│           ├── AndroidManifest.xml
│           ├── java/com/mschiller890/paddington/
│           │   ├── Hook.java              # LSPosed entry point
│           │   ├── MainActivity.kt        # Compose settings UI
│           │   └── SettingsProvider.java  # content provider for System UI
│           └── res/                       # Compose theme + launcher icons
└── paddington.keystore            # signing key (gitignored, generated)
```

## Building

### Prerequisites

- **JDK 17+** (`java`, `javac`). AGP 9 requires JDK 17 minimum.
- **Android SDK** with:
  - Platform **Android 37** (`platforms;android-37`)
  - Build-tools **37.0.0**
  - Platform-tools (`adb`)

Point the SDK out either via `local.properties` (`sdk.dir=...`) or the
`ANDROID_HOME` environment variable.

### Commands

```powershell
# Debug APK (signed with paddington.keystore, same key as release)
.\app\gradlew.bat :app:assembleDebug

# Release APK
.\app\gradlew.bat :app:assembleRelease

# Override the version for a build
.\app\gradlew.bat :app:assembleRelease -PversionName=1.0 -PversionCode=1
```

Outputs:

- `app/app/build/outputs/apk/debug/app-debug.apk`
- `app/app/build/outputs/apk/release/app-release.apk`

### Versioning

By default `versionCode`/`versionName` come from `defaultConfig` (1 / 1.0).
The CI workflow derives them from the git tag: a tag `v1.2.3` builds
versionName `1.2.3` and versionCode `10203`; otherwise it falls back to
`0.0.<run_number>`. Locally, pass `-PversionName=... -PversionCode=...` to
override.

## The LSPosed-specific Gradle bits

In `app/app/build.gradle.kts`:

- `compileOnly(files("libs/api-82.jar"))` — the XposedBridge API is only
  needed at compile time. It must **not** be packaged into the APK; LSPosed
  provides the classes at runtime in the target process.
- Both `debug` and `release` build types sign with `paddington.keystore`
  (repo root, password `paddington`). This keeps the signature stable so LSPosed
  can update the module in place.

## Continuous integration

[`.github/workflows/build.yml`](../.github/workflows/build.yml):

- runs on **Ubuntu**, sets up JDK 21 + the Android SDK
  (`setup-android`, then `sdkmanager "platforms;android-37" "build-tools;37.0.0"`),
- restores `paddington.keystore` from the `KEYSTORE_B64` secret (or generates a
  throwaway one),
- runs `:app:assembleRelease`, uploads the APK as an artifact, and on `v*`
  tags publishes a GitHub Release with the APK.

To sign releases reproducibly, add a `KEYSTORE_B64` repo secret containing the
base64 of the `paddington.keystore` file:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("paddington.keystore"))
```

## Installing and enabling

```bash
adb install -r app\app\build\outputs\apk\release\app-release.apk
```

Then in **LSPosed Manager**:

1. Modules → enable **Paddington**.
2. Scope → make sure `System UI` is ticked.
3. Restart System UI (or reboot).

Verify it is actually running from the module log:

```bash
adb logcat | grep -i paddington
```

You should see lines like:

```
Paddington: hooked com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmCenterCutout
Paddington: IndicatorGardenAlgorithmCenterCutout.calculateLeftPadding 44 -> 89 (density=2.8125, padding=16dp)
```

## Troubleshooting

### `resources.arsc` must be stored uncompressed (Android 11+)

This is handled automatically by AGP — `resources.arsc` and the manifest are
stored uncompressed and 4-byte aligned out of the box, so you don't need any
custom zip handling.

### Hooks not firing

If the module loads (`Paddington: loaded, classloader=...`) but your hook never
runs, the target is wrong — virtual dispatch resolves to the subclass override.
See [docs/DEBUGGING.md](docs/DEBUGGING.md).

### Zygote restart kills the LSPosed daemon

On Zygisk setups, restarting zygote can leave the LSPosed companion daemon
dead so no module loads at all. Relaunch it and restart zygote again:

```sh
/data/adb/modules/zygisk_lsposed/daemon
```

## Notes

- **Upgrading AGP / Compose:** bump versions in `app/gradle/libs.versions.toml`.
  Note the template pins `androidx.core`/`lifecycle` versions that require
  AGP 9.1+ and compileSdk 37 — keep those three in step.
- **Publishing:** push a `v*` tag; CI builds, signs (if `KEYSTORE_B64` is set)
  and attaches the APK to a GitHub Release. Users install it as a normal APK
  and enable it in LSPosed Manager.
