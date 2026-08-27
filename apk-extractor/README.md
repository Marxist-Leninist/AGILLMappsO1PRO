# APK Extractor

A small native Android utility for exporting APK files from apps already installed on the device.

## Features

- Lists installed user apps with icon, package name and version.
- Search by app name or package name.
- Optional system-app listing.
- Export the base APK through Android's Storage Access Framework.
- Export a complete `.apks` ZIP containing `base.apk` plus every split APK for split-installed apps.
- No storage permission required.

Long-press an app to open its Android app-info screen.

The app declares `QUERY_ALL_PACKAGES` because an APK extractor must enumerate installed packages. This is appropriate for sideloaded/private utility use, but Google Play restricts that permission to approved use cases.
