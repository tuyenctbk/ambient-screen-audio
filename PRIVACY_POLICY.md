# Privacy Policy for AetherScreen

Last updated: June 22, 2026

At AetherScreen, we value your privacy. This Privacy Policy details how we handle data when you use the AetherScreen mobile, Android TV, and Wear OS applications (collectively, the "Service").

---

## 1. Direct Data Collection & Privacy-First Policy
AetherScreen is designed as an on-device utility app. 

- **No Personal Data Collection**: We do not collect, store, transmit, or share any personally identifiable information (PII) such as names, email addresses, phone numbers, location data, or device identifiers.
- **Local Settings Processing**: All application configurations, including custom timer rules, target media application lists, and overlay dimming settings, are saved and processed locally on your device via Android Jetpack DataStore.

---

## 2. Permissions and Sensor Usage
To provide system-level screen optimization, AetherScreen requests the following Android permissions, which are processed strictly locally:

- **System Alert Window (Draw Over Other Apps)**: Used to display a black or translucent overlay window on top of other running applications. This is handled strictly by the local Android `WindowManager` API.
- **Foreground Service & Special Use**: Used to keep the overlay active and manage the sleep timer countdown when the app is in the background. Renders a persistent notification for user control.
- **Usage Statistics Access (PACKAGE_USAGE_STATS)**: Used locally on-device to check which media application is currently in the foreground to apply per-app dimming rules. This data is never logged or sent to any server.
- **Proximity Sensor**: Monitored locally to automatically trigger blackout mode when the device is face down or placed inside a pocket.
- **Accelerometer Sensor**: Processed locally to detect shake gestures to wake up and dismiss the overlay.
- **Vibration Access**: Used locally to trigger haptic feedback during wake-up gestures.

---

## 3. Third-Party Services
For application monitoring and monetization, we integrate Google Play Services, Firebase, and AdMob:

- **Firebase Crashlytics**: Collects anonymous crash logs and stack traces to help us fix bugs. This data contains no PII.
- **Firebase Remote Config**: Queries configurations (such as the latest version number) to suggest updates.
- **Google Mobile Ads (AdMob)**: Displays advertising banners. AdMob may collect device metadata to serve ads, complying with Google Play Ad Policies.
- **Google Play In-App Review**: Facilitates app rating requests locally using Google Play API.

---

## 4. Contact Us
If you have any questions or feedback regarding this Privacy Policy, please contact us directly via our GitHub repository issues page.
