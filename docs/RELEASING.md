# Releasing

## One-time setup (owner only — do this before the first `v*` tag)

1. Generate the release keystore **locally** (never commit it):
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias dift \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add four repository secrets (GitHub → Settings → Secrets and variables → Actions):
   - `KEYSTORE_BASE64` — `base64 -w0 release.jks`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `dift`), `KEY_PASSWORD`
3. **Back up `release.jks` and the passwords offline** (e.g. printed + password manager).
   If the key is lost or rotated, the phone rejects updates with a signature mismatch — the
   only way forward is uninstall/reinstall, which **destroys all app data** on the device.

## Cutting a release

```bash
git tag v0.3.0 && git push origin v0.3.0
```

That's all: `release.yml` builds `dift-v0.3.0.apk`, signs it with the keystore from secrets,
and attaches it to an auto-generated GitHub Release. Obtainium on the phone picks it up.

Rules:
- **Semver, monotonically increasing, never re-tag.** `versionCode` is computed as
  `major*10000 + minor*100 + patch` — re-tagging or going backwards breaks in-place updates.
- Before tagging: CI green on the commit, and the relevant docs/SMOKE_TEST.md items pass on
  the device.
- Release notes are auto-generated from commits/PRs; keep commit messages user-readable.

## Versioning in the build

The workflow passes `-PversionName=<tag minus v>`; `app/build.gradle.kts` derives
`versionCode` from it. Local builds without the property get `0.0.0-dev` / versionCode 1.
`assembleRelease` without keystore env vars falls back to **debug signing** so the task never
fails locally — such APKs are for local testing only and must never be attached to a Release.
