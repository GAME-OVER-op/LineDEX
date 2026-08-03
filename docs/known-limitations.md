# Known limitations

- The first hook profile is verified only against the supplied REDMAGIC 9 Pro (`tiro`) LineageOS 23.2 firmware files.
- A firmware update can rename or change hooked methods. Hooks fail independently and report diagnostic codes, but runtime testing is still required.
- Native WMShell captions depend on the ROM's desktop components being initialized successfully after the SystemUI hooks are active.
- Some applications ignore resize requests, force portrait orientation, or keep a single task across displays.
- Phone-display power control is experimental and may be rejected by SELinux or the display service.
- Physical keyboard routing can vary by dock and input-port metadata. Pointer routing is handled separately by the `system_server` hook.
- The inherited MagicDesk code still contains compatibility helpers for its original Nubia backend. LineDEX active flows use the AOSP backend; remaining helpers are retained for gradual refactoring and test coverage.
