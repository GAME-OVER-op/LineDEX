# Device testing

## First boot

1. Install the APK.
2. Enable LineDEX for System Framework and SystemUI in the Xposed manager.
3. Reboot.
4. Grant LineDEX root access in KernelSU/Magisk.
5. Open LineDEX and confirm the root backend reports UID 0.
6. Tap **Prepare Android freeform settings**.
7. Enable the LineDEX session while the monitor is disconnected.
8. Connect the monitor. If it was already connected, disconnect and reconnect it once after session enable.

## Expected checkpoints

- The control panel reports an active LSPosed bridge.
- The external display receives its own Activity instead of mirroring display 0.
- The physical pointer moves to the external display in automatic mode.
- **Open desktop on monitor** launches the LineDEX desktop.
- Applications launched from the menu use the external display and freeform mode when supported.
- Disconnecting the monitor returns the pointer to display 0.
- Disabling the session restores the phone display before closing the desktop.

## Recovery

Keep root/ADB access available during early testing.

To disable LineDEX without opening the UI:

```sh
settings put global linedex_session_enabled 0
settings put global force_desktop_mode_on_external_displays 0
settings put global override_desktop_mode_features 0
cmd display power-reset 0
reboot
```

You can also disable the module in the Xposed manager and reboot.

## Alpha02 pointer diagnostics

After connecting a physical mouse, generate a compatibility report. The `INPUT-MOUSE-001` line should show the same positive display ID for `applied` and `external`. If they differ, include that line and the `POINTER-APPLY-001` event in the next report.
