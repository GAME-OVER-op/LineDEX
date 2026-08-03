# Architecture

## Application process

`LineDexControlActivity` is the phone-side control panel. It stores user intent in `LineDexPreferences` and sends immediate actions to `ILineDexSystemBridge`.

`DesktopActivity` is both manually launchable on a selected display and registered as `android.intent.category.SECONDARY_HOME`. The inherited MagicDesk desktop shell supplies the Start menu, taskbar, application repository, task overview, notifications, workspace profiles and freeform task controls.

`LineDexBridgeService` is an explicit endpoint. It accepts only system UID binding. The service receives the privileged system bridge Binder from the Xposed code and publishes it to `LineDexBridgeClient`.

## system_server process

`LineDexXposedEntry` hooks `com.android.server.SystemServer.startOtherServices` and initializes `HookRuntime` with the real system context.

The following responsibilities stay inside `system_server`:

- deciding whether LineDEX currently allows an extended external display;
- selecting the physical pointer display;
- applying a user-preferred physical display mode;
- experimental display-0 power operations;
- validating app callers and serving the typed Binder interface;
- reporting display and pointer changes through `RemoteCallbackList`.

The bridge is delivered by explicitly binding to the application's `LineDexBridgeService`. LineDEX does not replace `tv_input`, `dropbox`, clipboard, app-widget, or any other system service.

## SystemUI process

The SystemUI hook reads the dynamic `linedex_session_enabled` global setting. While enabled, it allows the verified WMShell `DesktopStateImpl` implementations to enter desktop/freeform mode on external displays.

No custom window frames are drawn by LineDEX. Window captions, resizing, maximize, fullscreen, snapping and transitions remain WMShell-owned.

## Shizuku

The inherited task-control layer uses a Shizuku UserService running as shell UID 2000. This is separate from the LSPosed bridge:

- LSPosed handles display policy, pointer routing and system-only APIs.
- Shizuku handles task/window shell commands and task observation.

## Failure behavior

When the application endpoint disconnects, `HookRuntime` attempts to restore display 0 and the default pointer target. Session state is disabled after every fresh `system_server` start until the application republishes the user's saved preference.
