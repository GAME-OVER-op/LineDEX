package com.linedex.desktop.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Legacy-compatible LSPosed entry used by Revenge Xposed and LSPosed 1.x. */
public final class LineDexXposedEntry
        implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static volatile String sModulePath = "";

    @Override
    public void initZygote(final StartupParam startupParam) {
        sModulePath = startupParam == null || startupParam.modulePath == null
                ? "" : startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(
            final XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (loadPackageParam == null) {
            return;
        }
        if ("android".equals(loadPackageParam.packageName)
                && ("android".equals(loadPackageParam.processName)
                        || "system_server".equals(loadPackageParam.processName))) {
            SystemServerHook.install(
                    loadPackageParam.classLoader, sModulePath);
            return;
        }
        if ("com.android.systemui".equals(loadPackageParam.packageName)) {
            SystemUiHook.install(loadPackageParam.classLoader);
        }
    }
}
