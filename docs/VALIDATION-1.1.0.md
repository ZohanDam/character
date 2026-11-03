# Validation: 1.1.0

Version **1.1.0 (3)**. APK: **91,278 bytes**. Requires Android 10 / API 29 or newer.

SHA-256: `b952efe27ed60cb0b3f56582f030750dd425a71a0f25ce43f8a2d74ff10e057b`.

The release uses the same signing certificate as 1.0.1: SHA-256 `69787941d332e42b77570a786592498e233a7e9519c3ff2121f601fd901527fd`.

## Build and unit checks

- **26 tests passed**, with zero failures or errors. Coverage includes Sunday rotation, cycle/year boundaries, next-reminder calculations, preserving delayed pending reminders, missing/stale alarm recovery, explicit clock rebuilds, Sunday notification expiry, daylight saving, version comparison, and backup data validation.
- Android lint: **0 errors, 2 warnings** concerning newer available SDK levels. The app currently targets API 35.
- Signed, minified release build and APK signature verification passed.
- [GitHub CI for the feature changes](https://github.com/ZohanDam/character/actions/runs/41489869117) passed tests, lint, and the debug build.

## Android 15 emulator checks

- **Resume:** both registered reminder timestamps remained unchanged when the app returned from the background.
- **Delivery:** an 8:00 AM reminder arrived while the app was in the background. Tomorrow's 8:00 AM reminder was scheduled, while today's separate 6:00 PM reminder remained intact.
- **Sunday expiry:** Android removed a notification posted on Saturday at Sunday midnight. The weekly trait advanced from Prudence to Diligence. Only the isolated test emulator's clock was changed and then restored.
- **Test notification:** requesting a test while daily reminders were off prompted for notification permission, posted the current trait with the shortened title and expandable definition, and left daily reminders off.
- **Next reminder:** the displayed date and time matched the stored Android schedule; blocked notification permission was shown explicitly after reinstall.
- **Sound controls:** the shortcut opened Android's settings for the correct Trait reminders channel.
- **Backup export:** the production app exported all twelve traits, reminder preference/times, starting Sunday, and theme through Android's document picker.
- **Import:** edited Unicode definitions, both reminder times, reminder preference, theme, and starting Sunday were restored. Cancelling import and selecting malformed JSON left the existing settings unchanged.
- **Uninstall recovery:** a production backup survived uninstall; a clean signed installation restored the complete data through Android's picker. Android notification permission was not transferred.
- **Upgrade:** the actual signed 1.0.1 → 1.1.0 update preserved edited data and migrated both legacy reminder times into the new scheduler.
- **Repeat downloads:** a local debug fixture with a lower version label exercised the new code against the existing public 1.0.1 release. A pre-existing APK did not block downloading; completed downloads were recognized; an explicit retry created a distinct file. Both downloaded APKs matched the public signed release byte for byte. No fake public release was created.

## Limits

Checks used an Android 15 x86_64 emulator. Physical-phone battery use, manufacturer-specific idle restrictions, other Android versions, and real device reboot delivery remain unverified. Boot/time-change rescheduling is implemented; time-change broadcasts and weekly expiry were exercised on the emulator. Signing credentials remain local and are excluded from GitHub.
