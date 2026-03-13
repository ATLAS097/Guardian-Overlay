# Guardian Overlay Prototype (Offline Scam Checker)

This prototype is a real Android app that does the following:
- Reads visible on-screen text using Accessibility Service.
- Runs a local fake mini TFLite-compatible detector (keyword phrase-pack scoring).
- Shows an on-screen warning overlay when scam risk is high.
- Supports screenshot OCR fallback by selecting an image from the phone.
- Stores last 20 detection results locally and shows them in a History screen.
- Works offline for detection logic.

## 1) Install Required Software

1. Install Android Studio (latest stable) from the official Android developer website.
2. Open Android Studio and finish first-time setup.
3. Install SDK components when prompted:
   - Android SDK Platform 34
   - Android SDK Build-Tools
   - Android Emulator (optional)
4. On Windows, also install USB driver for your Android phone (OEM driver) if needed.

## 2) Open This Project in Android Studio

1. Open Android Studio.
2. Click **Open**.
3. Select this folder: `Guardian-Overlay`.
4. Wait for indexing and Gradle sync.

If Android Studio asks to create missing Gradle wrapper files, approve it.

## 3) Set Up Phone for Real Device Testing

1. On your Android phone:
   - Open **Settings -> About phone**
   - Tap **Build number** 7 times to enable Developer Options
2. Go to **Settings -> Developer options**:
   - Turn on **USB debugging**
3. Connect phone to PC with USB cable.
4. Accept the debugging authorization popup on phone.

## 4) Build and Run the App

1. In Android Studio top bar, choose the `app` run configuration.
2. Select your connected phone as deployment target.
3. Click Run (green triangle).
4. Wait until app is installed and launched.

## 5) First-Time In-App Setup

1. In the app, press **Open Accessibility Settings**.
2. Find **Guardian Accessibility** service.
3. Enable it and confirm warning dialogs.
4. Return to app.

Now the service monitors visible text from active screens.

## 6) How to Test the Prototype

### A. Accessibility Overlay Test
1. Keep Guardian app installed and Accessibility service enabled.
2. Open a chat or SMS screen containing scam-like text, for example:
   - "URGENT your bank account will be suspended. Click http://... now"
3. The service scans visible text and if high-risk, a warning overlay appears.

### B. Screenshot OCR Fallback Test
1. In Guardian app, tap **Pick Screenshot and Run OCR**.
2. Select a screenshot image containing message text.
3. App runs on-device OCR and shows:
   - Verdict (SCAM / NOT SCAM)
   - Risk score
   - Reason tags
   - Text snippet

## 7) Important Prototype Notes

- This version uses a fake mini TFLite-compatible detector (prototype). It mimics model I/O flow and can be swapped with real TFLite later.
- No cloud inference is used.
- Screenshot OCR is user-selected image fallback in this prototype.
- You can replace detector later with TFLite model while keeping same app structure.

## 8) Phrase Pack Customization (English, Singlish, Bahasa slang, Taglish)

Edit this file to add or tune scam phrases and weights:
- `app/src/main/assets/risk_phrases.json`

How it works:
1. Each rule has `reason`, `weight`, and `keywords`.
2. If extracted text contains a keyword, detector adds the rule weight.
3. If final score exceeds threshold, verdict is SCAM.

You can test quickly by adding a unique phrase and using it in a screenshot.

## 9) Project Structure

- `app/src/main/kotlin/com/guardian/overlay/MainActivity.kt`
- `app/src/main/kotlin/com/guardian/overlay/HistoryActivity.kt`
- `app/src/main/kotlin/com/guardian/overlay/service/GuardianAccessibilityService.kt`
- `app/src/main/kotlin/com/guardian/overlay/overlay/OverlayManager.kt`
- `app/src/main/kotlin/com/guardian/overlay/ocr/OcrProcessor.kt`
- `app/src/main/kotlin/com/guardian/overlay/detection/FakeMiniTfliteDetector.kt`
- `app/src/main/kotlin/com/guardian/overlay/detection/DetectorProvider.kt`
- `app/src/main/kotlin/com/guardian/overlay/data/DetectionHistoryStore.kt`
- `app/src/main/kotlin/com/guardian/overlay/processing/TextNormalizer.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_history.xml`
- `app/src/main/res/layout/overlay_warning.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/assets/risk_phrases.json`

## 10) Common Issues and Fixes

### App runs but no overlay appears
- Confirm Accessibility service is enabled.
- Ensure tested screen has enough text content.
- Use obvious scam phrases to trigger threshold.

### History screen is empty
- Run at least one screenshot OCR scan or let accessibility scan a screen.
- Then tap **Open Detection History**.

### OCR returns empty text
- Use clear screenshot with readable text.
- Avoid blurred screenshots.

### Build fails on first import
- Let Android Studio finish Gradle sync and dependency downloads.
- Update SDK Platform and Build Tools from SDK Manager.

## 11) Next Upgrade (After Prototype)

1. Replace fake detector internals in `FakeMiniTfliteDetector` with real TFLite `Interpreter.run`.
2. Add confidence calibration and false-positive controls.
3. Add multi-label risk indicators and richer explanation engine.
4. Add local encrypted event logs and optional export.
