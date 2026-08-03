# Changelog

## 0.1.0-alpha02

- Removed the Shizuku dependency, provider, UserService and AIDL command service.
- Replaced privileged app commands with a root-only `su -c` backend for settings, tasks, native helpers and diagnostics.
- Fixed the desktop switch crash by moving Settings writes out of the app-originated `system_server` Binder call.
- Made session enable asynchronous and fail-safe: root or Binder failures restore the switch instead of terminating LineDEX.
- Corrected the Android 16 input service class to `com.android.server.input.InputManagerInternal`.
- Added physical mouse routing through `InputManagerService.mNative.setPointerDisplayId()` with a virtual-pointer compatibility fallback.
- Updated diagnostics, setup UI and unit tests for REDMAGIC 9 Pro / NX769J and the root-only architecture.
- Replaced hidden `Display.getType()` use with public presentation-display discovery.
- Retained the CI fixes for Android API 36, NDK r29, Gradle 9.4.1 and AGP 9 unit-test/lint tasks.

## 0.1.0-alpha01

- Initial LineDEX source release.
- Added a phone-side desktop-session control panel.
- Added a legacy-compatible LSPosed/Xposed entry for `system_server` and SystemUI.
- Added typed AIDL IPC between the app and the privileged hook runtime.
- Added dynamic extended-display policy and physical pointer routing hooks.
- Added WMShell desktop-eligibility hooks for the verified LineageOS 23.2 firmware.
- Registered the inherited MagicDesk shell as an external-display `SECONDARY_HOME`.
- Added native monitor-mode selection, per-display DPI handling, and root task control.
- Added `devRelease` and signed `release` GitHub Actions workflows.
