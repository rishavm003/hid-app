# Free Windows-to-iPhone test setup

Haven is still a development prototype. The Windows server/controller runs, and backend tests pass. The updated native iPhone source has not yet been compiled or tested on a physical iPhone. No installable IPA has been produced yet.

## 1. Start your PC controller

Double-click `windows/Start Haven.vbs`. The official Cloudflare helper is already installed on the development PC. On another Windows PC, install Python with Tkinter and run `windows/Install connection.ps1` once first. The helper is downloaded from Cloudflare's official GitHub release and checked against its published SHA-256 digest.

The launcher starts a local family server, opens a temporary HTTPS tunnel, and connects the controller automatically. No server address or controller token needs typing into the PC app. The controller token is stored using Windows DPAPI for your Windows account. Private data stays in `windows/private/` and must never be uploaded to GitHub.

This free test launcher disables SMS and has no SMS contacts configured. Location sharing and manual call notes use real data if you enable them. No one receives SMS from this launcher. Keep the PC awake and online. Closing the controller stops the server; the latest committed location remains on disk. Do not open multiple launcher instances against the same database.

Cloudflare's temporary URL can change after restart. Use **Copy phone server address** and the phone's **Reconnect after PC restart** section when that happens. The controller API is only available on the local PC; the phone-facing tunnel rejects controller operations even with a controller token.

## 2. Build the iPhone package without owning a Mac

The repository includes `.github/workflows/ios-build.yml`, which uses a standard GitHub-hosted macOS runner and creates an unsigned physical-device IPA. No Apple password or signing certificate belongs in GitHub.

1. Upload the source to your repository. If using `release/Haven-GitHub-Source.zip`, extract it first and upload the files/folders, not the ZIP itself. Include `.github/workflows/ios-build.yml` and the `ios` folder.
2. On GitHub, open **Actions → Build iPhone test package → Run workflow**.
3. Wait for the build. If it fails, share its build-log link so the compiler errors can be fixed.
4. Download **Haven-iPhone-test-package** from the successful run's artifacts and extract `Haven-unsigned.ipa`.

Standard hosted runners are free for public repositories; private repositories use your account's included allowance. Keep paid usage disabled if you want a strict zero-cost setup. A successful workflow compiles the app; it does not prove location delivery works on a locked physical iPhone.

## 3. Install from Windows with free signing

Use **AltStore Classic / AltServer**, following its official Windows guide linked below. This is a sideloaded test installation, not TestFlight or an App Store release.

1. Follow AltStore's instructions for the required Apple iTunes/iCloud components and AltServer on Windows.
2. Connect the owner's unlocked iPhone with a cable, trust the PC and enable Wi-Fi sync as instructed.
3. Use AltServer to install AltStore on that phone. The owner signs in to Apple themselves in AltServer/AltStore. Never send Apple passwords in this chat or put them in source files.
4. Trust the developer profile and enable Developer Mode when iOS asks.
5. Transfer `Haven-unsigned.ipa` to the phone, then import it in AltStore's My Apps screen. AltStore signs and installs it using the owner's free Apple account.

**Free signing expires after seven days.** Use AltStore's Refresh All while AltServer is available before expiry. If the app expires, do not assume background sharing continues; refresh it, reopen Haven and verify a new location in the controller. Apple also limits the number of active sideloaded apps. Repeat setup with each family member's own Apple account. This weekly maintenance makes free sideloading unsuitable as a set-and-forget emergency system.

## 4. Connect each phone in a few taps

1. PC: **Create invitation → Copy invitation**. Share it privately; it expires in ten minutes.
2. iPhone: **Haven → Family → Paste invitation → Use invitation**. Enter your name, review the controller and accept joining.
3. Tap **Enable hourly location sharing** and approve location permission.
4. Tap **Improve background sharing**. Choose Always if offered; otherwise use **Open Haven's iOS Settings → Location → Always** if available. This permission remains the phone owner's choice.
5. Tap **Share my location now**. Verify that the controller receives a new timestamp and sensible coordinates. Start with an ordinary location test, not a real emergency.

Haven combines foreground-started background location, significant-location-change recovery with Always permission, and scheduled best-effort background refresh. The phone can recover from some OS terminations when movement produces an event. Force quitting, disabled permissions, expired signing, a dead battery, no connection, a stopped PC server, or iOS scheduling can still interrupt uploads. An exact hourly update while stationary is not guaranteed.

## 5. Hide the icon only after testing

Long-press Haven and look for **Require Face ID → Hide and Require Face ID**. **This must be checked on the actual sideloaded build.** Apple's documented hiding eligibility is limited; a development/sideloaded installation may not offer this option. Do not assume free sideloading meets the full icon-hiding requirement until verified.

If the option is absent, **Remove from Home Screen** only removes the Home Screen icon; it still appears in App Library. Haven cannot silently hide itself or bypass iOS hiding rules. Hiding is not uninstall protection. Retest location sharing after hiding, locking the phone, changing Wi-Fi/cellular networks and refreshing the weekly installation.

## Official references

- [AltStore: Windows installation](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows)
- [AltStore: seven-day expiry and app limits](https://faq.altstore.io/altstore-classic/your-altstore)
- [GitHub: hosted runners and free usage](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [Apple: location authorization and background recovery](https://developer.apple.com/documentation/corelocation/requesting-authorization-to-use-location-services)
- [Apple: lock or hide an app](https://support.apple.com/en-ie/guide/iphone/iph00f208d05/ios)
- [Cloudflare: temporary test tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)
