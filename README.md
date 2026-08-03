# LineDEX

LineDEX is an experimental desktop environment for Android 16 / LineageOS external displays. It is built from the open-source MagicDesk desktop shell and replaces the original Nubia Console Mode dependency with an AOSP/Lineage backend.

The first target is:

- REDMAGIC 9 Pro (`tiro`, SM8650)
- LineageOS 23.2 / Android 16 (SDK 36)
- Revenge Xposed / LSPosed-compatible framework
- USB-C DisplayPort monitor
- Shizuku running as shell UID 2000

## Current alpha scope

The repository contains a buildable single APK with:

- phone-side LineDEX control panel;
- legacy-compatible LSPosed/Xposed entry;
- `system_server` hooks for external-display policy and pointer routing;
- SystemUI/WMShell desktop-state hooks;
- typed AIDL Binder bridge between the app and `system_server`;
- external-display `SECONDARY_HOME` desktop;
- application menu, taskbar, task switching and freeform task management inherited from MagicDesk;
- monitor mode selection;
- per-display DPI controls;
- experimental phone-display power control;
- Shizuku task-management backend;
- GitHub Actions for dev and signed release builds.

This is an **alpha hardware-specific project**. The hooks use class names verified against the supplied LineageOS 23.2 firmware. A failed or changed hook should fail open and leave the phone display usable, but testing should be done with ADB/root access available.

## Required Xposed scope

Enable LineDEX for:

1. **System Framework** (`system` / `android`)
2. **System UI** (`com.android.systemui`)

Then reboot. Open LineDEX on the phone, grant Shizuku access, prepare Android freeform settings, enable the desktop session, and only then connect the monitor.

## Build locally

Requirements:

- JDK 17
- Android SDK platform 37 (compile SDK)
- Android build-tools 37.0.0
- Android 16 target SDK 36
- Android NDK `29.0.14206865`

```bash
./gradlew :app:testDevReleaseUnitTest :app:lintDevRelease :app:assembleDevRelease
```

Output:

```text
app/build/outputs/apk/devRelease/app-devRelease.apk
```

`devRelease` uses release code settings (`debuggable=false`) and is signed with:

1. a configured LineDEX development keystore, or
2. Gradle's debug key as a buildable fallback.

The fallback uses the standard Android debug certificate generated on the build machine. Configure a persistent LineDEX development key for reliably upgradeable test APKs.

## GitHub Actions

Push the repository to GitHub. The **Android CI** workflow builds and uploads `LineDEX-devRelease`.

For a stable development certificate, add these repository secrets:

```text
LINEDEX_DEV_KEYSTORE_BASE64
LINEDEX_DEV_STORE_PASSWORD
LINEDEX_DEV_KEY_ALIAS
LINEDEX_DEV_KEY_PASSWORD
```

Generate a development keystore with:

```bash
./scripts/generate-dev-keystore.sh
```

For production tags (`v*`), configure:

```text
LINEDEX_RELEASE_KEYSTORE_BASE64
LINEDEX_RELEASE_STORE_PASSWORD
LINEDEX_RELEASE_KEY_ALIAS
LINEDEX_RELEASE_KEY_PASSWORD
```

Never commit either keystore.

## Runtime design

```text
LineDEX APK
├── phone control activity
├── external SECONDARY_HOME desktop
├── Shizuku task service
├── typed AIDL bridge endpoint
└── Xposed module
    ├── system_server
    │   ├── extended-display policy
    │   ├── physical pointer target
    │   ├── monitor mode control
    │   ├── phone display power
    │   └── privileged Binder bridge
    └── SystemUI
        └── WMShell desktop eligibility
```

See [Architecture](docs/architecture.md), [Verified firmware hooks](docs/firmware-hooks.md), [Testing](docs/testing.md), and [Known limitations](docs/known-limitations.md).

## Security model

The system bridge:

- accepts calls only from the installed `com.linedex.desktop` UID or system UID;
- exposes typed operations instead of arbitrary shell execution;
- restores display 0 and the default pointer target when the app endpoint dies;
- does not register under or replace an existing Android Binder service name.

Shizuku remains necessary for the inherited task/window management commands. It must run as shell UID 2000.

## Attribution and license

LineDEX is derived from MagicDesk by Ilya Mekhontsev. The inherited code is used under the MIT License. See [NOTICE](NOTICE) and [LICENSE](LICENSE).

## Project files

- [Changelog](CHANGELOG.md)
- [Security notes](SECURITY.md)
- [MIT license](LICENSE)
