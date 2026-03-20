# Webvault

## Building

This repository includes a GitHub Actions workflow at `.github/workflows/build-apk.yml` that builds and uploads an installable debug APK.

### Workflow behavior

- Runs on every pull request and on push to `main`.
- Builds the debug APK via `./gradlew assembleDebug`.
- Uploads the generated APK artifact as **Webvault-debug.apk** with a 7-day retention period.

### Why debug APKs are used in CI

Debug APKs are signed automatically with the Android debug keystore, so they can be installed directly for testing without requiring repository signing secrets.
