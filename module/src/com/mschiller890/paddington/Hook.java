package com.mschiller890.paddington;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

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

    /** Extra horizontal padding added on each side, in dp. */
    private static final float EXTRA_PADDING_DP = 16.0f;

    /**
     * After-hook that adds {@value #EXTRA_PADDING_DP}dp to a returned padding
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
            int extraPx = Math.round(EXTRA_PADDING_DP * density);
            param.setResult(original + extraPx);
            XposedBridge.log(TAG + ": " + param.method.getDeclaringClass().getSimpleName()
                    + "." + param.method.getName() + " " + original + " -> " + (original + extraPx)
                    + " (density=" + density + ")");
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
