# Feature spec: blocking (M2, extended M4)

## Behavior

When the foreground app has a `Block` verdict:

1. The full-screen block overlay appears within 500 ms of the app reaching the foreground
   (smoke-test item 6), drawn via `OverlayComposeHost` — never an Activity (ADR-0003).
2. If the accessibility service is bound, a home-kick (`GLOBAL_ACTION_HOME`) fires as the
   primary enforcement; the overlay is the explanation the user sees. In fallback (polling)
   mode there is no home-kick — overlay only.
3. Enforcement re-fires on every window event while the verdict holds (defeats fast re-entry
   and overlay-over-overlay races).
4. Every enforcement writes a `block_events` row (reason, outcome, detection latency ms).

## Overlay content per strictness

| Strictness | Overlay shows | Unblock path |
|---|---|---|
| `TAP_THROUGH` | app name, rule name, "unblock" button | one tap → grant for `grantMinutes` |
| `FRICTION` | wait countdown (`frictionDelaySeconds`), then a phrase field | wait + type the exact phrase → grant for `grantMinutes` |
| `HARD` | reason + `blockedUntil` time | none; overlay persists until the condition ends |

Grants are written to `unblock_grants` and evaluated by the RuleEngine; grants never defeat
`HARD` rules.

## Non-goals / accepted limits

- The user can always disable the accessibility service in Settings — the OS hides overlays
  there by design. Dift is hard against *habit*, not adversarial against its owner. The
  watchdog posts a notification when the service dies; no arms race beyond that.
- No blocking of the `SafetyDenylist` (dialer, Settings, SystemUI, launcher, IME, Dift):
  the rule editor refuses these packages and the coordinator hides the overlay when a
  denylisted app is foreground.
- No process killing (impossible without root — see ANDROID_CONSTRAINTS.md).
