package com.linedex.desktop;

import android.content.Context;
import android.util.Log;

/** Compatibility facade that launches the AOSP LineDEX desktop. */
final class ConsoleSessionController {
    private static final String TAG = "LineDexSession";

    private ConsoleSessionController() {
    }

    static void show(final int requestedDisplayId) {
        final Context context = MagicDeskApplication.applicationContext();
        if (context == null) {
            return;
        }
        int displayId = requestedDisplayId > 0
                ? requestedDisplayId
                : ConsoleDisplayController.findExternalDisplayId();
        if (displayId <= 0) {
            CompatibilityDiagnostics.record(
                    "LINEDEX-DISPLAY-001",
                    "No active external display is available",
                    "Connect a monitor after enabling the LineDEX session");
            return;
        }
        try {
            ConsoleDisplayController.ensureLandscape(displayId);
        } catch (Exception error) {
            Log.w(TAG, "Could not lock external orientation", error);
        }
        try {
            final Integer dpi = DisplayProfileController.prepareExternalProfile(
                    context, displayId);
            if (dpi != null) {
                ConsoleDisplayController.applyStartupDensity(
                        displayId, dpi.intValue());
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not apply the monitor profile", error);
        }
        MagicDeskRuntimeService.start(context);
        DesktopActivity.launchOnDisplay(context, displayId);
    }

    static boolean setExternalTaskCaptionsEnabled(final boolean enabled) {
        // Native captions remain owned by WMShell. The SystemUI hook only
        // changes desktop eligibility; it does not draw replacement frames.
        return true;
    }
}
