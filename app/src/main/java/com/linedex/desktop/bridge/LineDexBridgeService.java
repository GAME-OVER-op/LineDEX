package com.linedex.desktop.bridge;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.linedex.desktop.LineDexPreferences;
import com.linedex.desktop.LineDexSessionCoordinator;

/**
 * Endpoint bound explicitly by the LSPosed code running inside system_server.
 * The system bridge Binder is delivered back through this endpoint.
 */
public final class LineDexBridgeService extends Service {
    private static final String TAG = "LineDexBridgeService";
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private volatile ILineDexSystemBridge mSystemBridge;

    private final ILineDexCallback mCallback = new ILineDexCallback.Stub() {
        @Override
        public void onStateChanged() {
            scheduleCurrentState();
        }

        @Override
        public void onExternalDisplayChanged(final int displayId) {
            if (displayId > 0) {
                mMain.post(() -> LineDexSessionCoordinator
                        .startOnExternalDisplay(LineDexBridgeService.this, displayId));
            } else {
                mMain.post(() -> LineDexSessionCoordinator
                        .stop(LineDexBridgeService.this));
            }
        }

        @Override
        public void onPointerDisplayChanged(final int displayId) {
            // The control panel reads pointer state from the bridge bundle.
        }

        @Override
        public void onError(final String code, final String message) {
            Log.w(TAG, code + ": " + message);
        }
    };

    private final ILineDexAppEndpoint.Stub mEndpoint =
            new ILineDexAppEndpoint.Stub() {
                @Override
                public void publishSystemBridge(
                        final ILineDexSystemBridge bridge,
                        final int interfaceVersion,
                        final String frameworkFingerprint)
                        throws RemoteException {
                    enforceSystemCaller();
                    if (bridge == null) {
                        throw new RemoteException("system bridge is null");
                    }
                    final ILineDexSystemBridge previous = mSystemBridge;
                    if (previous != null) {
                        try {
                            previous.unregisterCallback(mCallback);
                        } catch (RemoteException ignored) {
                            // A replaced bridge may already be dead.
                        }
                    }
                    mSystemBridge = bridge;
                    LineDexBridgeClient.publish(
                            bridge, interfaceVersion, frameworkFingerprint);
                    try {
                        bridge.registerCallback(mCallback);
                        bridge.setDesktopSessionEnabled(
                                LineDexPreferences.isDesktopEnabled(
                                        LineDexBridgeService.this));
                        bridge.setPointerDisplayId(
                                LineDexPreferences.pointerTarget(
                                        LineDexBridgeService.this));
                        scheduleCurrentState();
                    } catch (RemoteException error) {
                        Log.w(TAG, "Cannot restore LineDEX bridge state", error);
                        throw error;
                    }
                }
            };

    @Override
    public IBinder onBind(final Intent intent) {
        final int caller = Binder.getCallingUid();
        if (caller != Process.SYSTEM_UID && caller != Process.myUid()) {
            Log.w(TAG, "Rejected bridge bind from uid=" + caller);
            return null;
        }
        return mEndpoint;
    }

    @Override
    public void onDestroy() {
        final ILineDexSystemBridge bridge = mSystemBridge;
        mSystemBridge = null;
        if (bridge != null) {
            try {
                bridge.unregisterCallback(mCallback);
            } catch (RemoteException ignored) {
                // system_server may be restarting.
            }
        }
        super.onDestroy();
    }

    private void scheduleCurrentState() {
        final ILineDexSystemBridge bridge = mSystemBridge;
        if (bridge == null) {
            return;
        }
        mMain.post(() -> {
            try {
                if (!bridge.isDesktopSessionEnabled()) {
                    LineDexSessionCoordinator.stop(LineDexBridgeService.this);
                    return;
                }
                final int displayId = bridge.getExternalDisplayId();
                if (displayId > 0) {
                    LineDexSessionCoordinator.startOnExternalDisplay(
                            LineDexBridgeService.this, displayId);
                }
            } catch (RemoteException error) {
                Log.w(TAG, "Could not synchronize desktop state", error);
            }
        });
    }

    private static void enforceSystemCaller() {
        final int caller = Binder.getCallingUid();
        if (caller != Process.SYSTEM_UID) {
            throw new SecurityException(
                    "LineDEX bridge accepts system_server only; uid=" + caller);
        }
    }
}
