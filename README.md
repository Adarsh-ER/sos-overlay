# SOS Overlay

A minimal standalone Android app (package `com.sos.overlay`) whose **only job** is an SOS button
that floats above Google Maps (and any other app).

## What it does
- Draws a draggable red **SOS** button on top of other apps using a system overlay
  (`SYSTEM_ALERT_WINDOW`).
- Tap the button: it captures your GPS location and shows a card with
  **Open in Google Maps** and **Share location** (Google Maps link).
- Runs in a foreground service, so the overlay stays alive while you navigate.
- No maps SDK, no API key, no RidePack code, no rider pairing — just the SOS overlay.

## Use
1. Open the app.
2. Allow **display over other apps**.
3. Allow **location** (and notifications on Android 13+).
4. Tap **Turn on floating SOS**.
5. Open Google Maps — the red SOS button floats above it. Drag it anywhere. Tap it to act.

## Build
```
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

## Install (sideload)
```
adb install -r app-debug.apk
```

## Notes
- Android 8.0+ (API 26+).
- Google Play services is used only for the location fix.
