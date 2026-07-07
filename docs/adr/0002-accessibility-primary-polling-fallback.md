# ADR-0002: Accessibility service is the primary detector; usage-event polling is the fallback

**Status**: accepted (2026-07)

## Decision

Foreground-app detection uses an `AccessibilityService` (`TYPE_WINDOW_STATE_CHANGED`,
`canRetrieveWindowContent="false"`) as the primary source. When the service is disabled, a
`specialUse` foreground service polls `UsageStatsManager.queryEvents()` every ~1 s while the
screen is interactive. Both implement one `ForegroundAppDetector` interface.

## Rationale

- Accessibility events are instant (<100 ms), event-driven, and battery-cheap — polling alone
  adds 1–3 s of block latency and constant wakeups.
- Polling alone also cannot home-kick (needs the accessibility global action).
- Accessibility alone is fragile: the user can toggle it off; a fallback keeps blocking alive
  (overlay-only, degraded).
- **Rejected: UsageStats polling as the only mechanism** (latency, battery, no home-kick).
- **Rejected: reading window content** — Dift never needs it; a minimal config is the privacy
  posture and the defense against scope creep (CLAUDE.md invariant #8).

## Consequences

Onboarding must script the Android 13+ restricted-settings walkthrough (see
ANDROID_CONSTRAINTS.md). A watchdog notifies when the service is disabled.
