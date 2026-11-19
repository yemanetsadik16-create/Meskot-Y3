# Meskot · መስኮት (Android)

Bilingual (English / አማርኛ) Ethiopian & Habesha community social network built with Kotlin, Jetpack Compose, Material 3, and Firebase.

## GitHub Actions APK Publishing Workflow

This repository includes an automated GitHub Actions workflow at `.github/workflows/android-apk-release.yml` that builds and publishes the Android APK automatically.

### Required One-Time GitHub Setup
AI Studio omits `app/google-services.json` when pushing to GitHub for security. Add it once as a GitHub Actions secret:

1. In your GitHub repository, open **Settings → Secrets and variables → Actions**.
2. Click **New repository secret**.
3. Set **Name** to `GOOGLE_SERVICES_JSON`.
4. Paste the full contents of your `google-services.json` file into **Secret** and save.
5. Re-run the workflow in the **Actions** tab (or push a commit / `v1.0.0` tag).

*(Optionally, you can also set `GOOGLE_SERVICES_JSON_BASE64` or a custom `KEYSTORE_BASE64` secret.)*

### Triggers
- **Push to `main` / `master`**: Builds `Meskot-release.apk` and `Meskot-debug.apk`, uploads them as workflow artifacts, and updates the rolling `latest` GitHub Release.
- **Push a version tag (e.g., `v1.0.0`)**: Publishes a full versioned GitHub Release with `Meskot-release.apk` attached.
- **Manual trigger**: Go to **Actions → Build & Publish Meskot APK → Run workflow**.
