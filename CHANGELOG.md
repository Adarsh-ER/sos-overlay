# Changelog

All notable changes to SOS Overlay. Versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] — 2026-11-05 · First stable release

The stable baseline.

- Floating **SOS / Fuel / Food** button above Google Maps (or any app), draggable.
- **SOS** shares live location instantly (last known fix, refined when GPS arrives).
- **Fuel / Food** are reminder-only messages (no location popup).
- **Rider codes**: unique code per phone, add riders by code, Copy / Share.
- **Automatic presence**: riders in the same group appear on both phones.
- **Two-way** sharing, verified between two devices.
- **Offline** sharing over Bluetooth/Wi-Fi (Google Nearby).
- **Online** sharing over Firebase Realtime Database (~0.3 s delivery).
- System notification for every alert; popups auto-close (5 s alerts, 15 s menu).
- Foreground service keeps the button and link alive with the screen off.
- Navy/red shield launcher icon.

## [0.5.2] — 2026-11-05

- SOS sent immediately instead of waiting for a fresh GPS fix.
- Action menu stays open 15 s (alerts still 5 s).
- Link line shows both channels: `Nearby: … · Online: …`.

## [0.5.1] — 2026-11-05

- SOS no longer fails silently when there is no GPS fix.
- Android 12+ Bluetooth/Nearby permissions auto-requested; Nearby errors visible.
- Firebase sends fetch an auth token on demand.

## [0.5.0] — 2026-11-05

- Online channel moved from ntfy.sh to Firebase Realtime Database (push stream, ~300 ms).
- Anonymous auth against the existing `ridepack-3d413` project.

## [0.4.2] — 2026-11-05

- New vector launcher icon (navy tile, red shield, white bolt).

## [0.4.1] — 2026-11-05

- Presence heartbeat: riders in the group appear automatically on all phones.
- Remove blocks a rider from being auto re-added; add-by-code unblocks.

## [0.4.0] — 2026-11-05

- Rider codes per phone, with Copy / Share.
- Add rider by code; names fill in when the rider sends something.

## [0.3.2] — 2026-11-05

- Overlay popups capped to a compact card, so they no longer cover the screen in landscape.

## [0.3.1] — 2026-11-05

- Fuel / Food changed to reminder-only (no location popup).
- System notification for every alert; popups auto-close after 5 s.
- Two-way sharing confirmed for all riders in the group.

## [0.3.0] — 2026-11-05

- Rider group: name, group code, rider list, offline/online toggles.
- Offline sharing over Bluetooth/Wi-Fi.
- Online sharing over the internet.
- SOS / Fuel / Food options.

## [0.2.0] — 2026-11-05

- Replaced the initial RidePack-derived build with a minimal SOS-only overlay app.
- Floating SOS button, location capture, Open in Google Maps / Share.

[1.0.0]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v1.0.0
[0.5.2]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.5.2
[0.5.1]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.5.1
[0.5.0]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.5.0
[0.4.2]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.4.2
[0.4.1]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.4.1
[0.4.0]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.4.0
[0.3.2]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.3.2
[0.3.1]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.3.1
[0.3.0]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.3.0
[0.2.0]: https://github.com/Adarsh-ER/sos-overlay/releases/tag/v0.2.0
