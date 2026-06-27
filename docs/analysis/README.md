# Technical Analysis

Documentation of architectural decisions and technical investigations.

## Completed Analysis

- **Automotive Removal**: Decision to opt-out of Android Auto to avoid "Category not permitted" policy rejections due to the use of `SYSTEM_ALERT_WINDOW`.
- **Multi-Module Sync**: Analysis of Wear OS to Mobile/TV communication via `Wearable.MessageClient`.

## Ongoing Investigations

- **Localization Complexity**: Assessing the impact of RTL (Right-to-Left) languages on existing Jetpack Compose layouts.
- **TV Update Strategy**: How to prompt TV users for updates without being intrusive.
