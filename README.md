# Notes — Personal safety pilot (Android & iOS)

Start with [the Android guide (ANDROID.md)](ANDROID.md) for 2-minute APK building and simple sideloading without AltStore/Mac, or [the free Windows/iPhone test setup](WINDOWS-IPHONE.md) for iOS AltStore signing.

Native Kotlin/Compose Android app and native SwiftUI iPhone source plus a runnable Python alert server. **This is an unvalidated pilot, not a deployed or emergency-ready service.** GitHub Actions builds both platforms on demand.

**New: family controller mode & Android client.** See [ANDROID.md](ANDROID.md) for the Android client and [FAMILY.md](FAMILY.md) for the PC controller, individual member invitations, owner-approved background location, last-known-location display and manually shared call notes.

## Implemented

- Explicit owner consent, chosen trusted contacts, SOS confirmation, and optional foreground location sampling.
- Server-confirmed check-in timers (1–240 minutes). Once registered, a missed check-in creates an alert even without the phone. A completion after the deadline cannot recall the alert.
- Durable SQLite alerts, per-recipient delivery jobs, retry backoff, and request idempotency.
- Demo mode sends nothing. Live mode submits SMS to Twilio; activity says `provider accepted`, never `delivered`. Carrier delivery and human acknowledgement are not implemented.
- HTTPS-only iOS connections, Keychain token and pending-request storage, explicit retry after uncertain network outcomes, and a privacy cover when the app becomes inactive.
- No covert recording or location access without the phone owner's permission. Optional Family mode adds visible, owner-enabled background location sharing.

## Run the server locally (PowerShell, Python 3.11+)

```powershell
cd server
Copy-Item config.example.json config.json
$env:HAVEN_API_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:HAVEN_MODE = 'demo'
python server.py
```

Keep the generated token private; securely transfer it to the iPhone's Setup screen. Edit `config.json` with the phone owner's name and 1–5 consenting contacts in E.164 format. The example phone number is a placeholder. One server/database/token represents **one person**, not a multi-user deployment. Contacts are administered in the server configuration; restart the server after changing them. Existing alert jobs retain their original recipients.

The server binds to `127.0.0.1:8080`. For an iPhone, deploy behind an HTTPS reverse proxy on a trusted host with a valid certificate. The iOS client intentionally rejects HTTP origins. Do not expose Python's development HTTP server directly to the internet. Persist the database across restarts and run one server process per database. Both API and worker must remain running. A server restart processes overdue timers; an outage delays them.

For actual SMS, configure these **on the server only**, then restart:

```powershell
$env:HAVEN_MODE = 'live'
$env:TWILIO_ACCOUNT_SID = 'your-account-sid'
$env:TWILIO_AUTH_TOKEN = 'your-auth-token'
$env:TWILIO_FROM = 'your-sms-capable-number'
python server.py
```

SMS charges, country-specific sender rules, trial recipient verification and provider coverage apply. Arrange testing with recipients before enabling live mode. Never put Twilio credentials in the app. A failed request to the provider may have been accepted before the connection failed; retries can create duplicate SMS messages. Alerts include a stable short identifier to help contacts recognize duplicates.

## Build on a Mac

1. Install Xcode and XcodeGen (`brew install xcodegen`).
2. Run `cd ios && xcodegen generate`, then open `Notes.xcodeproj`.
3. Choose your signing team and a unique bundle identifier. Select an iPhone with iOS 17+ or a simulator and build/run.
4. In Setup, enter your HTTPS server origin and API token. Save and connect, review Circle contacts, then enable the consent toggle and save again.
5. Start in demo mode. Test SOS, a one-minute check-in, and completing before its deadline. Circle shows recorded alerts and submission states.

This Windows workspace has no Xcode or Swift compiler. The GitHub Actions workflow compiled an unsigned iPhone test IPA successfully, but signing, installation, simulator rendering and physical-device behavior remain unverified. No signed IPA or App Store release is included. An app icon, accessibility/device QA, privacy manifest assessment and App Store materials are still required for distribution.

## Tests

```powershell
python -m unittest discover -s server -v
```

Tests use temporary databases and a fake sender, never Twilio. They exercise deadline expiry, restart recovery, on-time and late completion, repeat requests, concurrent expiry/completion, optional and invalid locations, provider retry exhaustion, demo isolation and HTTP authentication.

## API

All endpoints require `Authorization: Bearer <token>`. POSTs require a UUID `Idempotency-Key`; retry an uncertain request with the exact same key and body. Different content with an old key is rejected.

| Endpoint | Payload | Result |
| --- | --- | --- |
| `GET /v1/status` | None | Mode, contacts, latest timer, last ten alerts and submission states |
| `POST /v1/sos` | Optional `location` | Durable queued/demo alert |
| `POST /v1/checkins` | Integer `minutes`, optional `location` | Server deadline and active timer ID |
| `POST /v1/checkins/complete` | Timer `id` | Complete or already expired |

Location contains numeric `latitude`, `longitude`, `accuracy` in meters and Unix `timestamp`. Samples older than two minutes are rejected on a new command. Retried previously accepted commands return the original result. Timer alerts label the saved location as last shared, not current. The app omits samples older than 90 seconds; toggle location off/on to refresh. An unavailable fix never blocks an SOS.

## Methodology and iOS boundaries

| Method | Treatment |
| --- | --- |
| SOS in app | Implemented with confirmation and server acknowledgement. Needs connectivity. |
| Missed check-in | Implemented on the server, independent of app background execution after registration. |
| Discreet app presence | Owner can use iOS hide/lock or remove from Home Screen. App cannot erase system records, prevent uninstall or guarantee concealment. |
| Recent-app preview | Privacy overlay implemented; verify snapshots on device. |
| SOS location | Optional single foreground sample. Never delays the request; can be absent. Family background sharing is a separate feature described in FAMILY.md. |
| Local reminder | Optional notification 30 seconds before deadline. Does not send an alert or cancel the server timer. |
| Siri / Action button / Back Tap | Possible future user-configured App Intent/Shortcut; not implemented. Lock-state and hiding restrictions must be tested. |
| Live emergency location | Future explicit, time-limited session; requires battery, permission and background-mode validation. Not implemented. |
| Automatic threat recognition | Not implemented; false positives and missed detections make it unsuitable as the sole trigger. |
| Silent camera/microphone / hidden persistence | Not part of this design. Use transparent owner-controlled permissions and platform APIs. |
| Emergency services | Use built-in iPhone Emergency SOS. Haven messages personal contacts and does not dispatch responders. |

An unconfirmed request may or may not exist on the server. The app retains it securely for manual retry. Clear-local-retry does not cancel a server timer or recall an SMS. Offline requests do not silently send later. The app can refresh the current server state, but continuous UI activity is not a service-health guarantee.

## Before any real-world reliance

Run device tests for foreground, locked phone, force quit, uninstall after timer acknowledgement, airplane mode, notification denial, location denial, provider failure, server restart and expired completion. Test VoiceOver and larger text. Verify trusted people actually receive and understand the messages. Practice using Apple's built-in safety tools separately.

The pilot uses one bearer token, no user registration or recipient acknowledgement, no carrier delivery callbacks, and no automated operational monitoring. A public multi-user release needs individual authentication and revocation, contact verification, abuse/rate controls, audit and incident handling, resilient workers, monitored delivery and outage escalation, and a security review. SQLite contains sensitive locations/phone numbers and currently retains history indefinitely. Limit access, encrypt the host/backups, establish deletion/retention rules and disclose SMS-provider processing before collecting real data. This development server is not hardened for public traffic.

## Primary references

- [Apple: hide and lock apps](https://support.apple.com/en-lamr/guide/iphone/iph00f208d05/ios)
- [Apple: background location execution](https://developer.apple.com/documentation/corelocation/handling-location-updates-in-the-background)
- [Apple: local notifications](https://developer.apple.com/library/archive/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/SchedulingandHandlingLocalNotifications.html)
- [Apple: App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/), including background services and location restrictions. Personal-contact alerts still need review; approval is not assumed.
- [Twilio: Message resource](https://www.twilio.com/docs/messaging/api/message-resource)
- [Twilio: delivery status tracking](https://www.twilio.com/docs/messaging/guides/track-outbound-message-status)
