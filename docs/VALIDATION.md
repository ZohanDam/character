# Validation

Version: **1.0.1 (2)**. Release APK: **78,218 bytes**. Minimum Android version: **10 / API 29**.

## Build checks

- `testDebugUnitTest`: **10 tests passed**, covering Sunday boundaries, the twelve-week wraparound, year changes, dates before the anchor, next-day reminders, daylight-saving changes, local timezones, and numeric version comparison.
- `lintDebug`: **0 errors**; two warnings remain about targeting API 35 rather than newer SDK levels.
- Signed, optimized release build completed. Android accepted the release signature when installing an update over an app signed with the same original key.
- [GitHub CI for the final application changes](https://github.com/ZohanDam/character/actions/runs/41483281676) passed tests, lint, and the debug build.

## Android 15 emulator checks

- Real light and dark screens visually inspected; reminder settings and About screen captured.
- Larger system text at 130% inspected: text reflows, and content remains scrollable.
- Added a second reminder using Android's time picker; edited an existing time using keyboard entry.
- Notification permission and the precise-alarm settings shortcut exercised.
- A real scheduled reminder arrived while the app was in the background. Its title and expanded definition were correct; tapping it opened the app.
- After delivery, Android retained the other reminder time and scheduled the delivered time for tomorrow.
- Blank trait names rejected. Edited names and definitions persisted through a process restart, with the starting Sunday unchanged.
- A local-only older-version fixture detected the public GitHub release and downloaded its APK through the app's Download Manager flow.
- Downloaded APK SHA-256 matched the published signed build byte for byte: `f379823b6b0476b5300dd190bdcb1ae276c7f61bed1cad6a57970a77c9b5f556`.
- Installed that downloaded update over the fixture. The edited trait name, definition, two reminder times, theme, and current weekly focus were preserved. Test trait edits were then restored.
- The installed 1.0.1 release checked the live GitHub API and reported that it was up to date. The update-screen refresh retained its scroll position.

## Limits

Tests used an Android 15 x86_64 emulator, not a physical phone. Battery drain, OEM background restrictions, every supported Android version, and real restart/timezone-change delivery have not been measured on a device. Restart/time-change rescheduling is implemented; date and timezone calculations have unit coverage. Android's force-stop behavior and reminder-permission requirements are documented in the README.
