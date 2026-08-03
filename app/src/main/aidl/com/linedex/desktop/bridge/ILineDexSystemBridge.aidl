package com.linedex.desktop.bridge;

import android.os.Bundle;
import com.linedex.desktop.bridge.ILineDexCallback;

interface ILineDexSystemBridge {
    int getBridgeVersion();
    String getFrameworkFingerprint();
    boolean isHookActive();

    void setDesktopSessionEnabled(boolean enabled);
    boolean isDesktopSessionEnabled();

    int getExternalDisplayId();
    void setPointerDisplayId(int displayId);
    int getPointerDisplayId();

    boolean applyPreferredDisplayMode(
            int displayId, int width, int height, float refreshRate);
    boolean setPhoneDisplayEnabled(boolean enabled);

    Bundle getState();
    void registerCallback(ILineDexCallback callback);
    void unregisterCallback(ILineDexCallback callback);
}
