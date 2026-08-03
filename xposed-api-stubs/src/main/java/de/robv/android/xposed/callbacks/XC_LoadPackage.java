package de.robv.android.xposed.callbacks;

/** Compile-only declarations for the legacy Xposed API. */
public final class XC_LoadPackage {
    private XC_LoadPackage() {}
    public static final class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
    }
}
