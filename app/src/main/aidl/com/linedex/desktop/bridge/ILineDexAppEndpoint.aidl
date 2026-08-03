package com.linedex.desktop.bridge;

import com.linedex.desktop.bridge.ILineDexSystemBridge;

interface ILineDexAppEndpoint {
    void publishSystemBridge(
            ILineDexSystemBridge bridge,
            int interfaceVersion,
            String frameworkFingerprint);
}
