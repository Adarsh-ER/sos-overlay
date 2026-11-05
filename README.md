# SOS Overlay

A minimal standalone Android app (`com.sos.overlay`): a floating **SOS / Fuel / Food** button that
stays on top of Google Maps (or any app) and shares its location with the riders in your group —
**offline over Bluetooth/Wi-Fi** and **online over the internet**.

## Features
- Floating red button on top of other apps (`SYSTEM_ALERT_WINDOW`), draggable.
- Tap → **SOS**, **Fuel** or **Food** → captures GPS → shares with the group.
- Alert card when a rider sends something: sender name, distance, **Open in Google Maps**, **Share location**, **Close**.
- **Rider group** config: your name, group code, rider list, and the two sharing channels.
- **Offline**: Google Nearby Connections (Bluetooth/Wi-Fi, no mobile data, a few hundred metres).
- **Online**: internet via a public `ntfy.sh` topic derived from the group code (no account/server needed).
- Foreground service so the button and the group link stay alive while you navigate.

## Use
1. Open the app.
2. Allow **display over other apps** and **location** (plus Bluetooth/notifications on newer Android).
3. Set your **name** and the **group code** (the same code on every rider's phone).
4. Turn on **Offline** and/or **Online** sharing.
5. Tap **Turn on floating SOS** (restart it after changing group/channels).
6. Open Google Maps — the red button floats above it. Tap it → SOS / Fuel / Food.

## Build
```
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

## Install (sideload)
```
adb install -r app-debug.apk
```

## Notes
- Android 8.0+ (API 26+). Google Play services for location + Nearby.
- Online mode uses a public topic (`sosoverlay-<groupcode>`); anyone who knows the group code can
  read that topic, so use an uncommon code. Offline mode works with no internet at all.
