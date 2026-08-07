package com.mschiller890.paddington;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

/**
 * Paddington - LSPosed module that increases the status bar side padding.
 *
 * <p>On Samsung One UI 8.5 / Android 16 the horizontal padding of the status
 * bar icon containers is computed by the "indicator garden" system in
 * SystemUI. The actual algorithm instance is one of the concrete subclasses
 * of {@code IndicatorGardenAlgorithm} (chosen by cutout type), which override
 * {@code calculateLeftPadding()}/{@code calculateRightPadding()}. Hooking the
 * base class does not work because virtual dispatch always reaches the
 * overrides, so we hook the concrete classes instead and add extra px to the
 * returned padding values.
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "Paddington";

    /**
     * Reads the module's settings from inside the System UI process.
     *
     * <p>System UI runs as a different UID than the module app, so it cannot
     * open the module's SharedPreferences files directly. Instead the module
     * app exposes {@link SettingsProvider}, which runs in the module's own
     * process and serves the configured value over a {@code content://} URI.
     */
    public static final class Settings {

        public static final String PREFS_NAME = "paddington_prefs";
        public static final String KEY_PADDING_DP = "padding_dp";

        /** Fallback used when the settings cannot be read. */
        public static final int DEFAULT_PADDING_DP = 16;

        private static final int MAX_PADDING_DP = 64;

        private static boolean cached = false;
        private static int cachedPaddingDp = DEFAULT_PADDING_DP;

        private Settings() {
        }

        /**
         * Returns the configured extra padding in dp, clamped to a sane range.
         * The value is resolved once and cached, so changing it requires a
         * System UI restart (which is what the settings screen tells the user).
         */
        public static int getPaddingDp() {
            if (cached) {
                return cachedPaddingDp;
            }
            cached = true;
            cachedPaddingDp = queryPaddingDp();
            return cachedPaddingDp;
        }

        private static int queryPaddingDp() {
            try {
                Context context = getContext();
                if (context == null) {
                    XposedBridge.log(TAG + ": no context available, using default padding");
                    return DEFAULT_PADDING_DP;
                }
                Uri uri = Uri.parse("content://" + SettingsProvider.AUTHORITY
                        + "/" + KEY_PADDING_DP);
                Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int dp = cursor.getInt(0);
                            if (dp < 0) {
                                dp = 0;
                            }
                            if (dp > MAX_PADDING_DP) {
                                dp = MAX_PADDING_DP;
                            }
                            return dp;
                        }
                    } finally {
                        cursor.close();
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": failed to read padding setting: " + t);
            }
            return DEFAULT_PADDING_DP;
        }

        private static Context getContext() {
            try {
                Class<?> activityThread =
                        XposedHelpers.findClass("android.app.ActivityThread", null);
                Object app = XposedHelpers.callStaticMethod(activityThread, "currentApplication");
                if (app instanceof Context) {
                    return (Context) app;
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": could not get application context: " + t);
            }
            return null;
        }
    }

    /**
     * After-hook that adds the configured padding (in dp) to a returned padding
     * value. The concrete algorithm objects expose their density through the
     * {@code inputProperties} field.
     */
    public static class PaddingHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object result = param.getResult();
            if (result == null) {
                return;
            }
            int original = (Integer) result;
            float density = 3.0f;
            try {
                Object props = XposedHelpers.getObjectField(param.thisObject, "inputProperties");
                density = XposedHelpers.getFloatField(props, "density");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": could not read density, using 3.0: " + t);
            }
            int paddingDp = Settings.getPaddingDp();
            int extraPx = Math.round(paddingDp * density);
            param.setResult(original + extraPx);
            XposedBridge.log(TAG + ": " + param.method.getDeclaringClass().getSimpleName()
                    + "." + param.method.getName() + " " + original + " -> " + (original + extraPx)
                    + " (density=" + density + ", padding=" + paddingDp + "dp)");
        }
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) {
            return;
        }
        XposedBridge.log(TAG + ": loaded, classloader=" + lpparam.classLoader);

        String[] classes = {
                "com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmCenterCutout",
                "com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmNoCutout",
                "com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmSidelingCenterCutout"
        };
        for (String cls : classes) {
            try {
                Class<?> c = XposedHelpers.findClass(cls, lpparam.classLoader);
                XposedHelpers.findAndHookMethod(c, "calculateLeftPadding", new PaddingHook());
                XposedHelpers.findAndHookMethod(c, "calculateRightPadding", new PaddingHook());
                XposedBridge.log(TAG + ": hooked " + cls);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": hook fail " + cls + ": " + t);
            }
        }
    }
}
