# Locust — native Android

Kotlin + Jetpack Compose. Replaces the Capacitor/WebView build.

## Building

Push to `main`, or run **Build Locust APK** from the Actions tab. The APK
appears as the `locust-apk` artifact on the finished run. No Gradle wrapper is
committed — CI provides Gradle, so there's no `gradle-wrapper.jar` to keep in
sync.

Locally: `gradle assembleDebug` (or open in Android Studio).

## Storage

`filesDir/locust.json`, written atomically through a temp file with the previous
version kept as `locust.prev.json`. If a write is interrupted the app falls back
to the previous file on next launch.

This is deliberately not Room. The dataset is a few megabytes of documents, not
a relational workload, and the JSON format is byte-identical to the web version's
vault export — so backups move between the two without conversion. Room can be
added later behind the same file format if querying ever justifies it.

Backups use the Storage Access Framework (`CreateDocument` / `OpenDocument`), so
the user picks the folder and no storage permission is declared or needed.

## Importing from the web version

Desk → Import, and pick a `locust-vault.json` exported from the HTML app.
Chapter HTML is flattened to plain text on the way in; entry kinds, chapter
numbering, tags, profile, and daily word stats all carry over.

## Layout

```
app/src/main/java/com/plutani/locust/
  Store.kt         model, JSON persistence, word counting, import/export
  Theme.kt         dark palette and the six accents
  MainActivity.kt  navigation and every screen
```
