# Security

LineDEX executes code in `system_server` and SystemUI through an Xposed-compatible
framework. Treat every release as privileged system software.

## Supported version

Only the latest source revision is supported during the alpha phase.

## Reporting

Do not publish an issue containing a working privilege-escalation path, signing
key, personal diagnostic data, or a Binder bypass. Report it privately to the
repository owner before public disclosure.

## Design constraints

- The privileged bridge exposes typed operations; it does not expose arbitrary shell execution.
- The bridge Binder is delivered through an explicit service binding and is not registered under an existing Android service name.
- The app endpoint accepts bridge publication only from system UID.
- The privileged bridge accepts the installed LineDEX UID or system UID.
- Keystores and signing properties are excluded from version control.
- Pointer and phone-display state are restored when the app bridge disconnects.

A production deployment should additionally pin the expected APK signing
certificate in the `system_server` bridge before supporting third-party builds.
## system_server bridge authentication

`LineDexBridgeService` is exported only for the LSPosed code running as UID 1000. The service is protected by an application-specific signature permission and accepts only the explicit LineDEX bridge action. Caller identity is not read from `Service.onBind()`, because that callback is dispatched by the application main thread and does not preserve the original Binder caller. The actual AIDL transaction `publishSystemBridge()` validates `Binder.getCallingUid() == Process.SYSTEM_UID` before accepting the privileged bridge.

