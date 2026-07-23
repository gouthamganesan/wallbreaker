# Wallbreaker

One-tap Android share target that saves Medium articles to Instapaper, unlocked through [Freedium](https://freedium.cfd).

Reading a Medium article on your phone and saving it to Instapaper used to take ~7 taps (open in browser → copy URL → share again → …). Wallbreaker collapses that to: **Share → “Save to Instapaper”** → a 3-second confirmation → back to what you were doing.

## What it does

- Registers as an `ACTION_SEND` (`text/plain`) share target labelled **Save to Instapaper**.
- Wraps `*.medium.com` article URLs through the **Freedium** mirror to bypass the paywall, then POSTs to the Instapaper **Simple API** (`/api/add`).
- Non-Medium URLs are saved as-is (Freedium only unlocks Medium).
- Shows a small translucent card (Saving → Saved, with the article title) that auto-dismisses after 3 seconds, dropping you back where you were.
- The launcher icon opens a one-screen config: Instapaper email + password, with a **Verify** button that hits `/api/authenticate`.

## Design notes

- **Credentials** — the password is encrypted with an AES-GCM key generated in the **Android Keystore** (StrongBox where available) and stored as ciphertext; the username is plaintext (an email isn't the secret). The daily-driver build is the non-debuggable release with `allowBackup="false"`, so the stored ciphertext is useless off-device and `adb run-as` is refused. See [`CredentialStore.kt`](app/src/main/java/dev/goutham/wallbreaker/CredentialStore.kt).
- **Freedium mirror** — `.cfd` hosts are volatile (the original `freedium.cfd` went dead; the live mirror is `freedium-mirror.cfd`). The base is a configurable ordered fallback chain (`Freedium.BASES`), not a hardcoded string. Wrapping is gated to `*.medium.com` URLs whose slug ends in a 12-hex post id. See [`Freedium.kt`](app/src/main/java/dev/goutham/wallbreaker/Freedium.kt).
- **UI** — Jetpack Compose. The share overlay is a translucent `Activity` (not a notification), so it needs no runtime permissions beyond `INTERNET`.

## Build & test

No system Gradle needed — use the wrapper. Requires a JDK 17+ (builds with JDK 21) and the Android SDK.

```bash
# If no JDK is on PATH, point at Android Studio's bundled one:
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew :app:testDebugUnitTest     # unit tests: URL extraction + Freedium gate
./gradlew :app:installRelease        # build + install the release on a connected device
```

Fire the share target directly (no share sheet needed) for a smoke test:

```bash
adb shell am start -n dev.goutham.wallbreaker/.ShareActivity \
  -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://medium.com/@author/some-post-1a2b3c4d5e6f"
```

## Stack

Kotlin · Jetpack Compose · Android Keystore · `HttpURLConnection` · minSdk 29 / targetSdk 36.
