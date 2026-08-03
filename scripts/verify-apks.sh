#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
    printf 'Usage: %s LINEDEX_APK\n' "$0" >&2
    exit 2
fi

apk=$1
if [ ! -f "$apk" ]; then
    printf 'Missing APK: %s\n' "$apk" >&2
    exit 1
fi

contents=$(unzip -Z1 "$apk")
for helper in \
    lib/arm64-v8a/libmagicdesk_uinput_bridge.so \
    lib/arm64-v8a/libmagicdesk_keyboard_bridge.so
do
    printf '%s\n' "$contents" | grep -qx "$helper" || {
        printf 'LineDEX APK is missing %s\n' "$helper" >&2
        exit 1
    }
done

printf '%s\n' "$contents" | grep -qx 'assets/xposed_init' || {
    printf 'LineDEX APK is missing assets/xposed_init\n' >&2
    exit 1
}
printf '%s\n' "$contents" | grep -qx 'META-INF/xposed/scope.list' || {
    printf 'LineDEX APK is missing the static Xposed scope list\n' >&2
    exit 1
}
printf '%s\n' "$contents" | grep -qx 'META-INF/xposed/module.prop' || {
    printf 'LineDEX APK is missing Xposed module.prop\n' >&2
    exit 1
}

if printf '%s\n' "$contents" | grep -q '\.ko$'; then
    printf 'LineDEX APK must not contain kernel modules\n' >&2
    exit 1
fi

printf 'LineDEX APK contents verified.\n'
