package de.robv.android.xposed;

/** Compile-only declarations for the legacy Xposed API. */
public interface IXposedHookZygoteInit {
    final class StartupParam {
        public String modulePath;
        public boolean startsSystemServer;
    }

    void initZygote(StartupParam startupParam) throws Throwable;
}
