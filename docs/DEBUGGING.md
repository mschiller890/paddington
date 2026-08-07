# The debugging journey: why the obvious hook didn't work

This is the story of how Paddington came to be. It started as a simple idea —
"the status bar icons sit too close to the edge, let's push them in a bit" —
and turned into a several-hour hunt through SystemUI internals. If you are
writing an Xposed/LSPosed module yourself, the lesson at the end is worth
reading.

## Setup

- Device: Galaxy A56 (SM-A566B), One UI 8.5, Android 16.
- Root via KernelSU, LSPosed via Zygisk.
- Everything done from a Windows PC over `adb`, plus a lot of `logcat` and
  pulling smali out of the SystemUI APK.

The module targets `com.android.systemui`, hooks a method, adds 16 dp to the
result, done. Simple, right? It wasn't.

## Attempt 1: hook the obvious method

The natural first target was `IndicatorGardenAlgorithm.getDefaultSidePadding()`.
It is in the package
`com.android.systemui.statusbar.phone` and clearly computes the padding.

The hook **never fired**. Not once. The module loaded fine — the
"module loaded, classloader=..." log line appeared — but the method hook
produced zero calls, even after restarting System UI multiple times.

At first I suspected LSPosed itself: maybe it wasn't really injected, maybe the
scope was wrong, maybe the restart didn't take. I checked everything:

- The module was enabled in LSPosed Manager. ✓
- The scope included `System UI`. ✓
- The APK was installed and readable. ✓
- LSPosed Manager even showed the module as active.

So LSPosed was fine. The problem had to be in *what* I was hooking.

## Instrumenting: probes instead of hooks

I switched strategy: instead of trying to change values, I added **probe hooks**
— hooks that just log when a method runs, so I could map out what actually gets
called at runtime.

I hooked `initResources()` on the base class and the presenter method
`IndicatorGardenPresenter.updateGardenWithNewModel()`, and I started watching
the log:

```
SystemUI: module loaded, classloader=...
SystemUI: probe hit IndicatorGardenAlgorithmCenterCutout.initResources
SystemUI: IndicatorGardenAlgorithmCenterCutout.calculateLeftPadding 44 -> 89
```

Two surprises:

1. The *CenterCutout* class was the one running, not the base class.
2. When I hooked the base class's `calculateLeftPadding`, it still never fired
   — even though the *CenterCutout* one clearly did run.

## The root cause: virtual dispatch

Once I pulled the smali apart, it all made sense. The layout is:

- `IndicatorGardenAlgorithm` — the abstract base.
- `IndicatorGardenAlgorithmCenterCutout` — for punch-hole displays.
- `IndicatorGardenAlgorithmNoCutout` — for displays without a cutout.
- `IndicatorGardenAlgorithmSidelingCenterCutout` — for side-hole displays.

`IndicatorGardenAlgorithmFactory.makeAlgorithm()` picks the concrete subclass
based on the display's cutout type. My device has a center punch-hole, so it
got `IndicatorGardenAlgorithmCenterCutout`.

That class **overrides** `initResources()`, `calculateLeftPadding()` and
`calculateRightPadding()`. And in the smali of
`IndicatorGardenPresenter.updateGardenWithNewModel()` you can see it calls the
padding methods on the algorithm object — through normal virtual dispatch,
which in Java/Dalvik always resolves to the **subclass override**.

So my base-class hooks were being bypassed: the base method is never the one
that runs, the override is. Hooking the base class is a no-op. (The base
`getDefaultSidePadding()` is also a tiny final method that the compiler loves
to inline, which makes it an especially bad hook target.)

## The fix: hook the concrete classes

The working approach is to hook the concrete classes directly:

- `IndicatorGardenAlgorithmCenterCutout`
- `IndicatorGardenAlgorithmNoCutout`
- `IndicatorGardenAlgorithmSidelingCenterCutout`

and attach the padding hook to their `calculateLeftPadding()` /
`calculateRightPadding()`. This covers every cutout variant, so the module
works regardless of which algorithm the factory picks.

Result, from the log:

```
IndicatorGardenAlgorithmCenterCutout.calculateLeftPadding 44 -> 89 (density=2.8125)
IndicatorGardenAlgorithmCenterCutout.calculateRightPadding 44 -> 89 (density=2.8125)
```

44 → 89 px on a 2.8125 density display is exactly +16 dp per side. The status
bar now sits nicely inset from the edges.

## Gotchas encountered along the way

### 1. Windows PowerShell 5.1 vs `CompressionLevel.NoCompression`

The first APK I built from the clean script failed to install:

```
-124: Failed parse during installPackageLI: Targeting R+ (version 30 and
above) requires the resources.arsc of installed APKs to be stored
uncompressed and aligned on a 4-byte boundary
```

`resources.arsc` was only 40 bytes yet was written as compressed (45 bytes) —
because .NET Framework's zip writer silently ignores
`CompressionLevel.NoCompression`. **PowerShell 7 respects it.** The build
script now detects Windows PowerShell and re-launches under pwsh.

### 2. Restarting zygote kills the LSPosed companion daemon

`setprop ctl.restart zygote` (or `adb shell su -c killall com.android.systemui`
plus a zygote restart) is the fast way to reload modules without a full reboot.
But on this setup the zygisk `lspd` companion daemon exits when zygote dies and
is **not** restarted automatically. The symptom is that *no* module loads at
all after the restart. Relaunch it with:

```sh
/data/adb/modules/zygisk_lsposed/daemon
```

then restart zygote again.

### 3. Keep old experiment modules from stacking

While testing I still had the earlier "StatusBar Padding" module enabled
alongside Paddington. Both added 16 dp, so the log showed a double push:
`44 -> 89 -> 134`. After uninstalling the old module and removing its rows from
LSPosed's database, only Paddington remained and the values were correct
(`44 -> 89`).

## Key takeaways

- **Hook the concrete class, not the interface/base class.** If a subclass
  overrides a method, virtual dispatch means the base hook never fires. Check
  the smali of the calling code to see which method is actually invoked.
- **Verify with probes first.** A hook that logs "this method ran" tells you
  more than a hook that "should" change something. Find out what *actually*
  runs before changing behavior.
- **Check the factory / provider chain.** The class you think SystemUI uses
  may not be the one it instantiates. Follow `makeAlgorithm()` and the
  presenter to find the real runtime type.
- **Confirm LSPosed is really loaded before blaming it.** "Module loaded"
  lines in the log prove injection works; if those appear but your hook
  doesn't, the problem is your hook target, not LSPosed.
