package com.linedex.desktop.xposed;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;

import com.linedex.desktop.ExternalDisplayCompat;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class SystemUiHook {
    private static final String TAG = "LineDexSystemUiHook";
    private static final String GLOBAL_SESSION = "linedex_session_enabled";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile Context sContext;

    private SystemUiHook() {
    }

    static void install(final ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        captureSystemUiContext(classLoader);
        hookDesktopState(
                classLoader,
                "com.android.wm.shell.shared.desktopmode.DesktopStateImpl");
        hookDesktopState(
                classLoader,
                "com.android.wm.shell.desktopmode.ShellDesktopStateImpl");
    }

    private static void captureSystemUiContext(final ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.systemui.SystemUIApplication",
                    classLoader,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(
                                final MethodHookParam param) {
                            if (param.thisObject instanceof Context) {
                                sContext = ((Context) param.thisObject)
                                        .getApplicationContext();
                            }
                        }
                    });
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": context hook failed: " + error);
        }
    }

    private static void hookDesktopState(
            final ClassLoader classLoader,
            final String className) {
        final Class<?> stateClass;
        try {
            stateClass = XposedHelpers.findClass(className, classLoader);
        } catch (Throwable ignored) {
            return;
        }
        hookBoolean(stateClass, "canEnterDesktopMode");
        hookBoolean(stateClass, "isFreeformEnabled");
        hookBoolean(stateClass, "enterDesktopByDefaultOnFreeformDisplay");
        try {
            XposedBridge.hookAllMethods(
                    stateClass,
                    "isDesktopModeSupportedOnDisplay",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(
                                final MethodHookParam param) {
                            if (!isLineDexEnabled()
                                    || !(param.method instanceof Method)
                                    || ((Method) param.method).getReturnType()
                                            != Boolean.TYPE) {
                                return;
                            }
                            if (targetsExternalDisplay(param.args)) {
                                param.setResult(Boolean.TRUE);
                            }
                        }
                    });
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": " + className
                    + " display support hook failed: " + error);
        }
    }

    private static void hookBoolean(
            final Class<?> stateClass,
            final String methodName) {
        try {
            XposedBridge.hookAllMethods(
                    stateClass,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(
                                final MethodHookParam param) {
                            if (isLineDexEnabled()
                                    && param.method instanceof Method
                                    && ((Method) param.method).getReturnType()
                                            == Boolean.TYPE) {
                                param.setResult(Boolean.TRUE);
                            }
                        }
                    });
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": " + methodName
                    + " hook failed: " + error);
        }
    }

    private static boolean targetsExternalDisplay(final Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return true;
        }
        final Context context = sContext;
        final DisplayManager manager = context == null
                ? null : context.getSystemService(DisplayManager.class);
        for (final Object argument : arguments) {
            if (argument instanceof Display) {
                return ExternalDisplayCompat.isExternal(
                        manager, (Display) argument);
            }
            if (argument instanceof Integer && manager != null) {
                final Display display = manager.getDisplay((Integer) argument);
                if (display != null) {
                    return ExternalDisplayCompat.isExternal(manager, display);
                }
            }
        }
        return false;
    }

    private static boolean isLineDexEnabled() {
        final Context context = sContext;
        return context != null
                && Settings.Global.getInt(
                        context.getContentResolver(), GLOBAL_SESSION, 0) == 1;
    }
}
