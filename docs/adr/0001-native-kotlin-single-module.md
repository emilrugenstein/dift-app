# ADR-0001: Native Kotlin + Compose, single Gradle module

**Status**: accepted (2026-07)

## Decision

Build Dift as a native Kotlin app with Jetpack Compose, in a single Gradle module (`:app`)
with package-level layering (`domain/`, `data/`, `system/`, `ui/`, `di/`).

## Rationale

- The core of the app — UsageStatsManager, AccessibilityService, overlay windows, typed
  foreground services — is Android-only and must be Kotlin regardless of UI framework.
- **Rejected: Flutter.** It would only cover the dashboard/settings UI behind a platform
  channel: a second language and a serialization boundary for zero cross-platform benefit
  (the required APIs have no iOS equivalent), and the blocking overlay would still be native.
- **Rejected: multi-module.** Compile-time layer separation is not worth doubling the Gradle
  surface an agent can break. Domain purity is enforced just as hard by the Konsist test
  (`DomainPurityTest`), which fails CI on any `android.*` import in `domain/`.

## Consequences

One language, one toolchain, predictable structure for LLM iteration. Adding a module later
requires a superseding ADR.
