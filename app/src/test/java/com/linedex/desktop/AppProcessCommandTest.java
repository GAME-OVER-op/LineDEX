package com.linedex.desktop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AppProcessCommandTest {
    @Test
    public void commandResolvesInstalledApkAndArguments() {
        final String command = AppProcessCommand.run(
                "com.linedex.desktop.TestCommand",
                "one two");

        assertTrue(command.contains(
                "pm path " + BuildConfig.APPLICATION_ID));
        assertTrue(command.contains(
                "CLASSPATH=\"$APK\" /system/bin/app_process / "
                        + "com.linedex.desktop.TestCommand one two"));
        assertFalse(command.contains("  one"));
    }

    @Test
    public void execUsesShellReplacement() {
        final String command = AppProcessCommand.exec(
                "com.linedex.desktop.Watcher", "");

        assertTrue(command.contains("export CLASSPATH=\"$APK\""));
        assertTrue(command.endsWith(
                "exec /system/bin/app_process / "
                        + "com.linedex.desktop.Watcher"));
    }
}
