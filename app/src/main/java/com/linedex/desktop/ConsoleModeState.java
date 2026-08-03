package com.linedex.desktop;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import com.linedex.desktop.bridge.LineDexBridgeClient;

/** AOSP/Lineage external-display state used by the inherited desktop runtime. */
final class ConsoleModeState {
    static final String DISPLAY_ID_SETTING = "linedex_session_enabled";
    static final String PHONE_SCREEN_OFF_SETTING = "linedex_phone_screen_off";

    private static volatile boolean sPhoneScreenOff;

    private ConsoleModeState() {
    }

    static int activeDisplayId(final Context context) {
        final android.os.Bundle state = LineDexBridgeClient.state();
        final int bridged = state.getInt("externalDisplayId", -1);
        if (bridged > Display.DEFAULT_DISPLAY) {
            return bridged;
        }
        if (context == null) {
            return -1;
        }
        final DisplayManager manager = context.getSystemService(DisplayManager.class);
        if (manager == null) {
            return -1;
        }
        return ExternalDisplayCompat.findActiveExternalDisplayId(manager);
    }

    static boolean isActive(final Context context) {
        return LineDexPreferences.isDesktopEnabled(context)
                && activeDisplayId(context) > Display.DEFAULT_DISPLAY;
    }

    static boolean isPhoneScreenOff(final Context context) {
        return sPhoneScreenOff;
    }

    static boolean setPhoneScreenOff(final boolean screenOff) {
        if (sPhoneScreenOff == screenOff) {
            return false;
        }
        sPhoneScreenOff = screenOff;
        return true;
    }
}
