package com.linedex.desktop.xposed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;

import com.linedex.desktop.ExternalDisplayCompat;
import com.linedex.desktop.BuildConfig;
import com.linedex.desktop.LineDexPreferences;
import com.linedex.desktop.bridge.ILineDexAppEndpoint;
import com.linedex.desktop.bridge.ILineDexCallback;
import com.linedex.desktop.bridge.ILineDexSystemBridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XposedBridge;

final class HookRuntime {
    static final int INTERFACE_VERSION = 1;
    private static final String TAG = "LineDexHookRuntime";
    private static final String PACKAGE_NAME = BuildConfig.APPLICATION_ID;
    private static final int INVALID_DISPLAY = -1;

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean DESKTOP_ENABLED = new AtomicBoolean();
    private static final AtomicInteger POINTER_TARGET =
            new AtomicInteger(LineDexPreferences.POINTER_AUTO);
    private static final RemoteCallbackList<ILineDexCallback> CALLBACKS =
            new RemoteCallbackList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POINTER_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "LineDexPointerRoute");
                thread.setDaemon(true);
                return thread;
            });

    private static volatile Context sContext;
    private static volatile ClassLoader sClassLoader;
    private static volatile String sModulePath = "";
    private static volatile int sExternalDisplayId = INVALID_DISPLAY;
    private static volatile int sAppliedPointerDisplayId = Display.INVALID_DISPLAY;
    private static volatile String sLastErrorCode = "";
    private static volatile String sLastErrorMessage = "";
    private static volatile ILineDexAppEndpoint sAppEndpoint;

    private static final Runnable AUTO_MODE_APPLY = () -> {
        if (!DESKTOP_ENABLED.get()) {
            return;
        }
        refreshExternalDisplay();
        final Context context = sContext;
        if (context == null || sExternalDisplayId < 0) {
            return;
        }
        final DisplayManager manager =
                context.getSystemService(DisplayManager.class);
        final Display display = manager == null
                ? null : manager.getDisplay(sExternalDisplayId);
        if (display == null) {
            return;
        }
        final Display.Mode best = Arrays.stream(display.getSupportedModes())
                .max(Comparator
                        .comparingInt(Display.Mode::getPhysicalWidth)
                        .thenComparingInt(mode -> -mode.getPhysicalHeight())
                        .thenComparingDouble(Display.Mode::getRefreshRate))
                .orElse(display.getMode());
        final Display.Mode current = display.getMode();
        if (current.getPhysicalWidth() == best.getPhysicalWidth()
                && current.getPhysicalHeight() == best.getPhysicalHeight()
                && Math.abs(current.getRefreshRate()
                        - best.getRefreshRate()) < 0.5f) {
            return;
        }
        applyPreferredMode(
                display.getDisplayId(),
                best.getPhysicalWidth(),
                best.getPhysicalHeight(),
                best.getRefreshRate());
    };

    private static final ILineDexSystemBridge.Stub BRIDGE =
            new ILineDexSystemBridge.Stub() {
                @Override
                public int getBridgeVersion() {
                    enforceCaller();
                    return INTERFACE_VERSION;
                }

                @Override
                public String getFrameworkFingerprint() {
                    enforceCaller();
                    return android.os.Build.FINGERPRINT;
                }

                @Override
                public boolean isHookActive() {
                    enforceCaller();
                    return INITIALIZED.get();
                }

                @Override
                public void setDesktopSessionEnabled(final boolean enabled) {
                    enforceCaller();
                    setDesktopEnabled(enabled);
                }

                @Override
                public boolean isDesktopSessionEnabled() {
                    enforceCaller();
                    return DESKTOP_ENABLED.get();
                }

                @Override
                public int getExternalDisplayId() {
                    enforceCaller();
                    refreshExternalDisplay();
                    return sExternalDisplayId;
                }

                @Override
                public void setPointerDisplayId(final int displayId) {
                    enforceCaller();
                    POINTER_TARGET.set(displayId);
                    applyPointerTarget();
                    notifyStateChanged();
                }

                @Override
                public int getPointerDisplayId() {
                    enforceCaller();
                    return pointerDisplayId();
                }

                @Override
                public boolean applyPreferredDisplayMode(
                        final int displayId,
                        final int width,
                        final int height,
                        final float refreshRate) {
                    enforceCaller();
                    return applyPreferredMode(
                            displayId, width, height, refreshRate);
                }

                @Override
                public boolean setPhoneDisplayEnabled(final boolean enabled) {
                    enforceCaller();
                    return runDisplayPowerCommand(enabled);
                }

                @Override
                public Bundle getState() {
                    enforceCaller();
                    refreshExternalDisplay();
                    final Bundle state = new Bundle();
                    state.putBoolean("hookActive", INITIALIZED.get());
                    state.putBoolean("desktopEnabled", DESKTOP_ENABLED.get());
                    state.putInt("externalDisplayId", sExternalDisplayId);
                    state.putInt("pointerDisplayId", pointerDisplayId());
                    state.putInt("appliedPointerDisplayId", sAppliedPointerDisplayId);
                    state.putString("lastErrorCode", sLastErrorCode);
                    state.putString("lastErrorMessage", sLastErrorMessage);
                    state.putString("modulePath", sModulePath);
                    return state;
                }

                @Override
                public void registerCallback(final ILineDexCallback callback) {
                    enforceCaller();
                    if (callback != null) {
                        CALLBACKS.register(callback);
                    }
                }

                @Override
                public void unregisterCallback(final ILineDexCallback callback) {
                    enforceCaller();
                    if (callback != null) {
                        CALLBACKS.unregister(callback);
                    }
                }
            };

    private HookRuntime() {
    }

    static void initialize(
            final Context context,
            final ClassLoader classLoader,
            final String modulePath) {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        sContext = context;
        sClassLoader = classLoader;
        sModulePath = modulePath == null ? "" : modulePath;
        refreshExternalDisplay();
        final DisplayManager manager = context.getSystemService(DisplayManager.class);
        if (manager != null) {
            manager.registerDisplayListener(
                    new DisplayManager.DisplayListener() {
                        @Override
                        public void onDisplayAdded(final int displayId) {
                            onDisplaysChanged();
                        }

                        @Override
                        public void onDisplayRemoved(final int displayId) {
                            onDisplaysChanged();
                        }

                        @Override
                        public void onDisplayChanged(final int displayId) {
                            onDisplaysChanged();
                        }
                    }, MAIN);
        }
        final InputManager inputManager = context.getSystemService(InputManager.class);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(
                    new InputManager.InputDeviceListener() {
                        @Override
                        public void onInputDeviceAdded(final int deviceId) {
                            applyPointerTarget();
                        }

                        @Override
                        public void onInputDeviceRemoved(final int deviceId) {
                            applyPointerTarget();
                        }

                        @Override
                        public void onInputDeviceChanged(final int deviceId) {
                            applyPointerTarget();
                        }
                    }, MAIN);
        }
        XposedBridge.log(TAG + ": initialized on " + android.os.Build.FINGERPRINT);
    }

    static ILineDexSystemBridge bridge() {
        return BRIDGE;
    }

    static boolean isDesktopSessionEnabled() {
        return DESKTOP_ENABLED.get();
    }

    static int pointerDisplayId() {
        if (!DESKTOP_ENABLED.get()) {
            return INVALID_DISPLAY;
        }
        final int requested = POINTER_TARGET.get();
        if (requested == LineDexPreferences.POINTER_AUTO) {
            refreshExternalDisplay();
            return sExternalDisplayId;
        }
        return requested;
    }

    static void onApplicationBridgeConnected(
            final ILineDexAppEndpoint endpoint) {
        sAppEndpoint = endpoint;
    }

    static void onApplicationBridgeDisconnected() {
        sAppEndpoint = null;
        if (DESKTOP_ENABLED.get()) {
            // Fail open: disable the session and restore phone input/display.
            setDesktopEnabled(false);
        }
    }

    static void recordError(
            final String code,
            final String message,
            final Throwable error) {
        sLastErrorCode = code == null ? "" : code;
        sLastErrorMessage = message == null ? "" : message;
        XposedBridge.log(TAG + ": " + sLastErrorCode + " " + sLastErrorMessage);
        if (error != null) {
            XposedBridge.log(error);
        }
        final int count = CALLBACKS.beginBroadcast();
        try {
            for (int index = 0; index < count; index++) {
                try {
                    CALLBACKS.getBroadcastItem(index).onError(
                            sLastErrorCode, sLastErrorMessage);
                } catch (RemoteException ignored) {
                    // RemoteCallbackList removes dead callbacks.
                }
            }
        } finally {
            CALLBACKS.finishBroadcast();
        }
    }

    private static void setDesktopEnabled(final boolean enabled) {
        DESKTOP_ENABLED.set(enabled);
        refreshExternalDisplay();
        applyPointerTarget();
        MAIN.removeCallbacks(AUTO_MODE_APPLY);
        if (enabled) {
            MAIN.postDelayed(AUTO_MODE_APPLY, 1_200L);
        }
        if (!enabled) {
            runDisplayPowerCommand(true);
        }
        notifyStateChanged();
    }

    private static void onDisplaysChanged() {
        final int previous = sExternalDisplayId;
        refreshExternalDisplay();
        if (previous != sExternalDisplayId) {
            applyPointerTarget();
            MAIN.removeCallbacks(AUTO_MODE_APPLY);
            if (DESKTOP_ENABLED.get() && sExternalDisplayId >= 0) {
                MAIN.postDelayed(AUTO_MODE_APPLY, 1_200L);
            }
            final int count = CALLBACKS.beginBroadcast();
            try {
                for (int index = 0; index < count; index++) {
                    try {
                        CALLBACKS.getBroadcastItem(index)
                                .onExternalDisplayChanged(sExternalDisplayId);
                    } catch (RemoteException ignored) {
                        // RemoteCallbackList removes dead callbacks.
                    }
                }
            } finally {
                CALLBACKS.finishBroadcast();
            }
        }
    }

    private static void refreshExternalDisplay() {
        final Context context = sContext;
        if (context == null) {
            sExternalDisplayId = INVALID_DISPLAY;
            return;
        }
        final DisplayManager manager = context.getSystemService(DisplayManager.class);
        if (manager == null) {
            sExternalDisplayId = INVALID_DISPLAY;
            return;
        }
        sExternalDisplayId = Arrays.stream(manager.getDisplays())
                .filter(display -> display.getDisplayId() != Display.DEFAULT_DISPLAY)
                .filter(display -> ExternalDisplayCompat.isExternal(manager, display))
                .filter(display -> display.getState() != Display.STATE_OFF)
                .map(Display::getDisplayId)
                .min(Comparator.naturalOrder())
                .orElse(INVALID_DISPLAY);
    }

    private static void applyPointerTarget() {
        final int target = pointerDisplayId();
        final int overrideDisplayId = target >= 0
                ? target : Display.INVALID_DISPLAY;
        POINTER_EXECUTOR.execute(() -> forcePointerDisplay(overrideDisplayId));
    }

    @SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    private static void forcePointerDisplay(final int displayId) {
        if (displayId >= 0 && !hasPhysicalMouse()) {
            XposedBridge.log(TAG + ": pointer route deferred; no physical mouse");
            return;
        }
        try {
            final Class<?> localServices = Class.forName(
                    "com.android.server.LocalServices", false, sClassLoader);
            final Class<?> inputManagerInternal = Class.forName(
                    "com.android.server.input.InputManagerInternal",
                    false,
                    sClassLoader);
            final Method getService = localServices.getDeclaredMethod(
                    "getService", Class.class);
            getService.setAccessible(true);
            final Object localService = getService.invoke(null, inputManagerInternal);
            if (localService == null) {
                throw new IllegalStateException("InputManagerInternal unavailable");
            }

            boolean virtualApplied = false;
            try {
                final Method setter = inputManagerInternal.getDeclaredMethod(
                        "setVirtualMousePointerDisplayId", Integer.TYPE);
                setter.setAccessible(true);
                final Object result = setter.invoke(
                        localService, Integer.valueOf(displayId));
                virtualApplied = !(result instanceof Boolean)
                        || ((Boolean) result).booleanValue();
                if (virtualApplied) {
                    XposedBridge.log(TAG
                            + ": virtual pointer override set to " + displayId);
                }
            } catch (NoSuchMethodException unavailable) {
                XposedBridge.log(TAG
                        + ": virtual pointer override is not present on this ROM");
            }

            boolean nativeApplied = false;
            try {
                final Object inputManagerService = findOuterInputManagerService(
                        localService);
                final Object nativeService = findFieldValue(
                        inputManagerService, "mNative");
                final Method nativeSetter = findIntMethod(
                        nativeService.getClass(), "setPointerDisplayId");
                nativeSetter.setAccessible(true);
                final int nativeTarget = displayId >= 0
                        ? displayId : Display.DEFAULT_DISPLAY;
                nativeSetter.invoke(nativeService, Integer.valueOf(nativeTarget));
                nativeApplied = true;
                XposedBridge.log(TAG
                        + ": physical pointer routed through native input to "
                        + nativeTarget);
            } catch (ReflectiveOperationException nativeFailure) {
                if (!virtualApplied) {
                    throw nativeFailure;
                }
                XposedBridge.log(TAG
                        + ": native pointer route unavailable after virtual override: "
                        + nativeFailure);
            }

            if (!virtualApplied && !nativeApplied) {
                throw new IllegalStateException(
                        "InputManager rejected pointer display " + displayId);
            }
            sAppliedPointerDisplayId = displayId;
            notifyPointerChanged(displayId);
        } catch (Throwable error) {
            recordError(
                    "POINTER-APPLY-001",
                    "Could not switch the physical mouse display",
                    error);
        }
    }

    @SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    private static Object findOuterInputManagerService(final Object localService)
            throws ReflectiveOperationException {
        Class<?> type = localService.getClass();
        while (type != null) {
            for (final java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (!field.getType().getName().equals(
                        "com.android.server.input.InputManagerService")) {
                    continue;
                }
                field.setAccessible(true);
                final Object value = field.get(localService);
                if (value != null) {
                    return value;
                }
            }
            type = type.getSuperclass();
        }
        throw new NoSuchFieldException("InputManagerService outer instance");
    }

    @SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    private static Object findFieldValue(
            final Object owner, final String name)
            throws ReflectiveOperationException {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                final java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                final Object value = field.get(owner);
                if (value == null) {
                    throw new IllegalStateException(name + " is null");
                }
                return value;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    private static Method findIntMethod(
            final Class<?> type, final String name)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, Integer.TYPE);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name + "(int)");
    }

    private static boolean hasPhysicalMouse() {
        final Context context = sContext;
        if (context == null) {
            return false;
        }
        final InputManager manager = context.getSystemService(InputManager.class);
        if (manager == null) {
            return false;
        }
        for (final int deviceId : manager.getInputDeviceIds()) {
            final InputDevice device = manager.getInputDevice(deviceId);
            if (device != null
                    && !device.isVirtual()
                    && device.supportsSource(InputDevice.SOURCE_MOUSE)) {
                return true;
            }
        }
        return false;
    }

    @SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    private static boolean applyPreferredMode(
            final int displayId,
            final int width,
            final int height,
            final float refreshRate) {
        final Context context = sContext;
        if (context == null) {
            return false;
        }
        final DisplayManager manager = context.getSystemService(DisplayManager.class);
        final Display display = manager == null ? null : manager.getDisplay(displayId);
        if (!ExternalDisplayCompat.isExternal(manager, display)) {
            return false;
        }
        Display.Mode match = null;
        for (final Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() != width
                    || mode.getPhysicalHeight() != height) {
                continue;
            }
            if (refreshRate > 0f
                    && Math.abs(mode.getRefreshRate() - refreshRate) > 1f) {
                continue;
            }
            match = mode;
            break;
        }
        if (match == null) {
            return false;
        }
        try {
            final Method method = Display.class.getDeclaredMethod(
                    "setUserPreferredDisplayMode", Display.Mode.class);
            method.setAccessible(true);
            method.invoke(display, match);
            return true;
        } catch (Throwable error) {
            recordError(
                    "DISPLAY-MODE-001",
                    "Could not apply the external display mode",
                    error);
            return false;
        }
    }

    private static boolean runDisplayPowerCommand(final boolean enabled) {
        final String operation = enabled ? "power-reset" : "power-off";
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder(
                    "/system/bin/cmd", "display", operation, "0")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                recordError(
                        "DISPLAY-POWER-001",
                        "Display power command timed out",
                        null);
                return false;
            }
            final StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null
                        && output.length() < 4096) {
                    output.append(line).append('\n');
                }
            }
            if (process.exitValue() == 0) {
                return true;
            }
            recordError(
                    "DISPLAY-POWER-002",
                    operation + " failed: " + output.toString().trim(),
                    null);
        } catch (Throwable error) {
            recordError(
                    "DISPLAY-POWER-003",
                    operation + " failed",
                    error);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return false;
    }

    private static void enforceCaller() {
        final Context context = sContext;
        final int callingUid = Binder.getCallingUid();
        if (callingUid == Process.SYSTEM_UID || callingUid == Process.myUid()) {
            return;
        }
        if (context == null) {
            throw new SecurityException("LineDEX is not initialized");
        }
        final PackageManager packageManager = context.getPackageManager();
        final String[] packages = packageManager.getPackagesForUid(callingUid);
        if (packages != null) {
            for (final String packageName : packages) {
                if (PACKAGE_NAME.equals(packageName)) {
                    return;
                }
            }
        }
        throw new SecurityException(
                "Rejected LineDEX bridge caller uid=" + callingUid);
    }

    private static void notifyStateChanged() {
        final int count = CALLBACKS.beginBroadcast();
        try {
            for (int index = 0; index < count; index++) {
                try {
                    CALLBACKS.getBroadcastItem(index).onStateChanged();
                } catch (RemoteException ignored) {
                    // RemoteCallbackList removes dead callbacks.
                }
            }
        } finally {
            CALLBACKS.finishBroadcast();
        }
    }

    private static void notifyPointerChanged(final int displayId) {
        final int count = CALLBACKS.beginBroadcast();
        try {
            for (int index = 0; index < count; index++) {
                try {
                    CALLBACKS.getBroadcastItem(index)
                            .onPointerDisplayChanged(displayId);
                } catch (RemoteException ignored) {
                    // RemoteCallbackList removes dead callbacks.
                }
            }
        } finally {
            CALLBACKS.finishBroadcast();
        }
    }
}
