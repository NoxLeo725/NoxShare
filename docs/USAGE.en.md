# NoxShare user guide

**English** · [简体中文](USAGE.zh-CN.md) · [Project home](../README.md)

## Connect devices

Install NoxShare on the hosting Android phone and connect all devices to the same Wi-Fi. Tap **Start sharing**, then open the displayed address in a browser on another phone or computer. A short address such as `192.168.1.108:8787` has no random path. The QR button displays the full link.

The top panel opens by default; tap its heading to collapse or expand it. On the same phone, **Open locally** checks the server and launches `http://127.0.0.1:8787/`. This loopback address is only for the hosting phone.

## Send and receive

- **Messages:** type below the chat and tap **Send**. Desktop Enter sends; Shift+Enter adds a line break. Messages support up to 4,000 characters. The current session keeps the latest 100 messages.
- **Files and images:** tap **+** and choose **Images** or **File**. Guests can upload too. Desktop users can drag multiple files into the browser window; folders are not supported.
- **Upload status:** the web page shows progress and allows cancellation. Each upload may be up to 2 GB, and the host accepts up to two simultaneous uploads.
- **Sender labels:** host files and messages show **Host**; guests are identified by their actual connection IP. Sent/received filters use that identity.

Web pages refresh shared content approximately every two seconds.

## Preview, open and copy

Tap an image to preview and zoom. Tap a video card to play, pause or seek. Close the preview to stop playback. If a format is unsupported, save/download the original and use another app.

In the Android app, tapping an ordinary file opens it with a compatible default app; the system lets you choose when needed. The file list and media preview also provide **Open with default app**. **Save** writes a separate copy to a location you choose.

URLs using HTTP/HTTPS, www addresses and LAN IP addresses with ports become clickable. They open in a browser. Long-press text for **Copy text**, or a link for **Copy link**, **Copy text** and **Open website**.

## Manage files in chat

On the host, long-press a file card, image or video in the **chat page** for about 0.65 seconds. Confirm the removal, or cancel to keep it. A normal tap still opens/previews the file. Scrolling cancels the hold.

Use **… → All files / Sent files / Received files** for search, multiple selection, removal and clearing. Guests can select files for a ZIP download, with up to 200 files per ZIP.

Removing a host-selected file removes its sharing entry and keeps the original. Removing a guest upload deletes its copy inside the host app. Originals on the uploading device and separately saved copies are kept.

## Language and permissions

Choose **… → Settings → Language**: **System default**, **简体中文** or **English**. Changes apply immediately and persist. Each browser stores its own choice. Switching the UI language preserves message text, filenames and the unsent draft.

The host settings also include permission status and links to installation, notification and system app settings. Opening an APK may ask you to allow **Install unknown apps** for NoxShare. Return to continue to the system installation confirmation. Installation still requires your confirmation there. Other apps receive temporary read-only access to selected files.

## Stop sharing and cleanup

Tap **Stop sharing**, or stop from the Android notification. This closes active network transfers and clears text messages. Files are kept by default.

The host can enable **Clear all shared content when sharing stops** in Settings. Then stopping sharing clears messages, sharing entries and received copies inside the app. Host originals and separately saved files remain. Save any received files you want to keep before clearing or uninstalling the app.

## Connection help

Check **… → Network info** for connection status, host IP, gateway, port and local service check. If the service check fails, stop and restart sharing. If local access works but another device cannot connect, check that both use the same network and that the router allows devices to communicate. A phone hotspot can be used instead; the gateway may show “—”.

Keep the host app running during sharing. Some phones require allowing background activity in their system settings. If the host IP changes after reconnecting to Wi-Fi, use the new address shown in the app.

Made by **@NoxLeo**.
