# Getting the first APK built (10 minutes, no software to install)

1. Create a free account at https://github.com (use an Altron company e-mail, not a personal one).
2. Click **New repository** → name `altron-android` → **Private** → Create.
3. On the repository page click **uploading an existing file**, drag the whole contents of this
   `altron-android` folder (including the hidden `.github` folder — easiest: zip the folder, unzip on the page, or use
   GitHub Desktop) → **Commit changes**.
4. Open the **Actions** tab → the workflow "Build Altron Android app" starts automatically (about 5–8 minutes).
   When it turns green, click it → **Artifacts → altron-app** → download the zip: it contains
   `altron-app.apk` and `version.json`.
5. Upload those two files to the website folder `app/dl/` (Hostinger File Manager). Staff can now install from
   https://demo.altron.com.qa/app/get-app.php and Admin → Android App & Devices shows every phone.

## Later (recommended, once the pilot works)
- **Signing key** (so updates install over the old version without uninstalling): follow README "Create the
  signing key once" and add the four `KEYSTORE_*` secrets in Settings → Secrets and variables → Actions.
- **Push notifications**: create a Firebase project (console.firebase.google.com) → Add Android app with package
  `qa.altron.app` → download `google-services.json` → paste its content into secret `GOOGLE_SERVICES_JSON`.
- **Auto-publish**: add `FTP_HOST`, `FTP_USER`, `FTP_PASS`, `FTP_DIR=/public_html/app/dl/` secrets (Hostinger →
  Files → FTP Accounts) and every build lands on the website by itself.

Give Claude the repository link (or invite `oper@altron.com.qa`'s GitHub user as a collaborator) and it will
handle build errors, versions and releases from there.
