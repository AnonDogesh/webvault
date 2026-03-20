# Webvault

## Building

This repository includes a GitHub Actions workflow at `.github/workflows/build-apk.yml` that builds, signs, and publishes a release APK.

### Required GitHub Secrets

Configure the following repository secrets before running release builds:

- `KEYSTORE_FILE`: Base64-encoded keystore file contents.
- `KEY_ALIAS`: Keystore key alias.
- `KEY_PASSWORD`: Password for the key alias.
- `STORE_PASSWORD`: Password for the keystore.
- `KEYSTORE_TYPE` (optional): Keystore type, usually `JKS` (default) or `PKCS12`.

### How to create `KEYSTORE_FILE`

From your local machine:

```bash
base64 -w 0 your-release-key.jks
```

Copy the output and save it as the `KEYSTORE_FILE` secret in GitHub.

> Important: The secret must contain only the base64 string of the keystore bytes (no extra text).

### Workflow behavior

- Runs on every pull request and on push to `main`.
- Builds release APK via `./gradlew assembleRelease`.
- Decodes and uses signing keystore from secrets.
- Uploads signed APK artifact named **Webvault-release.apk** (7-day retention).
- On push to `main`, also creates a GitHub Release and attaches the signed APK.
