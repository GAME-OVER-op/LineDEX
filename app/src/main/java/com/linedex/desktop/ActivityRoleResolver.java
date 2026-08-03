package com.linedex.desktop;

final class ActivityRoleResolver {
    private ActivityRoleResolver() {
    }

    static boolean opensPhoneControl(final int currentDisplayId) {
        return currentDisplayId == 0;
    }
}
