package com.linedex.desktop;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.RemoteException;
import android.view.Display;

import com.linedex.desktop.bridge.ILineDexSystemBridge;
import com.linedex.desktop.bridge.LineDexBridgeClient;

/** Coordinates a safe, AOSP-only LineDEX session shutdown. */
final class MagicDeskSessionController {
    private final MagicDeskSessionHost mHost;
    private final Activity mActivity;
    private boolean mExitInProgress;

    MagicDeskSessionController(final MagicDeskSessionHost host) {
        mHost = host;
        mActivity = host.sessionActivity();
    }

    void exit() {
        if (mExitInProgress) {
            return;
        }
        mExitInProgress = true;
        mHost.showSessionStatus(mActivity.getString(R.string.status_exiting));
        LineDexPreferences.setDesktopEnabled(mActivity, false);
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge != null) {
            try {
                bridge.setPhoneDisplayEnabled(true);
                bridge.setPointerDisplayId(Display.DEFAULT_DISPLAY);
                bridge.setDesktopSessionEnabled(false);
            } catch (RemoteException error) {
                mHost.showSessionError(
                        "EXIT-BRIDGE-001",
                        error.getMessage() == null
                                ? error.getClass().getSimpleName()
                                : error.getMessage(),
                        error);
            }
        }
        KeyboardShortcutWatcher.stop();
        MagicDeskRuntimeService.stop(mActivity);
        mHost.releaseSessionUi();

        final Display display = mActivity.getDisplay();
        if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
            final ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
            final Intent control = new Intent(
                    mActivity, LineDexControlActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                mActivity.startActivity(control, options.toBundle());
            } catch (RuntimeException ignored) {
                // The phone launcher remains reachable if this ROM rejects the
                // cross-display Activity launch during teardown.
            }
        }
        mActivity.finishAndRemoveTask();
    }
}
