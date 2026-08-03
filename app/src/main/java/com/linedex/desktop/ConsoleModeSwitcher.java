package com.linedex.desktop;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

import com.linedex.desktop.bridge.ILineDexSystemBridge;
import com.linedex.desktop.bridge.LineDexBridgeClient;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

final class ConsoleModeSwitcher {
    private static final String TAG = "MagicDeskConsoleSwitcher";
    private static final String CONSOLE_TASK_RETURN_COMMAND =
            "com.linedex.desktop.ConsoleTaskReturnCommand";
    private static final String DEVICE_LOCK_COMMAND =
            "com.linedex.desktop.DeviceLockCommand";
    private static final String SCREENSHOT_DIRECTORY =
            "/storage/emulated/0/Pictures/Screenshots";
    private static final AtomicBoolean DESKTOP_START_IN_PROGRESS = new AtomicBoolean();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(final Runnable runnable) {
                    final Thread thread = new Thread(runnable, "MagicDeskConsoleSwitcher");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private ConsoleModeSwitcher() {
    }

    interface ResultCallback {
        void onComplete(boolean success);
    }

    interface TouchpadRestoreCallback {
        void onComplete(boolean touchpadMissing, boolean restored);
    }

    static void setPhoneScreenOff(final boolean screenOff,
            final ResultCallback callback) {
        EXECUTOR.execute(() -> {
            boolean success = false;
            final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
            if (bridge != null) {
                try {
                    success = bridge.setPhoneDisplayEnabled(!screenOff);
                } catch (RemoteException error) {
                    Log.w(TAG, "Phone display request failed", error);
                }
            }
            if (callback != null) {
                callback.onComplete(success);
            }
        });
    }

    static void showMagicDesk() {
        showMagicDesk(-1);
    }

    static void showMagicDesk(final int knownConsoleDisplayId) {
        if (!DESKTOP_START_IN_PROGRESS.compareAndSet(false, true)) {
            return;
        }
        final Context context = MagicDeskApplication.applicationContext();
        final int displayId = knownConsoleDisplayId > 0
                ? knownConsoleDisplayId : getActiveConsoleDisplayId();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                if (context != null && displayId > 0) {
                    DesktopActivity.launchOnDisplay(context, displayId);
                }
            } finally {
                DESKTOP_START_IN_PROGRESS.set(false);
            }
        });
    }

    static void openTouchpad() {
        final Context context = MagicDeskApplication.applicationContext();
        if (context != null) {
            context.startActivity(new Intent(context, LineDexControlActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    static boolean isTouchpadVisible() {
        return false;
    }

    static void restoreTouchpadIfMissing() {
        restoreTouchpadIfMissing(null);
    }

    static void restoreTouchpadIfMissing(
            final TouchpadRestoreCallback callback) {
        if (callback != null) {
            callback.onComplete(false, true);
        }
    }

    static void restorePrimaryPhoneHome() {
        // The phone keeps its own HOME task in AOSP extended-display mode.
    }

    static void setExternalTaskCaptionsEnabled(final boolean enabled) {
        // Native WMShell captions are enabled by the SystemUI hook.
    }

    static void switchToMirror(final ResultCallback callback) {
        EXECUTOR.execute(() -> {
            boolean success = false;
            final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
            if (bridge != null) {
                try {
                    bridge.setDesktopSessionEnabled(false);
                    success = true;
                } catch (RemoteException error) {
                    Log.w(TAG, "Could not stop LineDEX session", error);
                }
            }
            if (callback != null) {
                callback.onComplete(success);
            }
        });
    }

    static void returnConsoleTasksToPhone(final ResultCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    final int displayId = getActiveConsoleDisplayId();
                    if (displayId <= 0) {
                        success = true;
                        return;
                    }
                    final String output = ShellAccess.run(
                            AppProcessCommand.run(
                                    CONSOLE_TASK_RETURN_COMMAND,
                                    Integer.toString(displayId))).trim();
                    success = output.contains("tasks-returned=");
                    if (!success) {
                        Log.w(TAG, "Console task return failed output=" + output);
                    }
                } catch (IOException error) {
                    Log.w(TAG, "Console task return failed", error);
                } finally {
                    if (callback != null) {
                        callback.onComplete(success);
                    }
                }
            }
        });
    }

    static void showMagicDeskStart() {
        Log.i(TAG, "show MagicDesk Start overlay");
        if (!DesktopRuntimeBridge.showStart()
                && !MagicDeskRuntimeService.showStartIfRunning()) {
            Log.w(TAG, "MagicDesk desktop is unavailable for Start");
        }
    }

    static void advanceAltTab(final boolean reverse) {
        if (!DesktopRuntimeBridge.advanceAltTab(reverse)) {
            Log.w(TAG, "MagicDesk desktop is unavailable for Alt+Tab");
        }
    }

    static void finishAltTab() {
        if (!DesktopRuntimeBridge.finishAltTab()) {
            Log.w(TAG, "MagicDesk desktop is unavailable for Alt+Tab completion");
        }
    }

    static void cancelAltTab() {
        DesktopRuntimeBridge.cancelAltTab();
    }

    static void sendSystemBack() {
        if (!DesktopTaskController.sendSystemBack()) {
            Log.w(TAG, "system Back shortcut unavailable");
        }
    }

    static void lockDevice() {
        if (!ShellAccess.isReady()) {
            Log.w(TAG, "device lock unavailable; shizuku="
                    + ShellAccess.statusLabel());
            return;
        }
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final String output = runConsoleCommand(
                        AppProcessCommand.run(
                                DEVICE_LOCK_COMMAND)).trim();
                if (!output.contains("device-locked")) {
                    Log.w(TAG, "device lock shortcut failed output="
                            + output.replace('\n', ' '));
                }
            }
        });
    }

    static void manageActiveWindow(final int shortcut) {
        if (!DesktopTaskController.handleActiveTaskShortcut(shortcut)) {
            Log.w(TAG, "window shortcut unavailable action=" + shortcut);
        }
    }

    static void showShortcutHelp() {
        if (!DesktopRuntimeBridge.toggleShortcutHelp()) {
            Log.w(TAG, "MagicDesk desktop is unavailable for shortcut help");
        }
    }

    static void toggleNotificationCenter() {
        if (!DesktopRuntimeBridge.toggleNotificationCenter()) {
            Log.w(TAG, "MagicDesk desktop is unavailable for notifications");
        }
    }

    static void captureScreenshot() {
        if (!ShellAccess.isReady()) {
            Log.w(TAG, "screenshot unavailable; shizuku="
                    + ShellAccess.statusLabel());
            return;
        }
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                captureScreenshotInternal();
            }
        });
    }

    static void toggleHardwareKeyboardLayout() {
        HardwareKeyboardLayoutController.toggle();
    }

    static void refreshHardwareKeyboardLayout() {
        HardwareKeyboardLayoutController.refresh();
    }

    static void executeSerialized(final Runnable action) {
        EXECUTOR.execute(action);
    }

    private static String shellQuote(final String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static int getActiveConsoleDisplayId() {
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge != null) {
            try {
                return bridge.getExternalDisplayId();
            } catch (RemoteException error) {
                Log.w(TAG, "Could not query external display", error);
            }
        }
        final Context context = MagicDeskApplication.applicationContext();
        final android.hardware.display.DisplayManager manager = context == null
                ? null : context.getSystemService(
                        android.hardware.display.DisplayManager.class);
        if (manager != null) {
            return ExternalDisplayCompat.findActiveExternalDisplayId(manager);
        }
        return -1;
    }

    private static void captureScreenshotInternal() {
        String path = null;
        try {
            final String physicalDisplayId =
                    ConsoleDisplayController.getExternalPhysicalDisplayId();
            final String fileName = "MagicDesk_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                            .format(new Date())
                    + ".png";
            path = SCREENSHOT_DIRECTORY + "/" + fileName;
            final String displayArgument = physicalDisplayId == null
                    ? "" : "-d " + physicalDisplayId + " ";
            final String command = "umask 002; "
                    + "/system/bin/mkdir -p " + shellQuote(SCREENSHOT_DIRECTORY)
                    + " && /system/bin/screencap " + displayArgument
                    + "-p " + shellQuote(path)
                    + " && /system/bin/test -s " + shellQuote(path)
                    + " && /system/bin/chmod 0664 " + shellQuote(path)
                    + " && /system/bin/am broadcast --user 0"
                    + " -a android.intent.action.MEDIA_SCANNER_SCAN_FILE"
                    + " -d " + shellQuote("file://" + path)
                    + " >/dev/null"
                    + " && echo " + shellQuote("screenshot-saved=" + path);
            final String output = ShellAccess.run(command).trim();
            if (!output.contains("screenshot-saved=" + path)) {
                throw new IOException(
                        "unexpected screenshot response: "
                                + output.replace('\n', ' '));
            }
            Log.i(TAG, "screenshot saved path=" + path
                    + " physicalDisplay=" + physicalDisplayId);
        } catch (IOException error) {
            Log.w(TAG, "screenshot failed path=" + path, error);
            CompatibilityDiagnostics.record(
                    "SCREENSHOT-001",
                    "Could not capture the external display",
                    error.getMessage());
        }
    }

    static String runConsoleCommand(final String command) {
        if (!ShellAccess.isReady()) {
            return "";
        }
        try {
            return ShellAccess.run(command);
        } catch (IOException error) {
            Log.w(TAG, "Console command failed: " + command, error);
            return "";
        }
    }
}
