# TidelFlow 🎵

<div align="center">

[![Live Website](https://img.shields.io/badge/Website-TidelFlow%20Live-6366F1?style=for-the-badge&logo=googlechrome&logoColor=white)](https://Nirav-kumar-dev.github.io/TideFlow/)
[![GitHub release](https://img.shields.io/github/v/release/Nirav-kumar-dev/TideFlow?include_prereleases&style=for-the-badge&color=22C55E)](https://github.com/Nirav-kumar-dev/TideFlow/releases)
[![GitHub downloads](https://img.shields.io/github/downloads/Nirav-kumar-dev/TideFlow/total?style=for-the-badge&color=3B82F6)](https://github.com/Nirav-kumar-dev/TideFlow/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

**An ultra-premium, AI-powered music streaming client for Android with obsidian dark mode, liquid glass aesthetics, NVIDIA Nemotron 3.5 intelligence, and automatic in-app updates.**

[🌐 Visit Official Website](https://Nirav-kumar-dev.github.io/TideFlow/) • [📥 Download Latest APK (v1.0.3)](https://github.com/Nirav-kumar-dev/TideFlow/releases/latest) • [✨ Feature Request](https://github.com/Nirav-kumar-dev/TideFlow/issues)

</div>

---

## ✨ Cutting-Edge Features

### 🤖 NVIDIA Nemotron 3.5 Lightning 30B AI Assistant
- **Hyper-Personalized Curation:** Powered by NVIDIA's `nemotron-3.5-lightning-30b-a3b` with full awareness of your taste, history, and audio preferences.
- **Full AI Playlist Queueing:** Tapping **Play** or selecting any track seamlessly queues the entire AI playlist into playback.
- **Continuous Radio Playback:** When the curated playlist ends, TidelFlow automatically generates a continuous YouTube Radio stream based on the vibe.
- **Guaranteed Novelty & Diversity:** Intelligent non-repetition memory with dedicated persistent JSON storage prevents repetitive tracks across sessions.
- **Mood & Vibe Generation:** Type any vibe, emotion, activity, or prompt (e.g. *"late night cyberpunk drive"* or *"high tempo coding flow"*).

### 🔄 Built-In In-App Updater Engine
- **Seamless GitHub Releases Sync:** Automatically checks for new updates against the repository and alerts you via system notifications.
- **1-Tap Background Download & Install:** Complete in-app updater with cross-host 302 redirect support and active disk discovery.
- **Permission Managed:** Integrated `REQUEST_INSTALL_PACKAGES` and unified `FileProvider` architecture for smooth installation on Android 8.0 through Android 16.

### ⚡ Non-Blocking YouTube Music Sync
- **Instant Background Sync:** Seamlessly synchronizes your YouTube Music playlists, favorites, and library in the background without UI stalls or thread lockups.
- **Force Sync:** Quick one-tap manual sync in Account Settings.

### 📸 Camera Vision AI Playlist
- **Scene-to-Soundtrack:** Snap any view, sunset, workspace, party, or workout scene with your camera.
- **Visual Intelligence:** AI analyzes the ambiance, lighting, and mood to instantly generate a matching soundtrack.

### 🪟 Liquid Glass & Obsidian Dark UI
- **Default Liquid Glass:** Specular frosted glass surfaces across mini-players, navigation bars, and bottom sheets.
- **AMOLED Pitch Black (`#07070A`):** Battery-saving true black design paired with subtle spotlight glows and refined card borders.

### 🎛️ Interactive Real-Time Category Filter Bar
- Quick-filter your home feed across curated categories: **All, Ambient, Focus, Chill, Electronic, Romance, Energetic, Workout, Party**.
- Dynamically loads high-bitrate YouTube Music carousels on the fly.

### 🎧 Synchronized Listen Together
- Host or join synchronized listening rooms with friends anywhere in the world.
- Real-time playback synchronization with zero latency.

### 🛡️ Core Music Player Features
- **Zero Ads:** Complete uninterrupted music experience with no audio ads or sponsor interruptions.
- **Offline Caching:** Download songs, albums, and playlists for offline playback.
- **Synchronized Lyrics:** Word-by-word synced scrolling lyrics.
- **Audio Control:** Built-in equalizer presets, gapless playback, and pitch/speed adjustments.

---

## 📱 App Showcase

<div align="center">

| **Nemotron 3.5 AI Assistant** | **Obsidian Home Feed** |
| :---: | :---: |
| <img src="https://raw.githubusercontent.com/Nirav-kumar-dev/TideFlow/main/docs/assets/screen_ai.png" width="260" alt="Nemotron AI Assistant"/> | <img src="https://github.com/user-attachments/assets/36a91b09-e5bc-41f5-9624-22cf6da7ebda" width="260" alt="Home Screen"/> |

| **Now Playing & Lyrics** | **Listen Together** |
| :---: | :---: |
| <img src="https://github.com/user-attachments/assets/ff9bf8b6-16d8-4ced-be45-113629ee9d42" width="260" alt="Now Playing"/> | <img src="https://github.com/user-attachments/assets/b98efcae-8a23-4bd8-8ffc-e1ce5d32426b" width="260" alt="Listen Together"/> |

</div>

---

## 📥 Download & Installation

### Option 1: In-App Updates (Recommended)
If you already have TidelFlow installed, go to **Settings → Check for updates** or tap the update notification to download and install new versions directly.

### Option 2: Official Website
Visit the official [TidelFlow Website](https://Nirav-kumar-dev.github.io/TideFlow/) to grab the latest APK with one click.

### Option 3: GitHub Releases
1. Head over to the [GitHub Releases](https://github.com/Nirav-kumar-dev/TideFlow/releases) page.
2. Under **Assets**, download `TideFlow.apk`.
3. Open the APK on your Android device (allow *"Install unknown apps"* if prompted).
4. Launch **TidelFlow** and enjoy pure music streaming!

---

## 🛠️ Building From Source

```bash
# Clone the repository
git clone https://github.com/Nirav-kumar-dev/TideFlow.git
cd TideFlow

# Build ARM64 Debug APK with Gradle (Requires JDK 21)
./gradlew :app:assembleArm64FossDebug

# Output APK will be generated at:
# app/build/outputs/apk/arm64Foss/debug/app-arm64-foss-debug.apk
```

---

## 🤝 Community & Support

- **Official Website:** [https://Nirav-kumar-dev.github.io/TideFlow/](https://Nirav-kumar-dev.github.io/TideFlow/)
- **GitHub Issues:** [Create an Issue or Feature Request](https://github.com/Nirav-kumar-dev/TideFlow/issues)

---

## 🙏 Credits & Acknowledgements

- **Developer & Maintainer:** [Nirav Kumar (@Nirav-kumar-dev)](https://github.com/Nirav-kumar-dev)
- **Acknowledgements:** [Aarav Sharma (@aaravgaming007-dev)](https://github.com/aaravgaming007-dev) for earlier contributions, and the Vivi Music & InnerTune open-source communities.

---

## 📄 License
This project is licensed under the [MIT License](LICENSE).
