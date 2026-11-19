# HTML Runner

<p align="center">
  <img src="docs/images/html-runner-banner.jpg" alt="HTML Runner promotional banner" width="100%">
</p>

<p align="center">
  <strong>Run your local HTML files on Android.</strong><br>
  A lightweight, immersive Android WebView runtime that runs local web projects edge-to-edge.
</p>

<p align="center">
  <a href="https://github.com/Mobinshr/HtmlRunner1/actions/workflows/build.yml">
    <img src="https://github.com/Mobinshr/HtmlRunner1/actions/workflows/build.yml/badge.svg" alt="Build APK">
  </a>
  <a href="https://github.com/Mobinshr/HtmlRunner1/releases">
    <img src="https://img.shields.io/github/v/release/Mobinshr/HtmlRunner1?display_name=tag" alt="Latest release">
  </a>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/license-not_specified-lightgrey" alt="License not specified">
</p>

---

## 📱 About

**HTML Runner** is a simple Android application that lets you select an HTML file stored on your device and open it inside the app. It uses Android WebView to display the page and run its web content.

## ✨ Features

- 🖥️ Full-screen, edge-to-edge web execution with no in-app toolbar or preview chrome.
- 📂 Open a complete web project folder or a standalone HTML file using Android's system picker.
- 🌐 Display HTML pages using Android WebView.
- ⚡ JavaScript, DOM storage, local project assets, HTML file inputs, and full-screen media support.
- 📍 Geolocation support for the local project origin after Android permission is granted.
- 📦 Download installable debug APK builds from GitHub.
- 🤖 Automatically build an APK with GitHub Actions whenever code is pushed, or when the workflow is started manually.
- 🚀 Publish successful builds to the GitHub Releases page.

## ⬇️ Download

**[Download the latest APK from GitHub Releases](https://github.com/Mobinshr/HtmlRunner1/releases/latest)**

1. Open the Releases page using the link above.
2. Download `app-release.apk` from the latest release.
3. Open the downloaded APK on your Android device and follow the installation prompts.
4. If Android asks for permission to install apps from that source, allow it for your browser or file manager, then retry.

> The release APK is signed with the project's permanent signing key.

## 🧭 How to use

1. Install and open **HTML Runner**.
2. On first launch, choose the project folder containing `index.html` in Android's system picker. The last selected folder is restored automatically on later launches.
3. To open a standalone HTML file, use Android's **Open with / Share** action. On the empty screen, a short tap opens the folder picker and a long press opens the HTML file picker.
4. Web content occupies the full screen; there are no in-app toolbars or controls.

## 🛠️ Build from source

This project uses **Gradle** and **Android Gradle Plugin 8.5.2**.

### Requirements

- JDK 17
- Gradle 8.7 (the GitHub Actions workflow installs it automatically)
- Android SDK / Android build tools available in your build environment

### Build a debug APK

From the project root, run:

```bash
gradle assembleDebug
```

The APK is generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

## ⚙️ Automatic builds

The workflow is located at [`.github/workflows/build.yml`](.github/workflows/build.yml).

- Every push to the repository triggers an APK build.
- You can also start a build manually from the **Actions** tab.
- The generated APK is uploaded as an Actions artifact.
- Successful builds are also published to **GitHub Releases**.

Useful links:

- [Build history](https://github.com/Mobinshr/HtmlRunner1/actions)
- [Latest APK release](https://github.com/Mobinshr/HtmlRunner1/releases/latest)
- [All releases](https://github.com/Mobinshr/HtmlRunner1/releases)

## 📁 Project structure

```text
.
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/...
├── .github/
│   └── workflows/
│       └── build.yml
├── build.gradle
├── gradle.properties
└── settings.gradle
```

## 🤝 Contributing

Suggestions, bug reports, and improvements are welcome. Please open an issue before submitting major changes.

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/Mobinshr">Mobinshr</a>
</p>
