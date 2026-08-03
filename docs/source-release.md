# Source release status

Version: `0.1.0-alpha01`

This source tree contains the initial complete LineDEX implementation intended
for GitHub Actions and device testing on the supplied LineageOS 23.2 firmware.

## Included

- Android application and external-display desktop shell
- Shizuku AIDL/UserService task backend
- LineDEX app-to-`system_server` AIDL bridge
- legacy-compatible Xposed entry and compile-only API declarations
- `system_server` display-policy and pointer-routing hooks
- SystemUI/WMShell desktop eligibility hooks
- monitor mode and density controls
- native keyboard/mouse helper sources
- unit-test sources inherited and adapted from MagicDesk
- dev-release and signed-release GitHub workflows
- signing, verification, diagnostic, and documentation files

## Validation performed for this source archive

- parsed all Android resource and manifest XML files;
- parsed all GitHub workflow YAML files;
- checked Java package paths and public top-level type names;
- checked manifest component classes and Java string/style references;
- checked AIDL package paths and Xposed entry/scope metadata;
- compiled the local Xposed API declarations with `javac`;
- ran Java compiler parsing across the source tree and found no syntax errors;
- compiled both native C files in strict syntax-check mode;
- checked shell scripts with `bash -n`;
- verified the Gradle wrapper JAR contains `GradleWrapperMain`;
- checked that no keystore, signing properties, firmware archive, or user data is present.

A full Android Gradle build was not executed in the source-preparation
container because it did not contain the Android SDK and could not download
Gradle/Maven dependencies. The included GitHub Actions workflow installs/uses
the required Android components and performs tests, lint, APK assembly, and APK
content verification.
