# AetherScreen: Ambient Screen & Audio Preservation

AetherScreen is a system utility application for Android designed to save battery power, prevent OLED/AMOLED screen burn-in, and facilitate passive media consumption. It allows users to dim or completely shut down the screen's display matrix (turning off black pixels completely on OLED screens) while strictly preserving background audio playback for apps like YouTube, Twitch, web browser players, and local media clients.

---

## 📱 Key Features

- **Dim Mode**: Translucent black overlay with adjustable opacity levels (10% to 95%). Clicks and gestures pass through, allowing the user to interact with the media app underneath.
- **Blackout Mode (Pocket Lock)**: A pure black overlay (`#000000`) that absorbs all touch events to prevent accidental clicks in pockets. Can be dismissed via custom gestures (Double-tap to wake or Shake device).
- **Auto-Pause Sleep Timer**: Slowly fades out display matrix and uses system media key signals to pause background audio when the timer runs out. Features easy presets (15, 30, 45, 60 mins).
- **Intelligent Triggers**: 
  - *Proximity Pocket Detection*: Auto-blackout when face down or placed inside a pocket.
  - *Shake to Wake*: Dismiss overlay instantly by shaking the device.
- **Per-App Custom Rules**: Automatically toggles floating triggers when target media applications enter the foreground.
- **Bedside Screensaver Clock (TV & Mobile)**: Shows an ultra-dim floating digital clock on the black screen. The clock's coordinates shift randomly every 60 seconds to completely prevent static pixel burn-in.
- **Wear OS smartwatch remote**: Remote-controls and toggles the screen overlays of paired phones and TVs directly from the wrist.

---

## 🏗️ Multi-Module Architecture

AetherScreen is organized as a clean, multi-module Android project:

- **`:core`**: Shared logic, DataStore config storage (`PreferencesManager`), and media playback key control.
- **`:mobile`**: Main phone/tablet app, overlay managers, sensor listeners, Quick Settings tile toggles, and Wear OS listener service.
- **`:tv`**: Leanback-compliant Android TV app, TV overlay screensaver, and TV remote key interceptor (remote buttons wake TV).
- **`:wear`**: Wear OS smartwatch remote-control companion app.

---

## 📈 Monetization & Production-Grade Features

1. **AdMob Advertising placeholders**: A standard test banner ad is integrated at the bottom of the main mobile dashboard, ready for monetization setup.
2. **Google Play In-App Review prompts**: Users can rate and leave feedback directly via a dedicated "Rate AetherScreen" button, which utilizes the Google Play In-App Review API (with auto-fallback to Store Link intents).
3. **Firebase Remote Config Updates**: Dynamic update checks are performed automatically. If a new version is published, a banner notification appears at the top of the mobile screen prompting the user to update.

---

## 🛠️ How to Build & Run

### Prerequisites
- JDK 17 or JDK 21+ (OpenJDK 23 recommended).
- Android SDK installed (API 37 platforms, build-tools).

### Build Instructions
Run the following build command in the root folder to generate debug APKs and library archives for all modules:

```bash
./gradlew assembleDebug
```

### Generated Artifacts
- **Mobile app**: `mobile/build/outputs/apk/debug/mobile-debug.apk`
- **TV app**: `tv/build/outputs/apk/debug/tv-debug.apk`
- **Wear OS app**: `wear/build/outputs/apk/debug/wear-debug.apk`
- **Shared library**: `core/build/outputs/aar/core-debug.aar`
