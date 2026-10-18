# Android option — design notes only

Updated 18 October 2026. **This project does not contain an Android app or APK.** The implemented phone client is iOS. This file preserves the requirements and platform findings for a future Android build.

## Possible scope

An Android client could use the same family invitation and controller model. The phone owner would explicitly grant location permission and enable sharing. The server would retain the latest successfully uploaded coordinates so the PC can display the last known position if the phone is offline. Android background limits still prevent a guaranteed exact one-hour schedule; a long-running location foreground service would have a visible notification and battery cost.

Android exposes call-log fields such as number, type, date and duration through `CallLog.Calls`, but `READ_CALL_LOG` is a restricted permission. It may require installer allowlisting, and Google Play normally permits call-log access only for a qualifying default Phone or Assistant handler or approved exception. A simple safety app that merely displays a permission prompt cannot assume it will obtain the complete call history. Sideloading also does not guarantee access. A call-log feature would need a clearly disclosed design, explicit owner approval, platform-specific testing, data minimization and retention controls. It would not provide call audio or conversation contents.

An Android app cannot reliably promise that it will be undiscoverable or impossible to uninstall. If it runs a foreground location service, Android shows a notification. The current design keeps sharing visible and under the phone owner's control.

## Before building

1. Choose the target Android versions and whether distribution is private testing or Google Play.
2. Decide whether becoming the phone's default dialer is acceptable to the owner. If not, keep manual call notes, as on iOS.
3. Define the exact call fields needed, how long they are retained, and who can see and delete them.
4. Prototype permission and background behavior on real devices before promising it as a safety feature.
5. Keep separate credentials per family member and require the owner to join and enable each data type.

Primary references: [Android background location](https://developer.android.com/develop/sensors-and-location/location/permissions/background), [background location limits](https://developer.android.com/about/versions/oreo/background-location-limits), [CallLog.Calls](https://developer.android.com/reference/android/provider/CallLog.Calls), [`READ_CALL_LOG` permission](https://developer.android.com/reference/android/Manifest.permission), and [Google Play SMS/Call Log policy](https://support.google.com/googleplay/android-developer/answer/10208820). Check the latest versions before implementing or publishing.
