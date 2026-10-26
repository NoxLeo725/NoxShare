# 构建与测试

**简体中文** · [English](BUILD.en.md) · [项目首页](../README.zh-CN.md)

## 环境

- JDK 17
- Android SDK Platform 35 和 Android SDK Build Tools
- Android Studio，或已配置 `JAVA_HOME`、`ANDROID_HOME` 的命令行 Android SDK

项目自带 Gradle wrapper，会下载 Gradle 8.10.2，Android Gradle Plugin 版本为 8.7.3。最低支持 API 26，目标 API 35，应用 ID 为 `com.noxleo.share`。

## 编译 APP

用 Android Studio 打开仓库根目录并同步 Gradle，或在 PowerShell 中运行：

```powershell
.\gradlew.bat assembleDebug lintDebug
```

macOS/Linux 使用：

```sh
./gradlew assembleDebug lintDebug
```

安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。可传到手机安装，也可运行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`。

Debug 包使用本机的开发签名。不同电脑编译的签名可能不同，未必能覆盖已有安装。本仓库不包含正式签名配置或私钥。

## 网页预览

用任意本地静态 HTTP 服务提供 `app/src/main/assets` 目录。例如安装 Python 3 后运行：

```sh
python -m http.server 8848 --directory app/src/main/assets
```

打开 `http://127.0.0.1:8848/?preview=admin&demo=1` 预览主机，使用 `?preview=visitor&demo=1` 预览访问端。预览中的文件和 IP 为示例数据，不会启动 Android 共享服务。

## JVM 服务测试

在仓库根目录的 PowerShell 运行：

```powershell
New-Item -ItemType Directory -Force build/server-test | Out-Null
javac -encoding UTF-8 -d build/server-test app/src/main/java/com/noxleo/share/HttpServer.java app/src/main/java/com/noxleo/share/SharingRouter.java app/src/main/java/com/noxleo/share/UploadStorage.java app/src/main/java/com/noxleo/share/MediaContent.java tests/ServerTest.java
java -cp build/server-test ServerTest
```

测试覆盖流式上传下载、文件字节一致性、IP 来源、中断上传清理、ZIP、文字、分段读取、并发连接和停止服务。

## 独立 Android 测试

用 ADB 连接 Android 15 或以上模拟器/设备，通过测试配置编译独立的 `.verify` 应用：

```powershell
.\gradlew.bat --init-script tests/verify.gradle assembleDebug assembleDebugAndroidTest
adb install -r build/verify/app/outputs/apk/debug/app-debug.apk
adb install -r build/verify/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.noxleo.share.verify.test/com.noxleo.share.FeatureInstrumentation
```

如需其他测试入口，在构建命令中加引号参数 `'-PnoxshareRunner=com.noxleo.share.LanguageInstrumentation'` 或 `'-PnoxshareRunner=com.noxleo.share.LinkPermissionInstrumentation'`，重新安装生成的测试 APK，再用 `adb shell am instrument -w` 调用对应入口。

- `FeatureInstrumentation`：跨应用文件读取、只读授权、共享清理。
- `LanguageInstrumentation`：系统/中英语言、原生弹窗、通知即时切换、持久保存及原文保留。
- `LinkPermissionInstrumentation`：浏览器调用、安装授权设置和安装器 Intent。初始安装授权应关闭；测试会截获安装器调用，不实际安装 APK。

测试拒绝对正式应用 ID 执行，只清理独立测试包内创建的内容。

## 修改翻译

编辑 `app/src/main/assets/translations.json`，然后用 Node.js 重新生成浏览器翻译包：

```sh
node tools/generate-translations.cjs
```

仓库已包含生成结果，正常编译 Android 不需要 Node.js。Android 原生提示也读取同一份 JSON。文件名和聊天原文作为用户数据保留，不作为翻译键处理。
