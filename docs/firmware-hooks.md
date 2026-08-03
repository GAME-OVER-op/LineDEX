# Verified firmware hooks

Reference firmware supplied for development:

```text
Device: tiro / REDMAGIC 9 Pro
SoC: SM8650
OS: LineageOS 23.2
Android: 16
SDK: 36
```

## system_server

Verified class and method names in the supplied `services.jar`:

```text
com.android.server.SystemServer#startOtherServices
com.android.server.display.DisplayManagerService#isExtendedDisplayAllowed
com.android.server.wm.InputManagerCallback#getPointerDisplayId
com.android.server.input.InputManagerInternal (local service)
com.android.server.input.InputManagerService.mNative#setPointerDisplayId
```

`getPointerDisplayId()` is the policy hook for the physical-mouse route. The supplied ROM also exposes the native `setPointerDisplayId(int)` path, which LineDEX invokes after session or input-device changes. The stock implementation returns display 0 when the legacy force-desktop field is false, even when a hot-plugged external display has already accepted a separate Activity.

`isExtendedDisplayAllowed()` is hooked dynamically. This replaces the manual post-boot `force_desktop_mode_on_external_displays=1` workaround.

## SystemUI / WMShell

Verified classes in the supplied `SystemUI.apk`:

```text
com.android.wm.shell.shared.desktopmode.DesktopStateImpl
com.android.wm.shell.desktopmode.ShellDesktopStateImpl
com.android.wm.shell.desktopmode.DesktopTasksController
com.android.wm.shell.windowdecor.DesktopModeWindowDecoration
com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel
com.android.wm.shell.freeform.FreeformTaskTransitionHandler
```

Hooked state methods are discovered by name so minor overload changes do not prevent APK compilation:

```text
canEnterDesktopMode
isFreeformEnabled
isDesktopModeSupportedOnDisplay
enterDesktopByDefaultOnFreeformDisplay
```

Every hook is wrapped independently. Missing methods are logged rather than crashing SystemUI.
