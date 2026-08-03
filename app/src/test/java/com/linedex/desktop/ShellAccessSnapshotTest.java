package com.linedex.desktop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ShellAccessSnapshotTest {
    @Test
    public void grantedRootBackendIsReady() {
        assertTrue(snapshot(true, true, 0, 1).isReady());
    }

    @Test
    public void unavailableDeniedOrUnprivilegedBackendIsNotReady() {
        assertFalse(snapshot(false, true, 0, 1).isReady());
        assertFalse(snapshot(true, false, 0, 1).isReady());
        assertFalse(snapshot(true, true, 2000, 1).isReady());
        assertFalse(snapshot(true, true, 10615, 1).isReady());
    }

    private static ShellAccess.Snapshot snapshot(
            final boolean running,
            final boolean permissionGranted,
            final int uid,
            final int version) {
        return new ShellAccess.Snapshot(
                true,
                running,
                permissionGranted,
                uid,
                version,
                "");
    }
}
