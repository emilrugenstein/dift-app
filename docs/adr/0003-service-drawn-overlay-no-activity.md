# ADR-0003: Blocking UI is a service-drawn overlay window — never an Activity

**Status**: accepted (2026-07)

## Decision

All blocking UI is drawn by attaching a ComposeView directly via `WindowManager.addView`
with `TYPE_APPLICATION_OVERLAY` (`OverlayComposeHost`). Blocking never calls `startActivity`.

## Rationale

- Android 15/16 background-activity-launch (BAL) restrictions make service-initiated activity
  starts unreliable; the SYSTEM_ALERT_WINDOW exemption is being phased out. An unreliable
  block is a useless block.
- A WindowManager overlay appears instantly, cannot be dismissed by recents/back, and works
  from both the accessibility service and the fallback monitor.
- **Rejected: full-screen blocking Activity** — the "obvious" design that LLMs default to;
  it intermittently fails to launch on modern Android. Do not reintroduce it.

## Consequences

- `OverlayComposeHost` hand-wires the ViewTree owners (Lifecycle/ViewModelStore/
  SavedStateRegistry) that a ComposeView outside an Activity requires.
- The window stays focusable so FRICTION unblocking can use the soft keyboard (verified by
  the M0 spike; smoke-test item 1).
- Home-kick is only available while the accessibility service is bound; fallback mode is
  overlay-only (documented degradation).
