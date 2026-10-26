<p align="center"><img src="docs/images/icon.svg" width="72" alt="NoxShare icon"></p>

# NoxShare

[简体中文](README.zh-CN.md) · **English**

A chat-style Android app for sharing files, images, videos and messages over your local network. Other devices join through a browser. Made by **@NoxLeo**.

**Version 1.0.7 · Android 8.0+ · English / 简体中文**

[Download APK](https://github.com/NoxLeo725/NoxShare/releases/download/v1.0.7/NoxShare-1.0.7.apk) · [Release notes](https://github.com/NoxLeo725/NoxShare/releases/tag/v1.0.7)

<p><img src="docs/images/chat-en.png" width="290" alt="English chat interface"> <img src="docs/images/chat-zh.png" width="290" alt="Chinese chat interface"></p>

## Features

| Feature | Details |
| --- | --- |
| Local web sharing | Start or stop sharing from the Android app. Guests open a short IP-and-port address or scan a QR code. |
| Two-way transfers | Host and guests send files, images, videos and messages. Desktop browsers support drag-and-drop uploads. |
| Chat interface | Collapsible sharing panel, file cards, timestamps and sender labels. The host is labeled “Host”; guests use their connection IP. |
| Media previews | Tap images to view and zoom; tap videos to play and seek. Save or download the original file. |
| File management | Search, sent/received filters, multiple selection, removal and ZIP downloads. Long-press a chat file to remove it on the host. |
| Text and links | Clickable URLs and long-press actions to copy a link or the complete message. |
| Android integration | Open files in a default app, save with the system file picker, and request APK installation permission when needed. |
| Sharing settings | Network status, IP, gateway and service checks. Optional cleanup when sharing stops. |
| Languages | Follow the system language or select English / 简体中文 in Settings. Each browser can choose independently. |

## Quick start

1. [Download the APK](https://github.com/NoxLeo725/NoxShare/releases/latest), transfer it to your Android phone and install it. You can also [build from source](docs/BUILD.en.md).
2. Connect the phone and other devices to the same Wi-Fi, or connect them to the phone’s hotspot.
3. Open NoxShare and tap **Start sharing** in the top panel.
4. Open the displayed address, such as `http://192.168.1.108:8787`, in another device’s browser, or scan the QR code.
5. Send a message from the bottom input, or use **+** to choose images/files. On a computer, drop files into the window to upload.
6. Use **… → Settings → Language** to change the language, and **Stop sharing** when finished.

On the hosting phone itself, **Open locally** checks the service and opens `http://127.0.0.1:8787/`. Other devices use the host’s LAN IP.

## Guides

- [User guide — English](docs/USAGE.en.md) / [使用说明 — 中文](docs/USAGE.zh-CN.md)
- [Build and test — English](docs/BUILD.en.md) / [构建与测试 — 中文](docs/BUILD.zh-CN.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Project layout

```text
app/src/main/java/com/noxleo/share/   Android app, foreground service and HTTP server
app/src/main/assets/                 Shared app/web UI and translations
app/src/androidTest/                 Isolated Android feature tests
tests/                              JVM server tests and Android test configuration
tools/                              Translation bundle generator
docs/                               Bilingual guides and screenshots
```

The app uses Java 17, Android SDK 35, Gradle 8.10.2 and Android Gradle Plugin 8.7.3. The UI and QR library are bundled locally, with no CDN dependency. The default port is **8787**.

Browser transfers, media playback, language switching and core server tests have passed. Android integration and notification language changes have been checked on an Android 15 emulator; physical-device networking and vendor background policies still need device testing.
