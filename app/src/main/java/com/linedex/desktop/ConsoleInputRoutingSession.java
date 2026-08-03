package com.linedex.desktop;

import android.os.IBinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Associates physical keyboard input ports with the active external display.
 * Pointer placement itself is handled by the LineDEX system_server hook.
 */
final class ConsoleInputRoutingSession implements AutoCloseable {
    private static final int DISPLAY_TYPE_EXTERNAL = 2;
    private static final String DUMPSYS = "/system/bin/dumpsys";

    private final Set<String> mAssociatedInputPorts = new LinkedHashSet<>();

    private Object mInputManager;
    private Method mAddPortAssociation;
    private Method mRemovePortAssociation;
    private int mConsoleDisplayId = -1;
    private int mDisplayPort = -1;
    private int mKeyboardAssociationCount;
    private boolean mClosed;

    private ConsoleInputRoutingSession() {
    }

    static ConsoleInputRoutingSession open(
            final List<ConsoleKeyboardDevice> keyboards,
            final List<ConsoleMouseDevice> mice) throws Exception {
        cleanupStaleAssociations();
        final ConsoleInputRoutingSession session =
                new ConsoleInputRoutingSession();
        try {
            session.start(keyboards, mice);
            return session;
        } catch (Exception error) {
            session.close();
            throw error;
        }
    }

    int consoleDisplayId() {
        return mConsoleDisplayId;
    }

    int associationCount() {
        return mAssociatedInputPorts.size();
    }

    int keyboardAssociationCount() {
        return mKeyboardAssociationCount;
    }

    static int cleanupStaleAssociations() throws Exception {
        final Set<String> ownedPorts = ConsoleInputRoutingOwnership.read();
        if (ownedPorts.isEmpty()) {
            return 0;
        }
        final Object inputManager = getService(
                "input", "android.hardware.input.IInputManager");
        final Class<?> inputManagerInterface =
                Class.forName("android.hardware.input.IInputManager");
        final Method removePortAssociation = inputManagerInterface.getMethod(
                "removePortAssociation", String.class);
        removeAssociations(inputManager, removePortAssociation, ownedPorts);

        final Set<String> remaining =
                ConsoleInputRoutingOwnership.findRuntimeAssociations(
                        readInputDump());
        remaining.retainAll(ownedPorts);
        if (!remaining.isEmpty()) {
            throw new IOException(
                    "input associations remain after cleanup: " + remaining);
        }
        ConsoleInputRoutingOwnership.clear();
        return ownedPorts.size();
    }

    private void start(
            final List<ConsoleKeyboardDevice> keyboards,
            final List<ConsoleMouseDevice> mice) throws Exception {
        final ExternalDisplayTarget target = findExternalDisplayTarget();
        mConsoleDisplayId = target.displayId;
        mDisplayPort = target.port;
        if (mConsoleDisplayId <= 0 || mDisplayPort < 0) {
            throw new IllegalStateException(
                    "external physical display target not found");
        }

        mInputManager = getService(
                "input", "android.hardware.input.IInputManager");
        final Class<?> inputManagerInterface =
                Class.forName("android.hardware.input.IInputManager");
        mAddPortAssociation = inputManagerInterface.getMethod(
                "addPortAssociation", String.class, int.class);
        mRemovePortAssociation = inputManagerInterface.getMethod(
                "removePortAssociation", String.class);

        final Set<String> requestedPorts = new LinkedHashSet<>();
        for (final ConsoleKeyboardDevice keyboard : keyboards) {
            addRequestedPort(requestedPorts, keyboard.location);
        }
        // Mouse associations are harmless on builds that use them and are
        // ignored by the pointer hook on builds that do not.
        for (final ConsoleMouseDevice mouse : mice) {
            addRequestedPort(requestedPorts, mouse.location);
        }
        ConsoleInputRoutingOwnership.record(requestedPorts);

        for (final ConsoleKeyboardDevice keyboard : keyboards) {
            if (associatePort(keyboard.location)) {
                mKeyboardAssociationCount++;
            }
        }
        for (final ConsoleMouseDevice mouse : mice) {
            associatePort(mouse.location);
        }
        if (mKeyboardAssociationCount == 0 && !keyboards.isEmpty()) {
            throw new IllegalStateException(
                    "no external keyboard input port could be associated");
        }
    }

    synchronized int refreshAssociations() throws Exception {
        if (mClosed || mInputManager == null
                || mAddPortAssociation == null || mDisplayPort < 0) {
            return 0;
        }
        int added = 0;
        for (final ConsoleKeyboardDevice keyboard
                : ConsoleInputDeviceDiscovery.findRoutableKeyboards()) {
            if (associatePort(keyboard.location)) {
                mKeyboardAssociationCount++;
                added++;
            }
        }
        for (final ConsoleMouseDevice mouse
                : ConsoleInputDeviceDiscovery.findRoutableMice()) {
            if (associatePort(mouse.location)) {
                added++;
            }
        }
        if (added > 0) {
            ConsoleInputRoutingOwnership.record(mAssociatedInputPorts);
        }
        return added;
    }

    private boolean associatePort(final String location)
            throws ReflectiveOperationException {
        if (location == null || location.isEmpty()
                || !mAssociatedInputPorts.add(location)) {
            return false;
        }
        try {
            mAddPortAssociation.invoke(mInputManager, location, mDisplayPort);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            mAssociatedInputPorts.remove(location);
            throw error;
        }
    }

    private static void addRequestedPort(
            final Set<String> ports, final String location) {
        if (location != null && !location.isEmpty()) {
            ports.add(location);
        }
    }

    private static ExternalDisplayTarget findExternalDisplayTarget()
            throws Exception {
        final Object displayManager = getService(
                "display", "android.hardware.display.IDisplayManager");
        final Class<?> displayManagerInterface =
                Class.forName("android.hardware.display.IDisplayManager");
        final Method getDisplayIds = displayManagerInterface.getMethod(
                "getDisplayIds", boolean.class);
        final Method getDisplayInfo = displayManagerInterface.getMethod(
                "getDisplayInfo", int.class);
        final int[] displayIds =
                (int[]) getDisplayIds.invoke(displayManager, true);
        ExternalDisplayTarget best = null;
        for (final int displayId : displayIds) {
            final Object info = getDisplayInfo.invoke(displayManager, displayId);
            if (info == null || getIntField(info, "type") != DISPLAY_TYPE_EXTERNAL) {
                continue;
            }
            final Object address = getField(info, "address");
            if (address == null) {
                continue;
            }
            try {
                final Object port = address.getClass()
                        .getMethod("getPort").invoke(address);
                if (port instanceof Number) {
                    final ExternalDisplayTarget candidate =
                            new ExternalDisplayTarget(
                                    displayId, ((Number) port).intValue());
                    if (best == null || candidate.displayId < best.displayId) {
                        best = candidate;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // A virtual/non-physical display does not expose a port.
            }
        }
        if (best == null) {
            throw new IOException("no physical external display address found");
        }
        return best;
    }

    @Override
    public synchronized void close() {
        if (mClosed) {
            return;
        }
        mClosed = true;
        boolean associationsRemoved = mAssociatedInputPorts.isEmpty();
        if (mRemovePortAssociation != null && mInputManager != null) {
            try {
                removeAssociations(
                        mInputManager,
                        mRemovePortAssociation,
                        mAssociatedInputPorts);
                associationsRemoved = true;
            } catch (ReflectiveOperationException | RuntimeException error) {
                System.err.println(
                        "LINEDEX_INPUT_ROUTING_CLEANUP ports=" + error);
            }
        }
        if (associationsRemoved) {
            try {
                ConsoleInputRoutingOwnership.clear();
            } catch (IOException error) {
                System.err.println(
                        "LINEDEX_INPUT_ROUTING_CLEANUP ownership=" + error);
            }
        }
        mAssociatedInputPorts.clear();
        mConsoleDisplayId = -1;
        mDisplayPort = -1;
        mKeyboardAssociationCount = 0;
    }

    private static void removeAssociations(
            final Object inputManager,
            final Method removePortAssociation,
            final Set<String> inputPorts)
            throws ReflectiveOperationException {
        for (final String inputPort : inputPorts) {
            removePortAssociation.invoke(inputManager, inputPort);
        }
    }

    private static String readInputDump()
            throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(DUMPSYS, "input")
                .redirectErrorStream(true)
                .start();
        final StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(
                    "dumpsys input failed with exit code " + exitCode);
        }
        return output.toString();
    }

    private static Object getService(
            final String name,
            final String interfaceName) throws Exception {
        final Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        final Object binder = serviceManager
                .getMethod("getService", String.class)
                .invoke(null, name);
        final Class<?> stub = Class.forName(interfaceName + "$Stub");
        return stub.getMethod("asInterface", IBinder.class)
                .invoke(null, binder);
    }

    private static Object getField(
            final Object target,
            final String fieldName) throws ReflectiveOperationException {
        final Field field = target.getClass().getField(fieldName);
        return field.get(target);
    }

    private static int getIntField(
            final Object target,
            final String fieldName) throws ReflectiveOperationException {
        final Field field = target.getClass().getField(fieldName);
        return field.getInt(target);
    }

    private static final class ExternalDisplayTarget {
        final int displayId;
        final int port;

        ExternalDisplayTarget(final int displayId, final int port) {
            this.displayId = displayId;
            this.port = port;
        }
    }
}
