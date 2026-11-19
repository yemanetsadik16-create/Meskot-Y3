# Meskot · መስኮት (Android)

Bilingual (English / አማርኛ) Ethiopian & Habesha community social network built with Kotlin, Jetpack Compose, Material 3, and Firebase.

## GitHub Actions APK Publishing Workflow

This repository includes an automated GitHub Actions workflow at `.github/workflows/android-apk-release.yml` that builds and publishes the Android APK automatically:

- **Push to `main` / `master`**: Builds `Meskot-release.apk` and `Meskot-debug.apk`, uploads them as workflow artifacts, and updates the rolling `latest` GitHub Release.
- **Push a version tag (e.g., `v1.0.0`)**: Publishes a full versioned GitHub Release with `Meskot-release.apk` attached.
- **Manual trigger**: Go to **Actions → Build & Publish Meskot APK → Run workflow** to trigger a build and optionally specify a release tag.

### Optional GitHub Repository Secrets
The workflow automatically decodes `debug.keystore.base64` and uses `app/google-services.json` out of the box. For custom production signing, you may optionally configure:
- `KEYSTORE_BASE64`: Base64-encoded Android release keystore.
- `GOOGLE_SERVICES_JSON_BASE64`: Base64-encoded `google-services.json` override.

## Local Build

```bash
gradle :app:assembleRelease
```

The generated APK is written to `app/build/outputs/apk/release/app-release.apk`.
