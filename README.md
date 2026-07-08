# Dift

A personal, **local-only** Android app that tracks screen time and **aggressively blocks
apps** — full-screen overlays, home-kicks, and a punitive night mode where every second of
late-night use costs an equal second of lockout.

Built for a single device (Fairphone 6, Android 15/16). No accounts, no cloud, no analytics,
no network permission at all: everything the app knows stays on the phone.

## Features

- **Screen-time dashboard** — today's total, top apps, and a 7- or 30-day trend, derived
  from usage events for accuracy (not the coarse system buckets).
- **Permissions onboarding** — a guided checklist, including the Android 13+ restricted-settings
  walkthrough that sideloaded apps need for accessibility.
- **Manual blocklist** — instant full-screen block (overlay + home-kick) when a listed app opens.
- **Daily limits & schedules** — per-app time budgets and time-window blocks.
- **Night usage-debt mode** — after 22:30, every usage burst (max 60 s) is punished with an
  equal-length device-wide lockout; enforcement is unlock-gated, so SOS calls, the flashlight
  tile, and the lockscreen camera stay available.
- **Per-rule strictness** — tap-to-unblock, friction (wait + typed phrase), or hard lockout.
- **Block history** — a timeline of every block and how it was resolved.

Everything runs locally. There is no network permission in the manifest, by design.

### Build status

Milestones M0–M5 are implemented. Remaining before a `v1.0.0` release: on-device smoke
testing (docs/SMOKE_TEST.md) and the signing keystore + GitHub secrets (docs/RELEASING.md).

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
