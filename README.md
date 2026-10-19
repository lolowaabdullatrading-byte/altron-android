# Altron Android app

Native Android shell around https://demo.altron.com.qa/app with **background location sharing** for field staff
(foreground service, offline buffer, auto start/stop on check-in/check-out), native push (optional Firebase),
guided permissions, and self-update from the website.

## How it works
- **WebView** loads the live web app (all modules stay server-side; deploy PHP as usual, nothing to rebuild).
- **Login screen** → `api/mobile/login.php` → device token + one-time `web_code` → WebView opens a logged-in session.
- **TrackingService** (foreground, "Location sharing on" notification) sends GPS every N seconds to `track_ping.php`
  with the device token. Every reply says whether tracking should stay on; `on:false` (check-out / auto-close /
  policy) stops the service. Points are buffered in SQLite when there is no signal.
- **StateWorker** (every 15 min + on boot + on app open) re-syncs with `api/mobile/state.php` so tracking
  resumes after reboot or if the OS killed the app.
- **Web bridge**: every staff page emits `AltronNative.setTrackState({...})` (see `inc/track.php`), so the
  service starts within seconds of a check-in inside the WebView.
- **SetupActivity**: Location → "All the time", Notifications, Battery unrestricted, brand-specific autostart hint.
- **PolicyActivity**: location policy (`api/mobile/policy.php`), acceptance stored via `api/mobile/consent.php`.

## Build (GitHub Actions - recommended)
1. Push this folder to a GitHub repository (branch `main`).
2. Add repository secrets (Settings → Secrets → Actions):
   - `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — release signing (see below). Without them the build is debug-signed (fine for a pilot, but updates must then always be debug-signed too).
   - `GOOGLE_SERVICES_JSON` — contents of Firebase `google-services.json` (optional; enables push).
   - `FTP_HOST`, `FTP_USER`, `FTP_PASS`, `FTP_DIR` (e.g. `/public_html/app/dl/`) — optional auto-publish to the website.
3. Every push to `main` builds `altron-app.apk` + `version.json` (Actions → artifact). Tag `v1.0.0` to also create a GitHub Release.
4. Upload the two files to `app/dl/` on the server (or let the FTP step do it). Staff install from `https://demo.altron.com.qa/app/get-app.php`; installed apps prompt "Update available".

### Create the signing key once (keep it safe — losing it means reinstalling the app on every phone)
```
keytool -genkeypair -v -keystore release.keystore -alias altron -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore > keystore.b64   # paste content into secret KEYSTORE_BASE64
```

## Build locally
Android Studio (Hedgehog+) → Open this folder → Build → Build APK. Optional `keystore.properties` (see example).

## Server side (already deployed)
`inc/mobile.php`, `api/mobile/{login,state,web_code,web_session,consent,fcm,logout,policy}.php`, `track_ping.php`
(bearer + batch), `admin/devices.php` (installed phones, permissions, revoke), `get-app.php` (download page), `dl/`.

## Notes
- Android needs the persistent notification while location runs in the background — this is by design and also
  satisfies PDPPL transparency.
- Xiaomi/Oppo/Vivo/Huawei/Samsung battery managers can still kill services; SetupActivity guides the person.
- Push without Firebase: reminders still arrive as Web Push when the web app is open; add Firebase for true native push.
