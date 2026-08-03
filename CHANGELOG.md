# Changelog

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
