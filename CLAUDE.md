# Dift — agent contract

Dift is a **personal, local-only** Android app for one device (Fairphone 6, Android 15/16,
minSdk 35): it tracks screen-time from UsageStats events and **aggressively blocks apps**
(full-screen overlay + home-kick) based on rules. No backend, no analytics, no network — ever.
Distribution is a signed APK on GitHub Releases, installed via Obtainium. Kotlin + Jetpack
Compose, single Gradle module.

## Commands

```bash
./gradlew detekt lint testDebugUnitTest   # static analysis + unit tests (what CI runs)
./gradlew assembleDebug                   # installable debug APK
./gradlew assembleRelease                 # falls back to debug signing without env vars
```

There is no CI-only magic: `.github/workflows/ci.yml` runs exactly these commands.
Releases: push a tag `vX.Y.Z` → signed APK on a GitHub Release (docs/RELEASING.md).

## Architecture map

```
app/src/main/kotlin/app/dift/
  domain/   PURE KOTLIN (no android.*/androidx.*). BlockEngine, DebtReducer, NightTimeline,
            SessionDeriver, models, SafetyDenylist. Enforced by .../arch/DomainPurityTest.kt.
  data/     Room entities/DAOs, DataStore settings, repositories.
  system/   Android machinery: accessibility service, fallback monitor, overlay host,
            blocking coordinator, usage ingester, workers, receivers, permission checks.
  ui/       Compose. One folder per screen: <name>Screen.kt + <name>ViewModel.kt.
  di/       Hilt modules.
```

Dift does two things: (1) **usage-debt blocking** — the only blocking mechanism; time-windowed
"blocks" ration continuous use and repay it with equal lockouts (docs/features/usage-debt.md),
and (2) a **night-aligned usage overview** that infers sleep (docs/features/usage-overview.md).

Runtime flow: detection (accessibility service, or polling fallback) → `ForegroundAppTracker`
→ `BlockingCoordinator`, which runs the pure `BlockEngine` (which block is in force) + `DebtReducer`
(burst/cooldown machine) and drives two overlays via `OverlayComposeHost`: the full-screen lockout
and the small corner indicator. No home-kick, no Activity. Details + diagram: docs/ARCHITECTURE.md.

## Invariants — violating any of these is a bug, not a refactor

1. **No network permission, ever.** The manifest must never gain `INTERNET` or any other
   network capability.
2. **`domain/` never imports `android.*` or `androidx.*`** and never reads the wall clock
   (time is an injected parameter). Enforced by `DomainPurityTest`; fix the code, never the test.
3. **Blocking never launches an Activity.** All blocking UI goes through `OverlayComposeHost`
   (WindowManager + TYPE_APPLICATION_OVERLAY). See ADR-0003 — service-launched activities are
   unreliable under Android 15/16 background-activity-launch restrictions.
4. **Never remove entries from `SafetyDenylist`** (dialer/Settings/SystemUI/launcher/Dift itself).
   Blocking the dialer is a safety hazard.
5. **All user-facing strings live in `res/values/strings.xml`.** No hardcoded UI text in Kotlin.
6. **One screen + one ViewModel per folder** under `ui/screens/`.
7. **Room schema changes require a migration and a committed schema JSON** (app/schemas/).
8. **The accessibility service keeps `canRetrieveWindowContent="false"`.** Dift never reads
   screen content — only window-change events.
9. **Dependencies are declared only in `gradle/libs.versions.toml`.**
10. If you touched `system/`, tell the user which items of docs/SMOKE_TEST.md to re-run —
    services, overlays, and permission flows cannot be verified in CI.

## Before you write code

- Android policy around services, overlays, usage stats, and permissions shifts every release,
  and models reliably hallucinate outdated APIs (`killBackgroundProcesses`, Activity-based
  block screens, `MOVE_TO_FOREGROUND`, …). **Read docs/ANDROID_CONSTRAINTS.md first** whenever
  work touches `system/`, the manifest, or permissions.
- Feature behavior is specified in docs/features/*.md — spec first, then code, then tests.
- Key past decisions and their rejected alternatives: docs/adr/.
