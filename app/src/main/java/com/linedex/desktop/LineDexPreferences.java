package com.linedex.desktop;

import android.content.Context;
import android.content.SharedPreferences;

public final class LineDexPreferences {
    private static final String PREFS = "linedex";
    private static final String KEY_ENABLED = "desktop_enabled";
    private static final String KEY_POINTER_TARGET = "pointer_target";
    private static final String KEY_AUTO_NATIVE_MODE = "auto_native_mode";

    public static final int POINTER_AUTO = -2;
    public static final int POINTER_PHONE = 0;

    private LineDexPreferences() {
    }

    public static boolean isDesktopEnabled(final Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setDesktopEnabled(
            final Context context, final boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static int pointerTarget(final Context context) {
        return prefs(context).getInt(KEY_POINTER_TARGET, POINTER_AUTO);
    }

    public static void setPointerTarget(
            final Context context, final int displayId) {
        prefs(context).edit().putInt(KEY_POINTER_TARGET, displayId).apply();
    }

    public static boolean autoNativeMode(final Context context) {
        return prefs(context).getBoolean(KEY_AUTO_NATIVE_MODE, true);
    }

    public static void setAutoNativeMode(
            final Context context, final boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_NATIVE_MODE, enabled).apply();
    }

    private static SharedPreferences prefs(final Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
