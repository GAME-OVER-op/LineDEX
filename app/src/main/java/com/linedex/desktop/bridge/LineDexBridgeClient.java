package com.linedex.desktop.bridge;

import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Process-local holder for the privileged bridge published by system_server. */
public final class LineDexBridgeClient {
    public interface Listener {
        void onBridgeChanged();
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile ILineDexSystemBridge sBridge;
    private static volatile int sVersion;
    private static volatile String sFingerprint = "";

    private LineDexBridgeClient() {
    }

    public static ILineDexSystemBridge bridge() {
        return sBridge;
    }

    public static boolean isConnected() {
        final ILineDexSystemBridge bridge = sBridge;
        if (bridge == null) {
            return false;
        }
        try {
            return bridge.asBinder().isBinderAlive() && bridge.isHookActive();
        } catch (RemoteException | RuntimeException ignored) {
            clearBridge(bridge.asBinder());
            return false;
        }
    }

    public static int interfaceVersion() {
        return sVersion;
    }

    public static String frameworkFingerprint() {
        return sFingerprint;
    }

    public static Bundle state() {
        final ILineDexSystemBridge bridge = sBridge;
        if (bridge == null) {
            return Bundle.EMPTY;
        }
        try {
            final Bundle state = bridge.getState();
            return state == null ? Bundle.EMPTY : state;
        } catch (RemoteException | RuntimeException ignored) {
            clearBridge(bridge.asBinder());
            return Bundle.EMPTY;
        }
    }

    public static void addListener(final Listener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(final Listener listener) {
        LISTENERS.remove(listener);
    }

    static synchronized void publish(
            final ILineDexSystemBridge bridge,
            final int version,
            final String fingerprint) {
        final ILineDexSystemBridge previous = sBridge;
        sBridge = bridge;
        sVersion = version;
        sFingerprint = fingerprint == null ? "" : fingerprint;
        try {
            bridge.asBinder().linkToDeath(
                    () -> clearBridge(bridge.asBinder()), 0);
        } catch (RemoteException error) {
            sBridge = null;
            sVersion = 0;
            sFingerprint = "";
        }
        notifyListeners();
    }

    private static synchronized void clearBridge(final IBinder binder) {
        if (sBridge == null || sBridge.asBinder() != binder) {
            return;
        }
        sBridge = null;
        sVersion = 0;
        sFingerprint = "";
        notifyListeners();
    }

    private static void notifyListeners() {
        for (final Listener listener : LISTENERS) {
            listener.onBridgeChanged();
        }
    }
}
