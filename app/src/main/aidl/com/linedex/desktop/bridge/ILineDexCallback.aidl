package com.linedex.desktop.bridge;

interface ILineDexCallback {
    void onStateChanged();
    void onExternalDisplayChanged(int displayId);
    void onPointerDisplayChanged(int displayId);
    void onError(String code, String message);
}
