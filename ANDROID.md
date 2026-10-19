# Haven Notes for Android

A native Android client for the Haven safety platform, written in Kotlin with Jetpack Compose. Disguised as **Daily Notes** with a fully functional note-taking interface and discreet PIN unlock.

---

## ⚡ Quick 2-Minute Setup (Get Your APK)

Unlike iPhone, Android does **not** require a Mac, AltStore, Apple ID, or weekly certificate renewal. You can install and run the APK directly on any Android phone (Android 8.0 through Android 15+).

### Step 1: Build the APK on GitHub Actions

1. Commit and push the `android/` directory and `.github/workflows/android-build.yml` to your GitHub repository:
   ```powershell
   git add android .github/workflows/android-build.yml ANDROID.md scripts/
   git commit -m "Add native Android app with Daily Notes disguise"
   git push origin main
   ```
2. On GitHub, go to **Actions → Build Android test package → Run workflow**.
3. The cloud runner builds `Notes.apk` in about 1–2 minutes.

### Step 2: Download the APK to your PC

Run the included automated helper script:
```powershell
python scripts/download_android_artifact.py
```
This automatically finds the build run, downloads the package, and places `Notes.apk` in your `release/` folder!

### Step 3: Install on your Android Phone

1. Transfer `release/Notes.apk` to your phone (via USB cable, Google Drive, WhatsApp, or email).
2. Tap `Notes.apk` on your phone to install.
3. If prompted, toggle **Allow from this source** (under *Install unknown apps*).
4. Tap **Open** — you will see a normal, functional **Notes** app!
5. **Zero-Effort Permission Popups**: On first open, the app automatically presents system prompts directly on screen:
   - **Location**: Tap "While using the app".
   - **Background Location**: Select **"Allow all the time"** so updates continue when the phone is locked.
   - **Battery Optimization Exemption**: A system popup appears asking *"Let app always run in background?"* — just tap **Allow**!
   - **Notifications**: Tap "Allow".
6. **Instant Zero-Touch Pairing (No Setup Needed)**:
   - The APK comes **pre-paired** with your PC controller as **"Aunt"**.
   - She does **NOT** need to enter an invitation code, type a secret PIN, or open the Family tab.
   - Once permissions are granted, her phone immediately begins sending hourly location updates to your PC controller!

---

## 🔒 Discreet Triggers & Stealth

The app opens as **Notes** with genuine local note-taking capabilities (create, search, edit, delete notes). It leaves no suspicion if casually inspected.

### How to Unlock Safety Controls:
1. **Search Bar PIN**: Tap the search bar and type your secret PIN (default: **`1998`**). The safety interface unlocks instantly.
2. **Title Long-Press**: Long-press the "Notes" header title for 1.2 seconds. A passcode prompt will appear.
3. **Instant Auto-Lock**: Whenever you switch apps, minimize, or lock your phone, Notes instantly relocks itself to the decoy notes screen.
4. **Change PIN**: Go to **Setup → Disguise & Security** to set any 4-digit PIN of your choice.

### Android System Privacy Features:
- **Samsung**: Move Notes into *Secure Folder*.
- **Android 15+**: Place Notes inside *Private Space*.
- **Xiaomi / Redmi / POCO**: Use *Second Space* or *Hidden Apps*.
- **Custom Launchers (Nova, Niagara)**: Hide the app icon from your app drawer.

---

## 📱 Feature Overview

| Feature | Android Status | Behavior |
| --- | --- | --- |
| **Daily Notes Decoy** | ✅ Implemented | Authentic local notes list, search, add, edit, delete |
| **Dual Unlock Triggers** | ✅ Implemented | Search bar secret PIN + 1.2s header title long-press |
| **Instant Auto-Relock** | ✅ Implemented | Locks immediately on app minimize, pause, or screen off |
| **Automatic Location Sharing** | ✅ Implemented | **ON by default** — active immediately upon joining family |
| **Automatic System Prompts** | ✅ Implemented | Directly prompts for battery optimization bypass and background location |
| **Urgent SOS** | ✅ Implemented | Large prominent button, confirmation dialog, optional location |
| **Check-in Timer** | ✅ Implemented | 1 to 60 min durations, server-backed deadline, live countdown |
| **Check-in Reminders** | ✅ Implemented | High-priority heads-up notification 30s before deadline |
| **Circle Contacts** | ✅ Implemented | Trusted contacts and SMS delivery states from server |
| **Family Invitation** | ✅ Implemented | Decodes `NOTES1.`, `HAVEN1.`, JSON base64, or raw codes |
| **Controller Preview** | ✅ Implemented | Verifies controller identity and requires explicit consent |
| **Background Location** | ✅ Implemented | Android Foreground Service with low-priority notification |
| **Hourly Throttling** | ✅ Implemented | Battery-conscious updates (at most once/hour when stationary) |
| **Share Location Now** | ✅ Implemented | Forces an immediate fresh GPS/network sample upload |
| **Manual Call Notes** | ✅ Implemented | Typed call details shared to controller (retained 30 days) |
| **Erase Shared Data** | ✅ Implemented | Erases location and notes from server with confirmation |
| **Reconnect Tunnel** | ✅ Implemented | Seamlessly updates server address after PC launcher restart |

---

## 🛠️ Testing Locally with Your Windows Controller

1. Start your local server and controller using the Windows launcher:
   - Double-click `windows/Start Haven.vbs`
2. In the controller window, click **Create invitation → Copy invitation**.
3. In Notes on your Android phone:
   - Unlock with PIN **`1998`**
   - Tap the **Family** tab
   - Paste the invitation into **Paste invitation from your controller** and tap **Use invitation**
   - Enter your name and tap **Review invitation**
   - Check the consent box and tap **Accept and join**
   - **Location sharing activates immediately and automatically** — no separate enable button needed!
4. To test SOS:
   - Tap **Connect Home and Circle to my family account**
   - Go to **Setup**, enable the alert consent toggle, and tap **Save and connect**
   - Go to **Home**, tap **Send SOS**, and confirm!

---

## 🔋 Android Battery & Background Reliability

- Android 10+ displays a discreet persistent notification (*"Notes: Family location sharing is active"*) required by Android to keep the service running. It does not vibrate or make sounds.
- Because the app directly requests battery optimization exemption upon opening, Android will not kill the hourly location updates in deep sleep.
