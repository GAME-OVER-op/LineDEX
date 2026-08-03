package com.linedex.desktop;

import android.os.RemoteException;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.linedex.desktop.bridge.ILineDexSystemBridge;
import com.linedex.desktop.bridge.LineDexBridgeClient;

/** Lineage/AOSP tools shown inside the desktop Start menu. */
final class ConsoleControlsController {
    private final DesktopShellActivity mActivity;
    private final DesktopUiFactory mUi;
    private TextView mStatus;
    private String mActivityStatus = "";
    private boolean mPhoneDisplayEnabled = true;

    ConsoleControlsController(
            final DesktopShellActivity activity,
            final DesktopUiFactory ui) {
        mActivity = activity;
        mUi = ui;
    }

    void start() {
        update();
    }

    void stop() {
        // No vendor listeners are installed by the AOSP backend.
    }

    void setActivityStatus(final String text) {
        mActivityStatus = text == null ? "" : text;
        update();
    }

    void setHardwarePanelVisible(final boolean visible) {
        // REDMAGIC hardware controls are intentionally not used on LineageOS.
    }

    void populateTools(final LinearLayout parent, final int spacing) {
        mStatus = new TextView(mActivity);
        mStatus.setTextColor(DesktopUiFactory.COLOR_TEXT);
        mStatus.setTextSize(14);
        parent.addView(mStatus, fullWidth(spacing));

        final Button phoneScreen = mUi.actionButton(
                R.string.linedex_phone_screen_off,
                DesktopUiFactory.COLOR_CYAN);
        phoneScreen.setOnClickListener(view -> togglePhoneScreen());
        parent.addView(phoneScreen, fullWidth(spacing));

        final Button controlPanel = mUi.actionButton(
                R.string.action_open_control_panel,
                DesktopUiFactory.COLOR_CYAN);
        controlPanel.setOnClickListener(view -> mActivity.openControlPanel());
        parent.addView(controlPanel, fullWidth(spacing));

        final Button diagnostics = mUi.actionButton(
                R.string.action_diagnostics,
                DesktopUiFactory.COLOR_CYAN);
        diagnostics.setOnClickListener(view -> mActivity.openDiagnostics());
        parent.addView(diagnostics, fullWidth(spacing));

        final Button exit = mUi.actionButton(
                R.string.action_exit,
                DesktopUiFactory.COLOR_RED);
        exit.setOnClickListener(view -> mActivity.exitMagicDesk());
        parent.addView(exit, fullWidth(spacing));
        update();
    }

    void populateHardware(final LinearLayout parent, final int spacing) {
        final TextView title = mUi.sectionTitle(
                R.string.linedex_hardware_title);
        parent.addView(title, fullWidth(0));
        final TextView message = new TextView(mActivity);
        message.setText(R.string.linedex_hardware_note);
        message.setTextColor(DesktopUiFactory.COLOR_MUTED);
        message.setTextSize(14);
        parent.addView(message, fullWidth(spacing));
    }

    void update() {
        if (mStatus == null) {
            return;
        }
        final boolean connected = LineDexBridgeClient.isConnected();
        final String bridge = connected
                ? mActivity.getString(R.string.linedex_module_active,
                        LineDexBridgeClient.interfaceVersion())
                : mActivity.getString(R.string.linedex_module_inactive);
        mStatus.setText(mActivityStatus.isEmpty()
                ? bridge : bridge + "\n" + mActivityStatus);
    }

    void togglePhoneScreen() {
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge == null) {
            mActivity.setStatus(
                    mActivity.getString(R.string.linedex_module_inactive));
            return;
        }
        final boolean target = !mPhoneDisplayEnabled;
        try {
            if (bridge.setPhoneDisplayEnabled(target)) {
                mPhoneDisplayEnabled = target;
                mActivity.setStatus(mActivity.getString(target
                        ? R.string.status_phone_screen_on
                        : R.string.status_phone_screen_off));
            } else {
                mActivity.setStatus(mActivity.getString(
                        R.string.linedex_phone_screen_failed));
            }
        } catch (RemoteException error) {
            mActivity.setStatus(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        }
        update();
    }

    static int snapDpi(final int dpi, final int maximum) {
        return DisplayDensityPolicy.snapDpi(dpi, maximum);
    }

    private LinearLayout.LayoutParams fullWidth(final int topMargin) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }
}
