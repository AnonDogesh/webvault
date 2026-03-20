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
- Always builds the release APK via `./gradlew assembleRelease`.
- Signs the APK only when all required signing secrets are available.
- Uploads **Webvault-release.apk** when signing succeeds, otherwise uploads **Webvault-release-unsigned.apk**.
- On push to `main`, creates a GitHub Release only when signing secrets are configured.
