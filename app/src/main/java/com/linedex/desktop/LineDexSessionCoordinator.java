package com.linedex.desktop;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** App-process lifecycle coordinator for the external LineDEX desktop task. */
public final class LineDexSessionCoordinator {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LineDexSessionCoordinator() {
    }

    public static void startOnExternalDisplay(
            final Context context,
            final int displayId) {
        if (context == null || displayId <= 0
                || !LineDexPreferences.isDesktopEnabled(context)) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        MagicDeskRuntimeService.start(appContext);
        MAIN.postDelayed(
                () -> DesktopActivity.launchOnDisplay(appContext, displayId),
                700L);
    }

    public static void stop(final Context context) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        MagicDeskRuntimeService.stop(appContext);
        final ActivityManager manager =
                appContext.getSystemService(ActivityManager.class);
        if (manager == null) {
            return;
        }
        for (final ActivityManager.AppTask task : manager.getAppTasks()) {
            try {
                final ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                if (isLineDexDesktop(info == null ? null : info.baseActivity)
                        || isLineDexDesktop(info == null ? null : info.topActivity)) {
                    task.finishAndRemoveTask();
                }
            } catch (RuntimeException ignored) {
                // A stale task may disappear while the session is stopping.
            }
        }
    }

    private static boolean isLineDexDesktop(final ComponentName component) {
        return component != null
                && DesktopActivity.class.getName().equals(component.getClassName());
    }
}
