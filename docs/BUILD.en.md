# Build and test

**English** · [简体中文](BUILD.zh-CN.md) · [Project home](../README.md)

## Requirements

- JDK 17
- Android SDK Platform 35 and Android SDK Build Tools
- Android Studio, or a command-line Android SDK with `JAVA_HOME` and `ANDROID_HOME` configured

The Gradle wrapper downloads Gradle 8.10.2. Android Gradle Plugin 8.7.3 is declared in the project. The minimum supported Android API is 26 and the target is 35. The application ID is `com.noxleo.share`.

## Build the app

Open the repository root in Android Studio and let Gradle sync, or run:

```powershell
.\gradlew.bat assembleDebug lintDebug
```

On macOS/Linux:

```sh
./gradlew assembleDebug lintDebug
```

The test APK is written to `app/build/outputs/apk/debug/app-debug.apk`. You can transfer it to your phone, or install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Debug builds use a local development signing key. Builds from another computer may have a different signature and cannot necessarily update an existing installation. Release signing configuration and private keys are not included in this repository.

## Browser preview

Serve `app/src/main/assets` with any local static HTTP server. For example, with Python 3 installed:

```sh
python -m http.server 8848 --directory app/src/main/assets
```

Open `http://127.0.0.1:8848/?preview=admin&demo=1` for the host UI or `?preview=visitor&demo=1` for the guest UI. Preview files and IPs are sample data; this mode does not start the Android sharing service.

## JVM server tests

From the repository root on PowerShell:

```powershell
New-Item -ItemType Directory -Force build/server-test | Out-Null
javac -encoding UTF-8 -d build/server-test app/src/main/java/com/noxleo/share/HttpServer.java app/src/main/java/com/noxleo/share/SharingRouter.java app/src/main/java/com/noxleo/share/UploadStorage.java app/src/main/java/com/noxleo/share/MediaContent.java tests/ServerTest.java
java -cp build/server-test ServerTest
```

Tests cover streaming uploads/downloads, byte integrity, IP labels, interrupted upload cleanup, ZIP downloads, messages, range requests, concurrent connections and shutdown.

## Isolated Android tests

Connect an Android 15+ emulator/device with ADB. Build using the test init script, which adds the separate `.verify` application ID:

```powershell
.\gradlew.bat --init-script tests/verify.gradle assembleDebug assembleDebugAndroidTest
adb install -r build/verify/app/outputs/apk/debug/app-debug.apk
adb install -r build/verify/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.noxleo.share.verify.test/com.noxleo.share.FeatureInstrumentation
```

To select another test runner, add the quoted Gradle option `'-PnoxshareRunner=com.noxleo.share.LanguageInstrumentation'` or `'-PnoxshareRunner=com.noxleo.share.LinkPermissionInstrumentation'` to the build command, install the generated test APK again, then invoke the matching runner with `adb shell am instrument -w`.

- `FeatureInstrumentation`: external app file access, read-only grants and cleanup settings.
- `LanguageInstrumentation`: system/Chinese/English settings, native dialogs, live notifications, persistence and original content preservation.
- `LinkPermissionInstrumentation`: browser handoff and installation settings/installer intents. Run with installation permission initially disabled. Installer launching is intercepted by the test; no APK is installed.

These tests refuse to run against the main application ID and only clean their own test content.

## Edit translations

Translations are stored in `app/src/main/assets/translations.json`. After editing them, regenerate the browser bundle using Node.js:

```sh
node tools/generate-translations.cjs
```

The generated bundle is committed, so Node.js is not required for a normal Android build. Android reads the same JSON dictionary for its native messages. File names and message bodies are treated as user data, not translation keys.
