# Changelog

- Fix NDK r29 validation and native helper compilation by using `clang --target=aarch64-linux-android35` instead of a nonexistent API 36 wrapper.

## CI maintenance

- Install Android command-line tools before invoking `sdkmanager`.
- Provision platform 36, build-tools 36.0.0 and NDK 29 through `android-actions/setup-android@v4`.
- Move GitHub-hosted actions to Node 24-compatible major versions.
- Derive Android SDK and NDK paths from the setup action instead of hard-coding runner paths.

## 0.1.0-alpha01

- Initial LineDEX source release.
- Added a phone-side desktop-session control panel.
- Added a legacy-compatible LSPosed/Xposed entry for `system_server` and SystemUI.
- Added typed AIDL IPC between the app and the privileged hook runtime.
- Added dynamic extended-display policy and physical pointer routing hooks.
- Added WMShell desktop-eligibility hooks for the verified LineageOS 23.2 firmware.
- Registered the inherited MagicDesk shell as an external-display `SECONDARY_HOME`.
- Added native monitor-mode selection, per-display DPI handling, and Shizuku task control.
- Added `devRelease` and signed `release` GitHub Actions workflows.
