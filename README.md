# SOS Overlay — standalone Android app

A separate application (package `com.sos.overlay`, app name **SOS Overlay**), independent of the
RidePack app (`com.ridepack.app`). Same feature set at its core: live map, ride-code pairing,
rider tracking over Bluetooth/Wi-Fi (Google Nearby), regroup alerts, and SOS with live rider
positions on the map.

## Identity
- App name: **SOS Overlay**
- Application ID: `com.sos.overlay`
- Launcher logo: red SOS disc with a white bolt (`res/drawable/ic_sos_*`, adaptive icon
  `res/mipmap-anydpi-v26/ic_launcher.xml`)

## Features
- Offline map areas (MapLibre), live GPS, GPX route import with turn-by-turn.
- Places along the ride (fuel / food / viewpoint / repair / stay).
- Ride-code pairing (create/join), rider tagging by ID.
- Phone-to-phone tracking over Bluetooth/Wi-Fi with no mobile data.
- Regroup alerts when a rider is more than 1.5 km away.
- **SOS**: red 🆘 button on the map sends your GPS fix to the ride group; every rider sees a red
  `🚨 SOS · <name>` marker, an alert card with distance, **Locate**, **Open in Google Maps**,
  and **Resolve** actions.
- Foreground service so tracking continues with the screen off.

## Build
Open the folder in Android Studio, or from a terminal:

    gradle wrapper --gradle-version 8.7
    ./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk

This project was built with JDK 17 + Android SDK 34 (`local.properties` points at the SDK).

## Test on phones (USB debugging on)
    adb devices
    ANDROID_SERIAL=<serialA> ./adb-check.sh
    ANDROID_SERIAL=<serialB> ./adb-check.sh

Phone A: Pair tab -> Create ride. Phone B: enter the code -> Join.
Pair tab should show "1 radio link(s)".

## Notes
- Needs Google Play services (Nearby, location). Radio range is a few hundred metres; no multi-hop relay yet.
- Anyone with the ride code can join; positions are not encrypted yet.
