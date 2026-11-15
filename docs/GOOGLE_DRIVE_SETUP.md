# AuraNotes Google Authentication & Google Drive Setup

## Provisioned OAuth & Firebase Configuration

- **Google Cloud Project ID**: `gen-lang-client-0607036828`
- **Project Number**: `371617000820`
- **Android Application ID**: `com.aistudio.auranotes.hqztxy`
- **Firebase Config File**: `app/google-services.json` (generates `R.string.default_web_client_id` automatically)
- **Debug Signing SHA-1**: `38:0A:DA:3E:95:8C:B3:99:AE:2E:3C:89:BD:DE:6F:35:A1:99:89:A0`
- **OAuth 2.0 Scopes (Least Privilege)**:
  - `openid`
  - `https://www.googleapis.com/auth/userinfo.email`
  - `https://www.googleapis.com/auth/userinfo.profile`
  - `https://www.googleapis.com/auth/drive.appdata`

## Required Google Cloud Console Verification Steps

Before distributing or testing Drive uploads on a live device, verify these two items in Google Cloud Console (`https://console.cloud.google.com/apis/dashboard?project=gen-lang-client-0607036828`):

1. **Verify Google Drive API Is Enabled**:
   - Navigate to **APIs & Services -> Library**.
   - Search for **Google Drive API** (`drive.googleapis.com`).
   - Confirm status is **Enabled**.
2. **Add Release Keystore SHA-1 (Before Publishing Release APKs/AABs)**:
   - Run `keytool -list -v -keystore my-upload-key.jks -alias upload` to obtain your release SHA-1.
   - Add the release Android OAuth 2.0 client / SHA-1 fingerprint in Google Cloud Console -> **APIs & Services -> Credentials** (and Firebase Console Project Settings) for package `com.aistudio.auranotes.hqztxy`.

## Security & Architecture Notes

- **Zero Stored Credentials**: Passwords and OAuth refresh tokens are never persisted locally. Short-lived access tokens are requested dynamically from Google Play Services `Identity.getAuthorizationClient(...)` at sync time.
- **Scoped Access**: Using `drive.appdata` limits AuraNotes exclusively to its hidden application folder in Google Drive (`appDataFolder`). The app cannot list, read, or modify personal Google Drive documents.
- **Night-Time Smart Scheduler**: Implemented with Jetpack `WorkManager` targeting a configurable local nighttime window (default `12:00 AM – 5:00 AM`) with unmetered Wi-Fi, battery-not-low, charging, and idle constraints and exponential retry backoff.
