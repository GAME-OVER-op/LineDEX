package com.linedex.desktop.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import com.linedex.desktop.BuildConfig;
import com.linedex.desktop.bridge.ILineDexAppEndpoint;
import com.linedex.desktop.bridge.LineDexBridgeService;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class SystemServerHook {
    private static final String TAG = "LineDexSystemHook";
    private static final String PACKAGE_NAME = BuildConfig.APPLICATION_ID;
    private static final String BRIDGE_SERVICE =
            LineDexBridgeService.class.getName();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private SystemServerHook() {
    }

    static void install(
            final ClassLoader classLoader,
            final String modulePath) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            final Class<?> systemServer = XposedHelpers.findClass(
                    "com.android.server.SystemServer", classLoader);
            XposedBridge.hookAllMethods(
                    systemServer,
                    "startOtherServices",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(
                                final MethodHookParam param) {
                            final Context context = (Context)
                                    XposedHelpers.getObjectField(
                                            param.thisObject,
                                            "mSystemContext");
                            if (context == null) {
                                XposedBridge.log(
                                        TAG + ": mSystemContext unavailable");
                                return;
                            }
                            HookRuntime.initialize(
                                    context, classLoader, modulePath);
                            installDisplayPolicyHook(classLoader);
                            installPointerHook(classLoader);
                            bindApplicationBridge(context);
                        }
                    });
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": install failed: " + error);
            XposedBridge.log(error);
        }
    }

    private static void installDisplayPolicyHook(
            final ClassLoader classLoader) {
        try {
            final Class<?> displayManagerService = XposedHelpers.findClass(
                    "com.android.server.display.DisplayManagerService",
                    classLoader);
            XposedBridge.hookAllMethods(
                    displayManagerService,
                    "isExtendedDisplayAllowed",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(
                                final MethodHookParam param) {
                            if (HookRuntime.isDesktopSessionEnabled()) {
                                param.setResult(Boolean.TRUE);
                            }
                        }
                    });
        } catch (Throwable error) {
            HookRuntime.recordError(
                    "HOOK-DISPLAY-001",
                    "isExtendedDisplayAllowed hook failed",
                    error);
        }
    }

    private static void installPointerHook(final ClassLoader classLoader) {
        try {
            final Class<?> inputManagerCallback = XposedHelpers.findClass(
                    "com.android.server.wm.InputManagerCallback",
                    classLoader);
            XposedBridge.hookAllMethods(
                    inputManagerCallback,
                    "getPointerDisplayId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(
                                final MethodHookParam param) {
                            final int target = HookRuntime.pointerDisplayId();
                            if (target >= Display.DEFAULT_DISPLAY) {
                                param.setResult(Integer.valueOf(target));
                            }
                        }
                    });
        } catch (Throwable error) {
            HookRuntime.recordError(
                    "HOOK-POINTER-001",
                    "getPointerDisplayId hook failed",
                    error);
        }
    }

    private static void bindApplicationBridge(final Context context) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] bindAttempt = new Runnable[1];
        final ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(
                    final ComponentName name,
                    final IBinder service) {
                try {
                    final ILineDexAppEndpoint endpoint =
                            ILineDexAppEndpoint.Stub.asInterface(service);
                    endpoint.publishSystemBridge(
                            HookRuntime.bridge(),
                            HookRuntime.INTERFACE_VERSION,
                            android.os.Build.FINGERPRINT);
                    HookRuntime.onApplicationBridgeConnected(endpoint);
                } catch (Throwable error) {
                    HookRuntime.recordError(
                            "BRIDGE-APP-001",
                            "Could not publish system bridge",
                            error);
                    handler.postDelayed(bindAttempt[0], 5_000L);
                }
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                HookRuntime.onApplicationBridgeDisconnected();
                handler.postDelayed(bindAttempt[0], 2_000L);
            }

            @Override
            public void onBindingDied(final ComponentName name) {
                HookRuntime.onApplicationBridgeDisconnected();
                handler.postDelayed(bindAttempt[0], 2_000L);
            }

            @Override
            public void onNullBinding(final ComponentName name) {
                HookRuntime.recordError(
                        "BRIDGE-APP-002",
                        "LineDexBridgeService returned a null binding",
                        null);
                handler.postDelayed(bindAttempt[0], 10_000L);
            }
        };
        bindAttempt[0] = () -> {
            final Intent intent = new Intent();
            intent.setComponent(new ComponentName(PACKAGE_NAME, BRIDGE_SERVICE));
            try {
                final boolean bound = context.bindService(
                        intent,
                        connection,
                        Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
                if (!bound) {
                    handler.postDelayed(bindAttempt[0], 10_000L);
                }
            } catch (Throwable error) {
                Log.w(TAG, "Bridge bind failed", error);
                handler.postDelayed(bindAttempt[0], 10_000L);
            }
        };
        handler.postDelayed(bindAttempt[0], 4_000L);
    }
}
