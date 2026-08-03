package com.linedex.desktop;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import com.linedex.desktop.bridge.ILineDexSystemBridge;
import com.linedex.desktop.bridge.LineDexBridgeClient;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** AOSP external-display facade retained for the inherited desktop runtime. */
final class ConsoleDisplayController {
    static final long START_TIMEOUT_MS = 10_000L;
    static final long STATE_POLL_MS = 100L;

    private static final String TAG = "LineDexDisplay";
    private static final String DISPLAY = "/system/bin/cmd display";
    private static final String WM = "/system/bin/wm";
    private static final long DENSITY_APPLY_TIMEOUT_MS = 2_000L;
    private static final Pattern EXTERNAL_PHYSICAL_DISPLAY_PATTERN =
            Pattern.compile("type EXTERNAL,.*?uniqueId \\\"local:([0-9]+)\\\"");
    private static final Pattern WM_SIZE_PATTERN =
            Pattern.compile("(?:Physical|Override) size: (\\d+)x(\\d+)");
    private static final Pattern WM_DENSITY_PATTERN =
            Pattern.compile("Override density: (\\d+)");

    private ConsoleDisplayController() {
    }

    static int getActiveConsoleDisplayId() {
        return findExternalDisplayId();
    }

    static int findExternalDisplayId() {
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge != null) {
            try {
                final int displayId = bridge.getExternalDisplayId();
                if (displayId > Display.DEFAULT_DISPLAY) {
                    return displayId;
                }
            } catch (android.os.RemoteException ignored) {
                // Fall through to the application DisplayManager.
            }
        }
        final Context context = MagicDeskApplication.applicationContext();
        final DisplayManager manager = context == null
                ? null : context.getSystemService(DisplayManager.class);
        if (manager == null) {
            return -1;
        }
        return ExternalDisplayCompat.findActiveExternalDisplayId(manager);
    }

    static boolean requestConsoleMode(final int externalDisplayId) {
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge == null) {
            return false;
        }
        try {
            bridge.setDesktopSessionEnabled(true);
            return true;
        } catch (android.os.RemoteException error) {
            Log.w(TAG, "Could not enable the LineDEX session", error);
            return false;
        }
    }

    static boolean requestMirrorMode() {
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge == null) {
            return false;
        }
        try {
            bridge.setDesktopSessionEnabled(false);
            return true;
        } catch (android.os.RemoteException error) {
            Log.w(TAG, "Could not disable the LineDEX session", error);
            return false;
        }
    }

    static boolean isMirrorMode() {
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge == null) {
            return true;
        }
        try {
            return !bridge.isDesktopSessionEnabled();
        } catch (android.os.RemoteException ignored) {
            return true;
        }
    }

    static int waitForConsoleDisplay() {
        final long deadline = SystemClock.uptimeMillis() + START_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final int displayId = findExternalDisplayId();
            if (displayId > Display.DEFAULT_DISPLAY) {
                return displayId;
            }
            SystemClock.sleep(STATE_POLL_MS);
        }
        return -1;
    }

    static boolean waitForConsoleStop() {
        final long deadline = SystemClock.uptimeMillis() + START_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (findExternalDisplayId() <= Display.DEFAULT_DISPLAY
                    || isMirrorMode()) {
                return true;
            }
            SystemClock.sleep(STATE_POLL_MS);
        }
        return false;
    }

    static boolean displayExists(final int displayId) {
        final Context context = MagicDeskApplication.applicationContext();
        final DisplayManager manager = context == null
                ? null : context.getSystemService(DisplayManager.class);
        return manager != null && manager.getDisplay(displayId) != null;
    }

    static void applyStartupDensity(final int displayId, final int dpi) {
        final String command = dpi == DesktopPreferences.SYSTEM_DESKTOP_DPI
                ? WM + " density reset -d " + displayId
                : WM + " density " + dpi + " -d " + displayId;
        final String output = runCommand(command).trim();
        Log.i(TAG, "Applied external display density display=" + displayId
                + " dpi=" + dpi + " output=" + output.replace('\n', ' '));
        final long deadline =
                SystemClock.uptimeMillis() + DENSITY_APPLY_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final String state = runCommand(WM + " density -d " + displayId);
            final Matcher matcher = WM_DENSITY_PATTERN.matcher(state);
            final boolean hasOverride = matcher.find();
            if ((dpi == DesktopPreferences.SYSTEM_DESKTOP_DPI && !hasOverride)
                    || (dpi > 0 && hasOverride
                    && Integer.toString(dpi).equals(matcher.group(1)))) {
                return;
            }
            SystemClock.sleep(STATE_POLL_MS);
        }
        Log.w(TAG, "External density did not settle display="
                + displayId + " dpi=" + dpi);
    }

    static void ensureLandscape(final int displayId) throws IOException {
        final String output = ShellAccess.run(WM + " size -d " + displayId);
        final Matcher matcher = WM_SIZE_PATTERN.matcher(output);
        int width = -1;
        int height = -1;
        while (matcher.find()) {
            width = Integer.parseInt(matcher.group(1));
            height = Integer.parseInt(matcher.group(2));
        }
        if (width <= 0 || height <= 0) {
            throw new IOException("could not read external display size: "
                    + output.trim());
        }
        // Do not swap the physical mode. Lock the external display to its
        // natural landscape rotation and let the monitor profile select size.
        ShellAccess.run(WM + " fixed-to-user-rotation -d "
                + displayId + " enabled");
        ShellAccess.run(WM + " user-rotation -d " + displayId + " lock 0");
    }

    static String getExternalPhysicalDisplayId() throws IOException {
        final String output = ShellAccess.run(
                DISPLAY + " get-displays --type external");
        final Matcher matcher = EXTERNAL_PHYSICAL_DISPLAY_PATTERN.matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String runCommand(final String command) {
        if (!ShellAccess.isReady()) {
            return "";
        }
        try {
            return ShellAccess.run(command);
        } catch (IOException error) {
            Log.w(TAG, "display command failed: " + command, error);
            return "";
        }
    }
}
