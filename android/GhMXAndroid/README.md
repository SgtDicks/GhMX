# GhMX for Android

This Android project is being prepared as an update to the existing GhMX Google Play listing. It uses the existing application ID `io.kodular.sgt_dicks.GhMX_1` and starts at version code `8` / version name `1.0.7` (the latest uploaded Play bundle is version code `7`, version name `1.0.6`; the production release is `1.0.4`).

## Features

- Home screen with the blue GhMX hex background and a GitHub-hosted logo that is cached by Coil.
- Event schedule, loaded only from the current-year GitHub status and stage files and cached. The legacy `schedule.json` is intentionally ignored.
- Searchable exhibitors sorted by booth number, zoomable venue map, and zoomable food menus.
- Saved vendors, on-device notes, personal saved-event schedule, and local visit/event reminders.
- Settings for system, light, or dark appearance; manual refresh; privacy policy, support, and event website links.
- Firebase Analytics hooks for tab views and vendor website taps, plus optional opt-in to the `ghmx_announcements` Cloud Messaging topic.

## Firebase setup

The Android app is registered in the existing Firebase project `ghmx-admin` with package `io.kodular.sgt_dicks.GhMX_1`. Its `google-services.json` is installed in `app/` and is git-ignored. The Google Services Gradle plugin is applied when that file exists. Firebase Analytics logs tab views and vendor website taps. The optional announcement switch requests notification permission and subscribes opted-in devices to the `ghmx_announcements` Cloud Messaging topic. Send announcements to that topic through Firebase Cloud Messaging or a trusted server; this app never sends announcements itself. Local vendor/event reminders stay on-device and do not use Firebase.

## Build

Open this folder in Android Studio and run the `app` configuration on an emulator or Android phone. Before creating a signed Android App Bundle for the existing Play listing, verify that the upload key matches the upload certificate shown in Play Console. The package name and version code must remain `io.kodular.sgt_dicks.GhMX_1` and greater than `7`. Keep the private upload key and passwords secure; do not commit them to source control.
