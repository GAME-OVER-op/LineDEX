package com.linedex.desktop;

import android.content.Context;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Root-only command backend used by LineDEX.
 *
 * <p>The application process never assumes that Android permissions are elevated merely
 * because root is available. Every privileged shell operation is executed through the
 * device's {@code su} implementation. LSPosed-only framework calls remain inside
 * {@code system_server}.</p>
 */
final class ShellAccess {
    static final int ROOT_UID = 0;
    private static final long PROBE_TIMEOUT_MILLIS = 15_000L;
    private static final long TASK_POLL_INTERVAL_MILLIS = 700L;
    private static final Set<StateListener> STATE_LISTENERS =
            new CopyOnWriteArraySet<>();

    private static volatile boolean sInitialized;
    private static volatile String sSuBinary = "su";
    private static volatile Snapshot sSnapshot = Snapshot.unavailable(
            false, "Root access is not initialized");

    interface StateListener {
        void onShellStateChanged(Snapshot snapshot);
    }

    private ShellAccess() {
    }

    static synchronized void initialize() {
        if (sInitialized) {
            return;
        }
        sInitialized = true;
        final Thread probe = new Thread(ShellAccess::refresh, "LineDexRootProbe");
        probe.setDaemon(true);
        probe.start();
    }

    static boolean isReady() {
        return sSnapshot.isReady();
    }

    static String statusLabel() {
        final Snapshot snapshot = sSnapshot;
        return snapshot.isReady()
                ? "root ready"
                : snapshot.error.isEmpty() ? "root unavailable" : snapshot.error;
    }

    static void addStateListener(final StateListener listener) {
        if (listener == null) {
            return;
        }
        STATE_LISTENERS.add(listener);
        listener.onShellStateChanged(sSnapshot);
    }

    static void removeStateListener(final StateListener listener) {
        STATE_LISTENERS.remove(listener);
    }

    static synchronized Snapshot refresh() {
        return publish(inspectNow());
    }

    private static Snapshot inspectNow() {
        final String su = findSuBinary();
        if (su == null) {
            return Snapshot.unavailable(false, "su binary was not found");
        }
        sSuBinary = su;
        Process process = null;
        try {
            process = new ProcessBuilder(
                    su,
                    "-c",
                    "/system/bin/id -u; "
                            + "/system/bin/id -Z 2>/dev/null || true")
                    .redirectErrorStream(true)
                    .start();
            final BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                    process, PROBE_TIMEOUT_MILLIS, 64 * 1024);
            final String[] lines = result.output.trim().split("\\r?\\n");
            final int uid = lines.length == 0 ? -1 : parseInt(lines[0], -1);
            if (result.exitCode != 0) {
                return new Snapshot(
                        true, true, false, uid, 1,
                        "Root request failed (exit " + result.exitCode + "): "
                                + result.output.trim());
            }
            if (uid != ROOT_UID) {
                return new Snapshot(
                        true, true, false, uid, 1,
                        "Root was not granted; uid=" + uid);
            }
            return new Snapshot(true, true, true, uid, 1, "");
        } catch (IOException error) {
            return Snapshot.unavailable(true, usefulMessage(error));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Snapshot.unavailable(true, "Root probe was interrupted");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    static int connectAndGetUid() throws IOException {
        final String output = run("/system/bin/id -u").trim();
        final int uid = parseInt(output, -1);
        if (uid != ROOT_UID) {
            throw new IOException("root command returned uid=" + uid);
        }
        return uid;
    }

    static String run(final String command) throws IOException {
        if (command == null || command.trim().isEmpty()) {
            throw new IOException("empty root command");
        }
        requireRoot();
        Process process = null;
        try {
            process = startRootProcess(command);
            final BoundedProcessRunner.Result result =
                    BoundedProcessRunner.run(process);
            if (result.exitCode != 0) {
                throw new IOException(
                        "root command failed " + result.exitCode + ": "
                                + result.output.trim());
            }
            return result.output;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("root command interrupted", error);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    static String probeCapabilities() throws IOException {
        return run(
                "printf 'format=2\\n'; "
                        + "printf 'backend=root\\n'; "
                        + "printf 'identity.uid='; /system/bin/id -u; "
                        + "printf 'identity.gid='; /system/bin/id -g; "
                        + "printf 'identity.groups='; /system/bin/id -G; "
                        + "printf 'identity.selinux='; "
                        + "/system/bin/id -Z 2>/dev/null || true; "
                        + "printf 'access.dev_input='; "
                        + "if ls /dev/input/event* >/dev/null 2>&1; then echo yes; else echo no; fi; "
                        + "printf 'access.uinput='; "
                        + "if [ -r /dev/uinput ] && [ -w /dev/uinput ]; then echo rw; "
                        + "elif [ -e /dev/uinput ]; then echo denied; else echo missing; fi; "
                        + "printf 'tool.settings='; command -v settings || true; "
                        + "printf 'tool.wm='; command -v wm || true; "
                        + "printf 'tool.am='; command -v am || true; "
                        + "printf 'tool.cmd='; command -v cmd || true; "
                        + "printf 'tool.dumpsys='; command -v dumpsys || true");
    }

    static String updateHardwareKeyboardLayout(
            final String mode,
            final String currentDescriptor) throws IOException {
        final Context context = MagicDeskApplication.applicationContext();
        if (context == null) {
            throw new IOException("application context is unavailable");
        }
        final StringBuilder command = new StringBuilder();
        command.append("CLASSPATH=")
                .append(shellQuote(context.getApplicationInfo().sourceDir))
                .append(" /system/bin/app_process /system/bin ")
                .append(HardwareKeyboardLayoutCommand.class.getName())
                .append(' ').append(shellQuote(mode));
        if (currentDescriptor != null && !currentDescriptor.isEmpty()) {
            command.append(' ').append(shellQuote(currentDescriptor));
        }
        final String output = run(command.toString());
        if (!"catalog".equals(mode)) {
            final String descriptor = field(output, "descriptor");
            final String code = field(output, "code");
            final String name64 = field(output, "name64");
            final String name;
            try {
                name = new String(
                        Base64.decode(name64, Base64.DEFAULT),
                        StandardCharsets.UTF_8);
            } catch (IllegalArgumentException error) {
                throw new IOException("invalid keyboard layout response", error);
            }
            run("/system/bin/settings put global "
                    + HardwareKeyboardLayoutController.LAYOUT_LABEL_STATE + " "
                    + shellQuote(code) + "; "
                    + "/system/bin/settings put global "
                    + HardwareKeyboardLayoutController.LAYOUT_NAME_STATE + " "
                    + shellQuote(name) + "; "
                    + "/system/bin/settings put global "
                    + HardwareKeyboardLayoutController.LAYOUT_STATE + " "
                    + shellQuote(descriptor));
        }
        return output;
    }

    static boolean capturePointerPosition() {
        // Physical cursor ownership is handled by the system_server pointer override.
        return false;
    }

    static void restorePointerPositionIfDisplaced() {
        // No legacy Nubia uinput cursor is used in the root-only backend.
    }

    static ParcelFileDescriptor openSystemWallpaper() throws IOException {
        requireRoot();
        final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        final ParcelFileDescriptor readSide = pipe[0];
        final ParcelFileDescriptor writeSide = pipe[1];
        final Thread copyThread = new Thread(() -> {
            Process process = null;
            try (OutputStream output =
                         new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
                process = startRootProcess(
                        "user=$(/system/bin/cmd activity get-current-user 2>/dev/null || echo 0); "
                                + "for f in /data/system/users/$user/wallpaper "
                                + "/data/system/users/0/wallpaper; do "
                                + "if [ -r \"$f\" ]; then cat \"$f\"; exit 0; fi; done; exit 1");
                try (InputStream input = new BufferedInputStream(process.getInputStream())) {
                    copy(input, output);
                }
                process.waitFor(15, TimeUnit.SECONDS);
            } catch (IOException ignored) {
                // The reader receives EOF when wallpaper access is unavailable.
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }, "LineDexRootWallpaper");
        copyThread.setDaemon(true);
        copyThread.start();
        return readSide;
    }

    static StreamHandle openOwnedStream(final String command)
            throws IOException {
        return openStream(command);
    }

    static StreamHandle openHeartbeatStream(final String command)
            throws IOException {
        return openStream(command);
    }

    private static StreamHandle openStream(final String command)
            throws IOException {
        if (command == null || command.trim().isEmpty()) {
            throw new IOException("empty root stream command");
        }
        requireRoot();
        return new StreamHandle(startRootProcess(command));
    }

    static TaskObserverHandle openTaskObserver(
            final ITaskObserverCallback callback,
            final Runnable disconnected) throws IOException {
        if (callback == null) {
            throw new IOException("missing task observer callback");
        }
        requireRoot();
        final TaskObserverHandle handle = new TaskObserverHandle(callback, disconnected);
        handle.start();
        return handle;
    }

    static void requestPermission() {
        final Snapshot snapshot = refresh();
        if (!snapshot.isReady()) {
            throw new IllegalStateException(snapshot.error);
        }
    }

    static void openManagerOrWebsite(final Context context) {
        final Thread probe = new Thread(ShellAccess::refresh, "LineDexRootRequest");
        probe.setDaemon(true);
        probe.start();
    }

    static void disconnect() {
        // Root commands are short-lived. There is no external service to disconnect.
    }

    private static void requireRoot() throws IOException {
        final Snapshot snapshot = sSnapshot;
        if (!snapshot.isReady()) {
            final Snapshot refreshed = refresh();
            if (!refreshed.isReady()) {
                throw new IOException(refreshed.error.isEmpty()
                        ? "root access is unavailable" : refreshed.error);
            }
        }
    }

    private static Process startRootProcess(final String command)
            throws IOException {
        return new ProcessBuilder(sSuBinary, "-c", command)
                .redirectErrorStream(true)
                .start();
    }

    private static String findSuBinary() {
        final String[] candidates = {
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/debug_ramdisk/su"
        };
        for (final String candidate : candidates) {
            final File file = new File(candidate);
            if (file.isFile() && file.canExecute()) {
                return candidate;
            }
        }
        try {
            final Process process = new ProcessBuilder(
                    "/system/bin/sh", "-c", "command -v su")
                    .redirectErrorStream(true)
                    .start();
            final BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                    process, 3_000L, 8 * 1024);
            final String path = result.output.trim();
            return result.exitCode == 0 && !path.isEmpty() ? path : null;
        } catch (IOException error) {
            return null;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static synchronized Snapshot publish(final Snapshot snapshot) {
        final Snapshot previous = sSnapshot;
        sSnapshot = snapshot;
        if (previous.sameState(snapshot)) {
            return snapshot;
        }
        for (final StateListener listener : STATE_LISTENERS) {
            listener.onShellStateChanged(snapshot);
        }
        return snapshot;
    }

    private static String field(final String output, final String name)
            throws IOException {
        final String prefix = name + "=";
        for (final String line : output.split("\\r?\\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        throw new IOException("missing " + name + " in command response");
    }

    private static int parseInt(final String value, final int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static String shellQuote(final String value) {
        final String safe = value == null ? "" : value;
        return "'" + safe.replace("'", "'\"'\"'") + "'";
    }

    private static String usefulMessage(final Throwable error) {
        final String message = error.getMessage();
        return message == null || message.isEmpty()
                ? error.getClass().getSimpleName() : message;
    }

    private static void copy(final InputStream input, final OutputStream output)
            throws IOException {
        final byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        output.flush();
    }

    static final class Snapshot {
        final boolean installed;
        final boolean running;
        final boolean permissionGranted;
        final int uid;
        final int version;
        final String error;

        Snapshot(
                final boolean installed,
                final boolean running,
                final boolean permissionGranted,
                final int uid,
                final int version,
                final String error) {
            this.installed = installed;
            this.running = running;
            this.permissionGranted = permissionGranted;
            this.uid = uid;
            this.version = version;
            this.error = error == null ? "" : error;
        }

        static Snapshot unavailable(
                final boolean installed, final String error) {
            return new Snapshot(installed, false, false, -1, 1, error);
        }

        boolean isReady() {
            return running && permissionGranted && uid == ROOT_UID;
        }

        private boolean sameState(final Snapshot other) {
            return other != null
                    && installed == other.installed
                    && running == other.running
                    && permissionGranted == other.permissionGranted
                    && uid == other.uid
                    && version == other.version
                    && Objects.equals(error, other.error);
        }
    }

    static final class StreamHandle implements Closeable {
        private final Process mProcess;
        private final InputStream mInput;
        private final BufferedWriter mWriter;
        private final AtomicBoolean mClosed = new AtomicBoolean();

        StreamHandle(final Process process) {
            mProcess = process;
            mInput = process.getInputStream();
            mWriter = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
        }

        InputStream inputStream() {
            return mInput;
        }

        void writeLine(final String line) throws IOException {
            if (mClosed.get()) {
                throw new IOException("root stream is closed");
            }
            mWriter.write(line == null ? "" : line);
            mWriter.newLine();
            mWriter.flush();
        }

        @Override
        public void close() {
            if (!mClosed.compareAndSet(false, true)) {
                return;
            }
            try {
                mWriter.close();
            } catch (IOException ignored) {
                // Process termination is authoritative.
            }
            try {
                mInput.close();
            } catch (IOException ignored) {
                // Process termination is authoritative.
            }
            mProcess.destroy();
            try {
                if (!mProcess.waitFor(500, TimeUnit.MILLISECONDS)) {
                    mProcess.destroyForcibly();
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                mProcess.destroyForcibly();
            }
        }
    }

    static final class TaskObserverHandle implements Closeable, Runnable {
        private final ITaskObserverCallback mCallback;
        private final Runnable mDisconnected;
        private final AtomicBoolean mClosed = new AtomicBoolean();
        private final Thread mThread;

        private volatile int mDisplayId = -1;
        private volatile Rect mDisplayBounds = new Rect();
        private volatile Rect mWorkAreaBounds = new Rect();

        TaskObserverHandle(
                final ITaskObserverCallback callback,
                final Runnable disconnected) {
            mCallback = callback;
            mDisconnected = disconnected;
            mThread = new Thread(this, "LineDexRootTaskObserver");
            mThread.setDaemon(true);
        }

        void start() {
            mThread.start();
        }

        void configure(
                final int displayId,
                final Rect displayBounds,
                final Rect workAreaBounds) throws IOException {
            if (displayBounds == null || workAreaBounds == null) {
                throw new IOException("missing task observer bounds");
            }
            mDisplayId = displayId;
            mDisplayBounds = new Rect(displayBounds);
            mWorkAreaBounds = new Rect(workAreaBounds);
        }

        void focusStack(
                final long sequence,
                final int displayId,
                final int[] taskIds) throws IOException {
            if (taskIds == null || taskIds.length == 0) {
                throw new IOException("empty task stack");
            }
            final Context context = MagicDeskApplication.applicationContext();
            if (context == null) {
                throw new IOException("application context is unavailable");
            }
            final StringBuilder command = new StringBuilder();
            command.append("CLASSPATH=")
                    .append(shellQuote(context.getApplicationInfo().sourceDir))
                    .append(" /system/bin/app_process /system/bin ")
                    .append(TaskControlCommand.class.getName())
                    .append(" focus-stack");
            for (final int taskId : taskIds) {
                command.append(' ').append(taskId);
            }
            int count = 0;
            String error = "";
            boolean success = false;
            try {
                ShellAccess.run(command.toString());
                count = taskIds.length;
                success = true;
            } catch (IOException failure) {
                error = usefulMessage(failure);
            }
            try {
                mCallback.onFocusStackResult(
                        sequence, success, count, error);
            } catch (RemoteException failure) {
                close();
                throw new IOException("task observer callback failed", failure);
            }
        }

        boolean isClosed() {
            return mClosed.get();
        }

        @Override
        public void run() {
            String previous = "";
            try {
                while (!mClosed.get()) {
                    final String current = ShellAccess.run(
                            "/system/bin/dumpsys activity activities 2>/dev/null | "
                                    + "/system/bin/grep -E "
                                    + "'Task\\{|mResumedActivity|mFocusedApp|mTopResumedActivity' "
                                    + "|| true");
                    if (!current.equals(previous)) {
                        previous = current;
                        mCallback.onTasksChanged();
                    }
                    Thread.sleep(TASK_POLL_INTERVAL_MILLIS);
                }
            } catch (RemoteException error) {
                // The owning desktop process is gone.
            } catch (IOException error) {
                try {
                    mCallback.onObserverError(usefulMessage(error));
                } catch (RemoteException ignored) {
                    // The callback is already gone.
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                if (mClosed.compareAndSet(false, true) && mDisconnected != null) {
                    mDisconnected.run();
                }
            }
        }

        @Override
        public void close() {
            if (!mClosed.compareAndSet(false, true)) {
                return;
            }
            mThread.interrupt();
        }
    }
}
