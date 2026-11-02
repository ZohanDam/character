# Privacy

Character stores edited traits, the initial Sunday, daily reminder times, reminder preference, and theme in private app storage on the Android device. No account is required. The app contains no analytics, advertising, or tracking SDKs.

An explicit **Check for updates** action connects to `api.github.com` to read public release metadata for `ZohanDam/character`. GitHub receives normal connection information, including the device's public IP address, and an app/version User-Agent. Trait text and settings are not sent.

An explicit **Download** action uses Android's Download Manager to retrieve an APK from GitHub Releases and its download infrastructure. Android displays download progress and stores the APK in Downloads. Opening source or release links uses the selected browser.

Automatic app backups are disabled. Uninstalling the app deletes its private data. A downloaded APK remains in Downloads until deleted. Installing a correctly signed update over the existing app preserves its private data.

The public source repository and releases are hosted by GitHub and are subject to GitHub's own privacy policy.
