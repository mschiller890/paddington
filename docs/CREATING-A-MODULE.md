# Creating an LSPosed module from scratch

This documents how Paddington is structured and built, so you can build it
yourself or adapt the process to your own module. The whole thing works
**without Gradle or Android Studio** — a JDK and a few command-line tools are
enough.

## What an LSPosed module needs

At a minimum, a module APK contains:

1. **A Java class that implements `IXposedHookLoadPackage`.** This is the
   entry point LSPosed calls when your module is loaded into a target process.
   For Paddington it is
   [`module/src/com/mschiller890/paddington/Hook.java`](../module/src/com/mschiller890/paddington/Hook.java).
2. **`assets/xposed_init`** — a plain text file listing the fully qualified
   class name of that entry point (one per line):
   ```
   com.mschiller890.paddington.Hook
   ```
3. **Manifest metadata** that tells LSPosed Manager the module exists and what
   it does (`xposedmodule`, `xposeddescription`, `xposedminversion`,
   `xposedscope`). See
   [`module/AndroidManifest.xml`](../module/AndroidManifest.xml).

That's it. No launcher activity, no resources, no permissions.

## Project layout

```
paddington/
├── README.md
├── docs/
├── module/
│   ├── AndroidManifest.xml        # manifest with the xposed meta-data
│   ├── assets/xposed_init         # entry point class name
│   ├── lib/api-82.jar             # XposedBridge API (gitignored, fetched by fetch-tools.ps1)
│   ├── src/                       # java sources
│   ├── fetch-tools.ps1            # downloads the toolchain once
│   └── build.ps1                  # compiles, packages, signs
├── .tools/                        # toolchain cache (gitignored)
└── dist/                          # built APK output (gitignored)
```

## Build steps (what build.ps1 does)

### 1. Download the toolchain

[`fetch-tools.ps1`](../module/fetch-tools.ps1) downloads and verifies (SHA-256):

- **XposedBridge API 82** (`api-82.jar`) — needed to compile against
  `IXposedHookLoadPackage` and the hook API.
- **Android platform android.jar** (Android 15 / API 35) — used by `aapt2`
  when linking the manifest.
- **Android build-tools 34.0.0** — `aapt2`, `d8`, `zipalign`, `apksigner`.

Everything is cached under `.tools/`, so it only downloads once.

### 2. Compile to dex

```powershell
javac --release 8 -cp lib\api-82.jar -d classes (sources)
jar cf classes.jar -C classes .
d8 --release --min-api 28 --output . classes.jar
```

- `--release 8` keeps the bytecode old enough for Xposed (the hook framework
  runs in the target process, so you want a widely compatible class file
  version).
- `d8` converts the `.jar` into `classes.dex`. `--min-api 28` matches the
  `minSdkVersion` in the manifest.

### 3. Assemble the APK container

```powershell
aapt2 link -o base.apk --manifest AndroidManifest.xml -I android.jar `
    --min-sdk-version 28 --target-sdk-version 35 `
    --version-code 1 --version-name 1.0
```

Since there is no `res/` directory, this only produces the binary manifest and
`resources.arsc`. The dex and `xposed_init` are added afterwards with a zip
writer.

### 4. Add dex + assets, keeping critical entries uncompressed

Android 11+ refuses to install APKs whose `resources.arsc` (and on some
versions the manifest) are compressed or not 4-byte aligned, with an error
like:

```
-124: Failed parse during installPackageLI: Targeting R+ (version 30 and above)
requires the resources.arsc of installed APKs to be stored uncompressed and
aligned on a 4-byte boundary
```

So when re-zipping, store `resources.arsc` and `AndroidManifest.xml` with
`CompressionLevel.NoCompression`, then run `zipalign -f 4`.

> **Windows gotcha:** Windows PowerShell 5.1 (.NET Framework) silently ignores
> `CompressionLevel.NoCompression` and still deflates the entry. Use
> **PowerShell 7 (pwsh)**, which respects it. `build.ps1` detects this and
> re-launches itself under pwsh automatically.

### 5. Align and sign

```powershell
zipalign -f 4 module-unsigned.apk module-aligned.apk
apksigner sign --ks paddington.keystore --ks-pass pass:<pass> `
    --out paddington-1.0.apk module-aligned.apk
```

`build.ps1` generates `paddington.keystore` automatically on first run (alias
`paddington`, password `paddington`) and uses it for signing. Sign with your
own key if you plan to publish updates.

## Installing and enabling

```bash
adb install -r dist\paddington-1.0.apk
```

Then in **LSPosed Manager**:

1. Modules → enable **Paddington**.
2. Scope → make sure `System UI` is ticked.
3. Restart System UI (or reboot).

Verify it is actually running from the module log:

```bash
adb logcat -s LSPosedBridge    # or: adb logcat | grep Paddington
```

You should see lines like:

```
LSPosedFramework: (com.android.systemui)[com.mschiller890.paddington,...]
    Paddington: IndicatorGardenAlgorithmCenterCutout.calculateLeftPadding 44 -> 89
```

## Notes

- **Versioning:** bump `versionCode`/`versionName` in the manifest and in
  `build.ps1` (`$versionCode`, `$versionName`) for each release.
- **Publishing:** upload the signed APK to a GitHub Release. Users install it
  as a normal APK and enable it in LSPosed Manager.
