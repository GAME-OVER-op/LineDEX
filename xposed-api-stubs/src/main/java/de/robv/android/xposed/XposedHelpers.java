package de.robv.android.xposed;

/** Compile-only declarations; the runtime implementation is supplied by Xposed. */
public final class XposedHelpers {
    private XposedHelpers() {}
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        throw new UnsupportedOperationException("compile-only stub");
    }
    public static Object getObjectField(Object object, String fieldName) {
        throw new UnsupportedOperationException("compile-only stub");
    }
    public static XC_MethodHook.Unhook findAndHookMethod(
            String className, ClassLoader classLoader, String methodName,
            Object... parameterTypesAndCallback) {
        throw new UnsupportedOperationException("compile-only stub");
    }
}
