# Indoweb - Chromium-Based Android Browser with UserScript Support

<p align="center">
  <b>Modern Android Web Browser with Built-in UserScript Execution Engine, Built with Kotlin & Jetpack Compose</b>
</p>

---

## 🌟 Key Features

### 1. Chromium-Based Modern Engine
- Full HTML5 and modern web standard support via Android Chromium `WebView`.
- **JavaScript Enabled**: Fast execution of standard and dynamic scripts.
- **DOM & Web Storage**: LocalStorage, SessionStorage, IndexedDB, and Web SQL enabled.
- **Hardware Acceleration**: Smooth 60fps scrolling and GPU canvas rendering.
- **Mixed Content Support**: `MIXED_CONTENT_ALWAYS_ALLOW` for flexible testing and browsing.
- **Desktop Site Mode**: Fast 1-tap user-agent switcher.
- **Custom WebChromeClient**: Handles JS Alerts/Confirms/Prompts, progress bars, and file uploads.

### 2. UserScript Execution Engine (Tampermonkey Style)
- **Automatic Page-Load Injection**: Executes enabled custom scripts on `onPageFinished()` via `evaluateJavascript()`.
- **URL Pattern Matching**: Supports wildcard matching (`*`, `*://*.example.com/*`), domain filters, and regular expressions.
- **Local Persistence**: Powered by SQLite via **Room Database**, keeping your scripts secure and persistent across restarts.
- **Pre-installed Built-in Scripts**:
  - 🌙 **Smart Dark Mode Inverter**: High-contrast dark styling with image and video preservation.
  - 🛡️ **Ad Shield & Overlay Cleaner**: Removes floating banners, sticky marketing popups, and cookie consent overlays.
  - ⚡ **Floating Auto-Scroller**: Floating on-screen widget for hands-free reading and smooth jumping.
  - 📖 **Article Typography Enhancer**: Optimal font sizing and line height for distraction-free reading.
  - 🎬 **HTML5 Video Booster**: Double-tap on videos to cycle playback speed (1x, 1.25x, 1.5x, 2x).
- **Custom Script Manager & Editor**:
  - Add, edit, toggle, and delete scripts anytime.
  - Starter templates (DOM Modifier, Custom CSS, Auto-Clicker, Link Tracker Cleaner).
  - Quick JavaScript snippet shortcuts for mobile keyboards (`querySelector`, `addStyle`, `console.log`).
  - "Run Now" instant test button for on-the-fly script execution.
  - Live console logs showing execution status (PASS/FAIL) and return values.

### 3. Browser UI & Navigation
- **Top Navigation Bar**: Security badge (SSL / TLS status dialog), interactive URL input with instant Go action, active UserScripts count badge, and reload/stop button.
- **Bottom Navigation Bar**: Back, Forward, Home, UserScripts Manager, Bookmarks/History, and More options menu.
- **Find in Page**: Live in-page text search with next/previous matching.
- **Speed Dial Home**: Quick access to top sites (Google, GitHub, Wikipedia, Reddit, DuckDuckGo, Hacker News) with search engine switching (DuckDuckGo, Google, Bing).
- **Bookmarks & History Library**: Save favorites and access recent browsing history.

---

## 🏗️ Project Architecture

```
/
├── .github/workflows/
│   └── release.yml                 # Automated APK build & GitHub Release workflow
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml     # Permissions & activity declarations
│   │   ├── java/com/example/
│   │   │   ├── MainActivity.kt     # Main entry point & Intent handler
│   │   │   ├── data/
│   │   │   │   ├── model/          # UserScript, Bookmark, HistoryItem entities
│   │   │   │   ├── local/          # Room DB, UserScriptDao, BookmarkDao, HistoryDao
│   │   │   │   └── repository/     # BrowserRepository
│   │   │   └── ui/
│   │   │       ├── browser/        # ViewModel, WebViewClient, WebChromeClient, WebView
│   │   │       ├── components/     # TopUrlBar, BottomNavBar, SpeedDialHome, FindInPageBar
│   │   │       ├── scripts/        # UserScriptsScreen, ScriptEditorDialog, ScriptTemplates
│   │   │       └── theme/          # M3 dynamic colors and typography
│   │   └── res/                    # Adaptive launcher icons, drawables, strings
│   └── build.gradle.kts            # App-level build script (package: jk.indoweb.sss)
├── gradle/
│   ├── libs.versions.toml          # Gradle Version Catalog
│   └── wrapper/                    # Gradle wrapper configuration
├── build.gradle.kts                # Root build file
└── settings.gradle.kts             # Project settings (Indoweb)
```

---

## 🚀 Building & Testing

### Building Locally
```bash
# Compile debug APK
./gradlew assembleDebug

# Output APK is located at:
app/build/outputs/apk/debug/app-debug.apk
```

### Running Tests
```bash
./gradlew testDebugUnitTest
```

---

## 📦 Automated GitHub Release Workflow

The workflow at `.github/workflows/release.yml` triggers automatically on:
- Every push to the `main` branch.
- Creating a tag matching `v*`.

It sets up JDK 17, builds `./gradlew assembleDebug`, generates a tag `v1.0.<run_number>`, and publishes a GitHub Release with the resulting `app-debug.apk` attached directly.
