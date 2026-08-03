package com.linedex.desktop;

import android.hardware.display.DisplayManager;
import android.view.Display;

/**
 * Public-SDK compatible external-display detection.
 *
 * <p>{@code Display#getType()} and {@code Display#TYPE_EXTERNAL} are framework
 * APIs that are not exposed by the public Android SDK.  LineDEX therefore uses
 * the presentation-display category, which is the supported application API
 * for discovering secondary displays suitable for independent content.</p>
 */
public final class ExternalDisplayCompat {
    private ExternalDisplayCompat() {
    }

    public static boolean isExternal(
            final DisplayManager manager,
            final Display display) {
        if (display == null
                || display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            return false;
        }
        if (manager != null) {
            final Display[] presentations = manager.getDisplays(
                    DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            for (final Display candidate : presentations) {
                if (candidate != null
                        && candidate.getDisplayId() == display.getDisplayId()) {
                    return true;
                }
            }
            if (presentations.length > 0) {
                return false;
            }
        }
        // Fail open for system-server/early-boot cases where presentation
        // categorisation is not populated yet. LineDEX never treats display 0
        // as external, and the bridge still validates that the display exists.
        return true;
    }

    public static Display findActiveExternalDisplay(
            final DisplayManager manager) {
        if (manager == null) {
            return null;
        }

        Display best = firstActiveSecondary(manager.getDisplays(
                DisplayManager.DISPLAY_CATEGORY_PRESENTATION));
        if (best != null) {
            return best;
        }

        // Some vendor display stacks publish the physical display before it is
        // assigned to the presentation category.  Keep a non-default fallback
        // so hotplug during boot remains functional.
        return firstActiveSecondary(manager.getDisplays());
    }

    public static int findActiveExternalDisplayId(
            final DisplayManager manager) {
        final Display display = findActiveExternalDisplay(manager);
        return display == null ? -1 : display.getDisplayId();
    }

    private static Display firstActiveSecondary(final Display[] displays) {
        if (displays == null) {
            return null;
        }
        Display best = null;
        for (final Display display : displays) {
            if (display == null
                    || display.getDisplayId() == Display.DEFAULT_DISPLAY
                    || display.getState() == Display.STATE_OFF) {
                continue;
            }
            if (best == null
                    || display.getDisplayId() < best.getDisplayId()) {
                best = display;
            }
        }
        return best;
    }
}
