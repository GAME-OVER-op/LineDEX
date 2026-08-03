package com.linedex.desktop;

import android.app.Activity;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.linedex.desktop.bridge.ILineDexCallback;
import com.linedex.desktop.bridge.ILineDexSystemBridge;
import com.linedex.desktop.bridge.LineDexBridgeClient;

import java.util.Arrays;
import java.util.Comparator;

/** Phone-side control panel for LineDEX. */
public final class LineDexControlActivity extends Activity
        implements LineDexBridgeClient.Listener {
    private TextView mModuleState;
    private TextView mDisplayState;
    private TextView mPointerState;
    private TextView mShellState;
    private TextView mErrorState;
    private Switch mDesktopSwitch;
    private Button mOpenDesktop;
    private Button mNativeMode;
    private Button mPointerTarget;
    private Button mPhoneScreen;
    private boolean mRendering;
    private boolean mPhoneDisplayEnabled = true;

    private final ILineDexCallback mCallback = new ILineDexCallback.Stub() {
        @Override
        public void onStateChanged() {
            runOnUiThread(LineDexControlActivity.this::render);
        }

        @Override
        public void onExternalDisplayChanged(final int displayId) {
            runOnUiThread(LineDexControlActivity.this::render);
        }

        @Override
        public void onPointerDisplayChanged(final int displayId) {
            runOnUiThread(LineDexControlActivity.this::render);
        }

        @Override
        public void onError(final String code, final String message) {
            runOnUiThread(() -> {
                mErrorState.setText(code + ": " + message);
                render();
            });
        }
    };

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(final int displayId) {
                    render();
                }

                @Override
                public void onDisplayRemoved(final int displayId) {
                    render();
                }

                @Override
                public void onDisplayChanged(final int displayId) {
                    render();
                }
            };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DeviceSetupManager.authorizeRuntime(this);
        setTitle(R.string.app_name);
        setContentView(createContent());
        LineDexBridgeClient.addListener(this);
        final DisplayManager manager = getSystemService(DisplayManager.class);
        if (manager != null) {
            manager.registerDisplayListener(mDisplayListener, null);
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        DeviceSetupManager.authorizeRuntime(this);
        ShellAccess.refresh();
        registerCallback();
        render();
    }

    @Override
    protected void onPause() {
        unregisterCallback();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        final DisplayManager manager = getSystemService(DisplayManager.class);
        if (manager != null) {
            manager.unregisterDisplayListener(mDisplayListener);
        }
        LineDexBridgeClient.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onBridgeChanged() {
        runOnUiThread(() -> {
            registerCallback();
            render();
        });
    }

    private View createContent() {
        final ScrollView scroll = new ScrollView(this);
        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        final TextView title = text("LineDEX", 30, true);
        page.addView(title);
        final TextView subtitle = text(
                getString(R.string.linedex_subtitle), 14, false);
        page.addView(subtitle, marginTop(4));

        mModuleState = statusText();
        mDisplayState = statusText();
        mPointerState = statusText();
        mShellState = statusText();
        mErrorState = statusText();
        page.addView(mModuleState, marginTop(22));
        page.addView(mDisplayState, marginTop(6));
        page.addView(mPointerState, marginTop(6));
        page.addView(mShellState, marginTop(6));
        page.addView(mErrorState, marginTop(6));

        mDesktopSwitch = new Switch(this);
        mDesktopSwitch.setText(R.string.linedex_enable_session);
        mDesktopSwitch.setTextSize(17);
        mDesktopSwitch.setOnCheckedChangeListener(
                this::onDesktopSwitchChanged);
        page.addView(mDesktopSwitch, marginTop(24));

        mOpenDesktop = button(R.string.linedex_open_desktop);
        mOpenDesktop.setOnClickListener(view -> openDesktop());
        page.addView(mOpenDesktop, marginTop(14));

        mNativeMode = button(R.string.linedex_apply_native_mode);
        mNativeMode.setOnClickListener(view -> applyNativeMode());
        page.addView(mNativeMode, marginTop(10));

        mPointerTarget = button(R.string.linedex_pointer_auto);
        mPointerTarget.setOnClickListener(view -> cyclePointerTarget());
        page.addView(mPointerTarget, marginTop(10));

        mPhoneScreen = button(R.string.linedex_phone_screen_off);
        mPhoneScreen.setOnClickListener(view -> togglePhoneScreen());
        page.addView(mPhoneScreen, marginTop(10));

        final Button shizuku = button(R.string.linedex_shizuku_action);
        shizuku.setOnClickListener(view -> requestShizuku());
        page.addView(shizuku, marginTop(10));

        final Button prepare = button(R.string.linedex_prepare_android);
        prepare.setOnClickListener(view -> prepareAndroidDesktop());
        page.addView(prepare, marginTop(10));

        final Button diagnostics = button(R.string.action_diagnostics);
        diagnostics.setOnClickListener(view -> startActivity(
                DiagnosticsActivity.createIntent(this)));
        page.addView(diagnostics, marginTop(10));

        final TextView note = text(
                getString(R.string.linedex_setup_note), 13, false);
        page.addView(note, marginTop(22));
        return scroll;
    }

    private void onDesktopSwitchChanged(
            final CompoundButton button,
            final boolean enabled) {
        if (mRendering) {
            return;
        }
        LineDexPreferences.setDesktopEnabled(this, enabled);
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge == null) {
            toast(R.string.linedex_module_inactive);
            render();
            return;
        }
        try {
            bridge.setDesktopSessionEnabled(enabled);
            if (enabled && LineDexPreferences.autoNativeMode(this)) {
                applyNativeMode();
            } else if (!enabled) {
                LineDexSessionCoordinator.stop(this);
            }
        } catch (RemoteException error) {
            toast(error.getMessage());
        }
        render();
    }

    private void openDesktop() {
        final int displayId = externalDisplayId();
        if (displayId < 0) {
            toast(R.string.linedex_no_external_display);
            return;
        }
        DeviceSetupManager.authorizeRuntime(this);
        DesktopActivity.launchOnDisplay(this, displayId);
    }

    private void applyNativeMode() {
        final Display display = externalDisplay();
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (display == null || bridge == null) {
            toast(R.string.linedex_no_external_display);
            return;
        }
        final Display.Mode best = Arrays.stream(display.getSupportedModes())
                .max(Comparator
                        .comparingInt(Display.Mode::getPhysicalWidth)
                        // Prefer a native widescreen timing over synthetic
                        // taller FLAG_SIZE_OVERRIDE modes with the same width.
                        .thenComparingInt(mode -> -mode.getPhysicalHeight())
                        .thenComparingDouble(Display.Mode::getRefreshRate))
                .orElse(display.getMode());
        try {
            final boolean applied = bridge.applyPreferredDisplayMode(
                    display.getDisplayId(),
                    best.getPhysicalWidth(),
                    best.getPhysicalHeight(),
                    best.getRefreshRate());
            toast(applied
                    ? getString(R.string.linedex_mode_applied,
                            best.getPhysicalWidth(),
                            best.getPhysicalHeight(),
                            best.getRefreshRate())
                    : getString(R.string.linedex_mode_failed));
        } catch (RemoteException error) {
            toast(error.getMessage());
        }
    }

    private void cyclePointerTarget() {
        final int current = LineDexPreferences.pointerTarget(this);
        final int external = externalDisplayId();
        final int next;
        if (current == LineDexPreferences.POINTER_AUTO) {
            next = external >= 0 ? external : LineDexPreferences.POINTER_PHONE;
        } else if (current == LineDexPreferences.POINTER_PHONE) {
            next = LineDexPreferences.POINTER_AUTO;
        } else {
            next = LineDexPreferences.POINTER_PHONE;
        }
        LineDexPreferences.setPointerTarget(this, next);
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge != null) {
            try {
                bridge.setPointerDisplayId(next);
            } catch (RemoteException error) {
                toast(error.getMessage());
            }
        }
        render();
    }

    private void requestShizuku() {
        try {
            if (ShellAccess.refresh().isReady()) {
                toast(R.string.linedex_shizuku_ready);
            } else {
                ShellAccess.requestPermission();
            }
        } catch (RuntimeException error) {
            toast(error.getMessage());
        }
        render();
    }

    private void prepareAndroidDesktop() {
        if (!ShellAccess.isReady()) {
            toast(R.string.linedex_shizuku_required);
            return;
        }
        new Thread(() -> {
            try {
                ShellAccess.run(
                        "/system/bin/settings put global enable_freeform_support 1 && "
                                + "/system/bin/settings put global force_resizable_activities 1 && "
                                + "/system/bin/settings put secure mirror_built_in_display 0");
                runOnUiThread(() -> toast(R.string.linedex_android_prepared));
            } catch (java.io.IOException error) {
                runOnUiThread(() -> toast(error.getMessage()));
            }
        }, "LineDexPrepareAndroid").start();
    }

    private void togglePhoneScreen() {
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge == null) {
            toast(R.string.linedex_module_inactive);
            return;
        }
        final boolean targetEnabled = !mPhoneDisplayEnabled;
        try {
            if (bridge.setPhoneDisplayEnabled(targetEnabled)) {
                mPhoneDisplayEnabled = targetEnabled;
            } else {
                toast(R.string.linedex_phone_screen_failed);
            }
        } catch (RemoteException error) {
            toast(error.getMessage());
        }
        render();
    }

    private void render() {
        if (isFinishing() || mModuleState == null) {
            return;
        }
        final boolean connected = LineDexBridgeClient.isConnected();
        final Bundle state = LineDexBridgeClient.state();
        final int external = connected
                ? state.getInt("externalDisplayId", externalDisplayId())
                : externalDisplayId();
        final boolean enabled = connected
                ? state.getBoolean("desktopEnabled",
                        LineDexPreferences.isDesktopEnabled(this))
                : LineDexPreferences.isDesktopEnabled(this);
        final int pointer = connected
                ? state.getInt("pointerDisplayId", -1)
                : -1;

        mRendering = true;
        mDesktopSwitch.setChecked(enabled);
        mRendering = false;

        mModuleState.setText(connected
                ? getString(R.string.linedex_module_active,
                        LineDexBridgeClient.interfaceVersion())
                : getString(R.string.linedex_module_inactive));
        final DisplayManager displayManager =
                getSystemService(DisplayManager.class);
        final Display display = external >= 0 && displayManager != null
                ? displayManager.getDisplay(external)
                : null;
        mDisplayState.setText(display == null
                ? getString(R.string.linedex_display_missing)
                : getString(R.string.linedex_display_connected,
                        external,
                        display.getMode().getPhysicalWidth(),
                        display.getMode().getPhysicalHeight(),
                        display.getMode().getRefreshRate()));
        mPointerState.setText(getString(
                R.string.linedex_pointer_state, pointer));
        mShellState.setText(getString(
                R.string.linedex_shizuku_state, ShellAccess.statusLabel()));
        final String code = state.getString("lastErrorCode", "");
        final String message = state.getString("lastErrorMessage", "");
        mErrorState.setText(code.isEmpty()
                ? getString(R.string.linedex_no_errors)
                : code + ": " + message);

        mOpenDesktop.setEnabled(connected && enabled && external >= 0);
        mNativeMode.setEnabled(connected && external >= 0);
        mPointerTarget.setEnabled(connected);
        mPhoneScreen.setEnabled(connected && enabled && external >= 0);
        mPhoneScreen.setText(mPhoneDisplayEnabled
                ? R.string.linedex_phone_screen_off
                : R.string.linedex_phone_screen_on);
        final int target = LineDexPreferences.pointerTarget(this);
        if (target == LineDexPreferences.POINTER_AUTO) {
            mPointerTarget.setText(R.string.linedex_pointer_auto);
        } else if (target == LineDexPreferences.POINTER_PHONE) {
            mPointerTarget.setText(R.string.linedex_pointer_phone);
        } else {
            mPointerTarget.setText(getString(
                    R.string.linedex_pointer_external, target));
        }
    }

    private void registerCallback() {
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge != null) {
            try {
                bridge.registerCallback(mCallback);
            } catch (RemoteException ignored) {
                // Bridge death is reflected by LineDexBridgeClient.
            }
        }
    }

    private void unregisterCallback() {
        final ILineDexSystemBridge bridge = LineDexBridgeClient.bridge();
        if (bridge != null) {
            try {
                bridge.unregisterCallback(mCallback);
            } catch (RemoteException ignored) {
                // Bridge death is reflected by LineDexBridgeClient.
            }
        }
    }

    private int externalDisplayId() {
        final Display display = externalDisplay();
        return display == null ? -1 : display.getDisplayId();
    }

    private Display externalDisplay() {
        final DisplayManager manager = getSystemService(DisplayManager.class);
        if (manager == null) {
            return null;
        }
        return Arrays.stream(manager.getDisplays())
                .filter(display -> display.getDisplayId() != Display.DEFAULT_DISPLAY)
                .filter(display -> display.getType() == Display.TYPE_EXTERNAL)
                .findFirst()
                .orElse(null);
    }

    private TextView text(
            final String value,
            final int size,
            final boolean bold) {
        final TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    private TextView statusText() {
        final TextView text = text("", 14, false);
        text.setTextIsSelectable(true);
        return text;
    }

    private Button button(final int textResource) {
        final Button button = new Button(this);
        button.setText(textResource);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(52));
        return button;
    }

    private LinearLayout.LayoutParams marginTop(final int dp) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(dp);
        return params;
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(final int stringResource) {
        toast(getString(stringResource));
    }

    private void toast(final String message) {
        Toast.makeText(this,
                message == null ? getString(R.string.linedex_unknown_error) : message,
                Toast.LENGTH_LONG).show();
    }
}
