# Auto Call Alarm (Android) — single-file version

At a fixed time you choose, this app:
1. (optionally) opens the system Clock app with an alarm set, and
2. automatically dials a phone number you specify.

## Files
Android projects always need a manifest and Gradle build files — that part can't
be avoided — but all the actual app logic (the screen, the trigger, and the
reboot handler) lives in **one file**:

- `app/src/main/java/com/example/autocallalarm/MainActivity.kt` — everything:
  the UI (built in code, no separate XML layout), `AlarmReceiver` (fires at the
  scheduled time), and `BootReceiver` (re-arms the schedule after a restart).
- `app/src/main/AndroidManifest.xml` — declares permissions and registers the
  activity/receivers.
- `app/build.gradle`, `build.gradle`, `settings.gradle` — standard Gradle setup.

## Permission notes
- `CALL_PHONE` is a "dangerous" runtime permission — the app prompts you once.
  If you deny it, auto-dial falls back to just opening the dialer pre-filled.
- Android 12+ needs exact-alarm permission; the app will send you to the
  settings screen for it if it's missing.
- Intended for personal use (e.g. calling one contact you specify) — not for
  dialing numbers you don't have a direct relationship with.

## Building it
- Android Studio: open the `AutoCallAlarm` folder and hit Run.
- Command line: `./gradlew assembleDebug`, then install
  `app/build/outputs/apk/debug/app-debug.apk`.
- Phone-only: push this folder to a GitHub repo — the included
  `.github/workflows/build.yml` builds the APK automatically and makes it
  downloadable from the Actions tab.
