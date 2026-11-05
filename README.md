# SOS Overlay

A minimal standalone Android app (`com.sos.overlay`): a floating **SOS / Fuel / Food** button that
stays on top of Google Maps (or any app) and shares its location with the riders in your group —
**offline over Bluetooth/Wi-Fi** and **online over the internet**.

## Features
- Floating red button on top of other apps (`SYSTEM_ALERT_WINDOW`), draggable.
- Tap → **SOS** (shares your live location), **Fuel** or **Food** (reminder only — no location popup).
- **Rider codes**: every phone has a unique code (e.g. `M6Z32U`); add a rider by entering their code
  (Copy / Share buttons included). Riders in the same group also **appear automatically on both
  phones** via a presence heartbeat, with their names.
- Every popup also raises a **system notification** and **auto-closes after 5 seconds**.
- Two-way: any rider in the group can send SOS / Fuel / Food and everyone receives it.
- Alert card when a rider sends an SOS: sender, distance, **Open in Google Maps**, **Share location**, **Close**.
- Reminder card for Fuel / Food: just the reminder text — no coordinates, no map buttons.
- **Rider group** config: your name, group code, rider list, and the two sharing channels.
- **Offline**: Google Nearby Connections (Bluetooth/Wi-Fi, no mobile data, a few hundred metres).
- **Online**: Firebase Realtime Database (existing `ridepack-3d413` project, anonymous auth,
  push-based event stream — measured ~300 ms).
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
- Online mode uses the ridepack-3d413 Firebase Realtime Database; the group code selects the path
  `rides/<group>/sos`, and access uses Firebase anonymous auth (already enabled on the project).
