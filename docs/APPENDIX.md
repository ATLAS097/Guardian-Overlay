# Guardian Overlay Appendix

## Architecture Overview

### Core Runtime Components

- Accessibility orchestrator: `GuardianAccessibilityService`
- Overlay rendering/state: `OverlayManager`
- OCR processing: `OcrProcessor`
- Heuristic detector pipeline: detector provider + phrase/rule scoring

### UI Structure

- Single activity host: `MainActivity`
- Fragment tabs:
  - Home
  - QR Scanner
  - Gallery
  - Trusted Contact
  - Settings

Navigation resources:

- `app/src/main/res/menu/bottom_nav_menu.xml`
- `app/src/main/res/navigation/nav_graph.xml`

## Detection and Safety Rules

- Live detection runs only when detection is enabled.
- Self-app package (`com.guardian.overlay`) is excluded from detection checks.
- Manual scan flow can still show and hold results even if live detection is disabled.
- Trusted contact CTA appears only when enabled and properly configured.

## Assistive UX Notes

- Bubble, panel, and remove target use light/dark semantic tokens.
- Panel uses explicit visual ON/OFF state cue for detection.
- Panel and key cards use subtle staged entry animations for improved polish.

## Settings Behavior

Settings are immediate-apply:

- No explicit Save action.
- Preference writes occur on user interaction.
- Accessibility-gated toggles are reverted if permission is not available.
- Theme mode changes apply immediately.

## Trusted Contact Configuration

Trusted Contact is configured on its own tab.

Fields:

- Enable/disable toggle
- Name (optional)
- Number (required for enabled state)
- Action mode: Chooser / SMS / Call / Share

## Build and Validation

Primary command:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

Optional focused compile:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
```

## Known Build Environment Note

On some Java versions, Kotlin daemon warnings may appear (`IllegalArgumentException: 25.0.1`).
Build can still succeed via fallback compilation.
