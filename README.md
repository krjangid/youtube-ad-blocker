# Lightweight Android TV YouTube Adblock Browser

A super lightweight Android TV application (under 2MB) that opens YouTube in fullscreen with a native network adblocker and javascript injection skipper.

## How it Works
1. **System WebView:** Utilizes the pre-installed Google/Android WebView Chromium engine on your TV. No heavy rendering engine compiled inside.
2. **Network Filtering:** Overrides `shouldInterceptRequest` inside `WebViewClient` to instantly intercept and block Google Ads/Doubleclick requests.
3. **JS Auto-Skipper & Speedup:** Once YouTube loads, it injects an active observer script to instantly auto-click "Skip Ad" buttons and speed up unskippable ads at `16x` playback speed with auto-muting.
4. **TV Remote Navigation:** Configures the WebView for D-Pad focusability and maps the TV remote's `Back` button to history navigation.

## How to Build & Install

### Step 1: Build the APK
Open this folder (`tv-browser`) in Android Studio or compile it from the command line using Gradle:
```bash
./gradlew assembleRelease
```
The compiled APK will be located at:
`app/build/outputs/apk/release/app-release-unsigned.apk` (or signed if you configure signing configs).

### Step 2: Sideload onto Android TV
1. Copy the generated `.apk` file to a USB flash drive or upload it to Google Drive.
2. Install the **Downloader** or **File Commander** app on your Android TV from the Play Store.
3. Enable installation from **Unknown Sources** under your TV's security settings.
4. Install the APK and launch **"TV Adblock Youtube"** from your TV's app drawer!
