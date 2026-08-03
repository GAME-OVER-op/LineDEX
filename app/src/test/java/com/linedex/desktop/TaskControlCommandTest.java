package com.linedex.desktop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TaskControlCommandTest {
    @Test
    public void shellUsesThePackageOwnedByUid2000() {
        assertEquals(
                "com.android.shell",
                TaskControlCommand.callingPackageForUid(2000));
    }

    @Test
    public void appAndRootUseCurrentApplicationId() {
        assertEquals(
                BuildConfig.APPLICATION_ID,
                TaskControlCommand.callingPackageForUid(0));
        assertEquals(
                BuildConfig.APPLICATION_ID,
                TaskControlCommand.callingPackageForUid(10615));
    }
}
