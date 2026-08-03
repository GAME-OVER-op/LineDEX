package com.linedex.desktop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DeviceSetupOverlayPolicyTest {
    @Test
    public void overlayProvisioningTargetsOnlyMagicDesk() {
        assertEquals(
                "/system/bin/cmd appops set com.linedex.desktop "
                        + "SYSTEM_ALERT_WINDOW allow",
                DeviceSetupManager.overlayPermissionCommand());
    }
}
