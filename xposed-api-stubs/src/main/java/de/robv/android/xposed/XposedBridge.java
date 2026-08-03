package de.robv.android.xposed;

import java.util.Set;

/** Compile-only declarations; the runtime implementation is supplied by Xposed. */
public final class XposedBridge {
    private XposedBridge() {}
    public static Set<XC_MethodHook.Unhook> hookAllMethods(
            Class<?> hookClass, String methodName, XC_MethodHook callback) {
        throw new UnsupportedOperationException("compile-only stub");
    }
    public static void log(String text) {}
    public static void log(Throwable throwable) {}
}
