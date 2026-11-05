#!/usr/bin/env bash
# Usage: ANDROID_SERIAL=<serial> ./adb-check.sh [apk]
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"; PKG=com.sos.overlay
adb devices -l | grep -w device >/dev/null || { echo "No authorised device. Enable USB debugging and accept the prompt."; exit 1; }
adb install -r "$APK" || exit 1
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do adb shell pm grant $PKG android.permission.$p 2>/dev/null; done
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow 2>/dev/null
adb logcat -c; adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 5
adb exec-out screencap -p > sos-overlay-screen.png
adb logcat -d --pid="$(adb shell pidof $PKG | tr -d '\r')" > sos-overlay-log.txt
adb logcat -d -s AndroidRuntime:E >> sos-overlay-log.txt
echo "Saved sos-overlay-screen.png and sos-overlay-log.txt"
