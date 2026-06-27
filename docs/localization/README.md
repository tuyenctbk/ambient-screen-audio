# Localization Strategy

AetherScreen aims to support 15+ high-impact languages to reach a global audience across Mobile, TV, and Wear OS.

## Current Support (5)

- [x] English (en) - Default
- [x] Spanish (es)
- [x] French (fr)
- [x] Vietnamese (vi)
- [x] Chinese (zh)

## Target Languages (Planned: +10)

1. [ ] German (de)
2. [ ] Portuguese (pt)
3. [ ] Japanese (ja)
4. [ ] Korean (ko)
5. [ ] Russian (ru)
6. [ ] Italian (it)
7. [ ] Hindi (hi)
8. [ ] Arabic (ar) - RTL Support required
9. [ ] Turkish (tr)
10. [ ] Indonesian (id)

## Implementation Workflow

1. **Extraction**: Ensure all strings are in `values/strings.xml` (no hardcoded strings).
2. **Translation**: Use AI or professional services for translation.
3. **Verification**: Verify layout on TV and Wear OS (smaller screens/constrained space).
4. **RTL Support**: Test UI mirroring for Arabic and other RTL languages.
