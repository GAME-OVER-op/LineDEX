# Device testing

## First boot

1. Install the APK.
2. Enable LineDEX for System Framework and SystemUI in the Xposed manager.
3. Reboot.
4. Start Shizuku as shell UID 2000.
5. Open LineDEX and grant Shizuku access.
6. Tap **Prepare Android freeform settings**.
7. Enable the LineDEX session while the monitor is disconnected.
8. Connect the monitor.

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
cmd display power-reset 0
reboot
```

You can also disable the module in the Xposed manager and reboot.
