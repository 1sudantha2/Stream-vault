# Stream-Vault Android app

Native Kotlin Android client for the Stream-Vault server. The UI follows the dark cyan/purple visual language in `web/templates/gallery.html` and `web/templates/watch.html`, while the player is powered by AndroidX Media3 (ExoPlayer).

## Included

- Material 3 dark UI with gallery cards, search, server statistics and a URL player.
- Backend URL is editable in **Settings** and is persisted with DataStore. The client uses the same `GET /api/videos` and `GET /api/videos/{id}` API shape used by the web player.
- Media3 playback for direct files, HLS/fMP4 and DASH URLs, with automatic format detection.
- Hardware decoder first with Media3 decoder fallback; the FFmpeg extension is included for devices that need a software path. No video transcoding is done on the phone.
- Audio and subtitle track selection, local SRT/ASS/SSA/VTT/TTML subtitle picker, subtitle-delay controls, speed presets from 0.25x to 4x, zoom gesture and reset.
- Player gestures: double-tap left/right for 10-second seek, horizontal swipe to seek, vertical swipe on the left for brightness and on the right for volume.
- Picture-in-picture, fullscreen, background playback through `MediaSessionService`, Android TV launcher support, and Android Storage Access Framework pickers.
- No ads and no broad storage permission. Only Internet, media playback foreground-service and optional notifications are declared.

## Build the APK on GitHub

Every push or pull request builds a release APK with [`.github/workflows/android.yml`](.github/workflows/android.yml). It uses JDK 17 and Gradle 8.7. After the workflow completes, download the `stream-vault-release` artifact from the Actions run.

The application ID is `com.streamvault.player` and the display name is `Stream-Vault`.

## Run locally

Open the repository in Android Studio Hedgehog or newer and let it sync. Select the `app` configuration and run it on an Android 7.0+ device or emulator. The phone/emulator must be able to reach the configured backend URL; for an emulator, a server on the host machine is commonly `http://10.0.2.2:PORT`.

The server should return video objects like:

```json
{
  "id": "123",
  "title": "Example",
  "duration": 42,
  "size_bytes": 123456,
  "created_at": "2026-01-01T00:00:00.000Z",
  "thumbnail": "/thumbnails/123.jpg",
  "fmp4_path": "/stream/123.mp4"
}
```

If the server is not configured yet, the app still supports **Play URL** and **Open local video** from the gallery toolbar.
