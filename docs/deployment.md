# Deployment & Setup Guide — Darzi Ledger

## 1. Prerequisites
- Android SDK (`minSdk = 24`, `targetSdk = 36`, `compileSdk = 36`)
- Java 11+
- Configured Firebase project (`app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml`)

## 2. Firebase Console Configuration
1. **Firestore Enterprise Database**: Provisioned using the named database ID defined in `R.string.firestore_database_id` (`app/src/main/res/values/firebase_applet_config.xml`).
2. **Firestore Security Rules**: Deploy `/firestore.rules` via Firebase CLI or the project deployment workflow so strict single-shop ownership (`request.auth.uid == shopId`), 2-device limits, and schema validations are enforced on the server.
3. **Authentication Providers**:
   - **Google Sign-In**: Enabled automatically with your debug/release SHA-1 and SHA-256 certificate fingerprints registered in Firebase Console.
   - **Email/Password Auth**: In **Firebase Console → Authentication → Sign-in method**, click **Email/Password**, toggle **Enable** on, and save so owners can also register and sign in with email and password.

## 3. Release Signing & Build
1. Do **not** commit production upload keystores or secrets to source control. Provide release keystore environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) in your CI/release environment.
2. Build a debug APK for testing or an Android App Bundle (`.aab`) for Play Console distribution:

```bash
gradle :app:assembleDebug
gradle :app:bundleRelease
```

## 4. Google Play Policy Checklist
- **No broad storage permissions**: JSON export/restore uses the zero-permission Android Storage Access Framework (`CreateDocument` / `OpenDocument`).
- **No location permissions for Bluetooth**: `BLUETOOTH_SCAN` is declared with `android:usesPermissionFlags="neverForLocation"` on Android 12+ and legacy Bluetooth permissions are capped with `maxSdkVersion="30"`.
- **Clean Metadata**: App title is `"Darzi Ledger"` (under 30 characters, no emojis or promotional claims).

## 5. Localization Readiness
All user-facing navigation labels, form fields, measurement terms, status badges, and backup dialogs are centralized in `app/src/main/res/values/strings.xml`, ready for an Urdu (`values-ur/strings.xml`) translation overlay without Kotlin code changes.
