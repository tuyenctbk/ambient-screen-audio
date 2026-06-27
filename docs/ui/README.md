# UI Layout Documentation

This document describes the user interface structure for AetherScreen across Mobile, TV, and Wear OS.

## Mobile Application

The Mobile UI follows a "Glassmorphic" dark theme with a focus on ease of access to the main power toggle.

### Structure

```text
+------------------------------------------+
|            AetherScreen (Header)         |
+------------------------------------------+
| [!] Update Available Banner (Optional)   |
+------------------------------------------+
|                                          |
|                ( ( O ) )                 |
|            Power Toggle Button           |
|             (Pulsing Glow)               |
|                                          |
+------------------------------------------+
| [ Dim & Blackout Controls ]              |
| +--------------------------------------+ |
| | Blackout Mode Switch                 | |
| | [-----------|----------------------] | |
| | Opacity Slider (if Blackout is Off)  | |
| | Touch Block Switch                   | |
| +--------------------------------------+ |
+------------------------------------------+
| [ Sleep Timer Presets ]                  |
| [ OFF ] [ 15m ] [ 30m ] [ 45m ] [ 60m ]  |
+------------------------------------------+
| [ Intelligent Triggers ]                 |
| +--------------------------------------+ |
| | Pocket Mode Switch                   | |
| | Shake to Wake Switch                 | |
| | Double Tap to Wake Switch            | |
| +--------------------------------------+ |
+------------------------------------------+
| [ Target Applications ]                  |
| +--------------------------------------+ |
| | App 1 Name                  [X]      | |
| | App 2 Name                  [ ]      | |
| +--------------------------------------+ |
+------------------------------------------+
| [ (AdMob Banner) ]                       |
+------------------------------------------+
```

## Android TV

The TV UI is optimized for D-Pad navigation and high visibility from a distance.

### Structure

```text
+-------------------------------------------------------+
|                 AetherScreen TV                       |
+-------------------------------------------------------+
|                                                       |
|  +-----------------------+  +----------------------+  |
|  |     START/STOP        |  |  Sleep Timer         |  |
|  |     BLACKOUT          |  |  [OFF] [15m] [30m]   |  |
|  |                       |  +----------------------+  |
|  | (Status Indicator)     |  |  Ambient Clock       |  |
|  |                       |  |  [ ENABLED ]         |  |
|  +-----------------------+  +----------------------+  |
|                                                       |
+-------------------------------------------------------+
|  Tip: Press any key to wake                           |
+-------------------------------------------------------+
```

## Wear OS

The Wear OS UI is a simplified, scrollable column optimized for small circular screens.

### Structure

```text
+------------------+
|      (Title)     |
+------------------+
|   [ TOGGLE ]     |
+------------------+
| [ +15 MIN ]      |
+------------------+
| [ +30 MIN ]      |
+------------------+
| Status Text      |
+------------------+
```
