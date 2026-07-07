# Dift

A personal, **local-only** Android app that tracks screen time and **aggressively blocks
apps** — full-screen overlays, home-kicks, and a punitive night mode where every second of
late-night use costs an equal second of lockout.

Built for a single device (Fairphone 6, Android 15/16). No accounts, no cloud, no analytics,
no network permission at all: everything the app knows stays on the phone.

## Features (by milestone)

- **M0** — app shell, CI, release pipeline, overlay tech spike ← *current*
- **M1** — permissions onboarding + screen-time dashboard (event-derived, accurate)
- **M2** — manual blocklist: instant full-screen block when a listed app opens
- **M3** — night usage-debt mode: after 22:30, every usage burst (max 60 s) is punished with
  an equal-length device-wide lockout; only lockscreen functions (SOS, flashlight, camera)
  stay available
- **M4** — daily time limits, schedule windows, per-rule strictness (tap / friction / hard),
  block history
- **M5** — polish: trends, battery audit, final icon

## Install & update (Obtainium)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases).
2. Add this repository's URL as an app source; Obtainium picks up the APK attached to the
   latest GitHub Release.
3. Updates: bump happens automatically whenever a new `vX.Y.Z` release is published.

First-time setup requires granting special permissions (usage access, display over other
apps, accessibility). Sideloaded apps hit Android's *restricted settings* gate — the app's
onboarding walks through the exact steps.

## Development

Kotlin + Jetpack Compose, single Gradle module. See [CLAUDE.md](CLAUDE.md) for the build
commands and architecture contract, and [docs/](docs/) for architecture, Android platform
constraints, data model, releasing, and feature specs. CI builds an installable debug APK on
every push; tagging `vX.Y.Z` publishes a signed release.
