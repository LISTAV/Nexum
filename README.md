# Nexum

<p align="center">
  <b>A modern, open-source Android app to sync and forward SMS and Call notifications to Telegram.</b>
</p>

---

## 📱 Features

- **Real-time SMS Forwarding:** Intercepts incoming and sent SMS and forwards them instantly to your configured Telegram bot or channel.
- **Call Log Synchronization:** Logs incoming, outgoing, and missed calls with contact resolution.
- **Compose & Send SMS:** Built-in SMS composer to draft and send SMS messages directly.
- **Offline Resiliency:** Employs Android `WorkManager` with Room database persistence to queue messages when offline and sync them immediately upon reconnection.
- **Modern UI:** Built with 100% Jetpack Compose and Material 3 design.
- **Privacy & FOSS:** Zero telemetry, zero proprietary trackers, and licensed under the GNU General Public License v3.0.

---

## 🔒 Permissions & Security Disclosure

Nexum requests sensitive permissions solely for its core forwarding functionality:

| Permission | Purpose |
| :--- | :--- |
| `android.permission.RECEIVE_SMS` / `READ_SMS` / `SEND_SMS` | Receive incoming messages and synchronize them to Telegram. |
| `android.permission.READ_CALL_LOG` / `READ_PHONE_STATE` | Detect incoming/missed calls and forward call records. |
| `android.permission.READ_CONTACTS` | Display contact names alongside phone numbers in forwarded messages. |
| `android.permission.INTERNET` | Transmit encrypted payloads to the official Telegram Bot API (`api.telegram.org`). |
| `android.permission.POST_NOTIFICATIONS` | Deliver status notifications and alerts. |

All communication happens directly between your Android device and the official Telegram API over TLS 1.3 / HTTPS. No third-party proxy or intermediary servers are used.

---

## 🚀 Building from Source

Ensure you have Android SDK 37 (or 35+) and JDK 17/21 installed.

```bash
# Clone the repository
git clone https://github.com/<your-username>/nexum.git
cd nexum

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

---

## 📦 Download & Installation

- **GitHub Releases:** Download the latest signed APK from [Releases](https://github.com/LISTAV/Nexum/releases)
- **F-Droid & IzzyOnDroid:** Metadata recipes and Fastlane specifications are included in [`docs/FDROID_SUBMISSION_GUIDE.md`](docs/FDROID_SUBMISSION_GUIDE.md).

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0** - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Developer & Contact

- **Developer:** Himangshu
- **Telegram:** [@Hd6567](https://t.me/Hd6567)


