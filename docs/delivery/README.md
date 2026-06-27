# Delivery Documentation

This document tracks the release history and deployment details for AetherScreen.

## Release History

### v1.0.5 (Build 208) - Current
- **Date**: 2024-05-22
- **Summary**: Version bump for new submission.
- **Changes**:
    - Increment versionCode to 208.
- **Artifacts**: 
    - `mobile-release.aab`
    - `tv-release.aab`
    - `wear-release.aab`

### v1.0.1 (Build 207)
- **Date**: 2024-05-22
- **Summary**: Compliance and Clean-up Release.
- **Changes**:
    - Removed all Android Auto and Automotive OS metadata to comply with Play Store policies.
    - Standardized versioning across all modules (Mobile, TV, Wear).
    - Initialized comprehensive documentation system.
    - Established localization roadmap for 15+ languages.
- **Artifacts**: 
    - `mobile-release.aab`
    - `tv-release.aab`
    - `wear-release.aab`

### v1.0.0 (Build 206)
- **Date**: 2024-05-20
- **Summary**: Initial Public Release.
- **Changes**:
    - Core blackout and dimming engine.
    - Intelligent triggers (Pocket mode, Shake to wake).
    - Multi-device sync (Wear OS remote control).
    - Support for English, Spanish, French, Vietnamese, and Chinese.

## Deployment Checklist

- [x] Version code incremented.
- [x] Proguard/R8 rules verified.
- [x] Android Auto metadata removed.
- [x] Documentation updated.
- [x] Signed AABs generated.
