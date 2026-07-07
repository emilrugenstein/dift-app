# ADR-0004: The rule engine and debt reducer are pure Kotlin functions

**Status**: accepted (2026-07)

## Decision

All blocking decisions are made by pure functions in `domain/`:

- `RuleEngine.evaluate(input) -> Verdict` — input carries the package, its matching rules,
  today's usage, active grants, the night-debt state, and `now: ZonedDateTime`.
- `DebtReducer.reduce(state, event, now) -> DebtState` — events: unlock, lock, screen-off,
  tick, window-enter/exit.

No `android.*` imports, no clock reads, no I/O. Enforced by `DomainPurityTest` (Konsist).

## Rationale

Blocking correctness is the product. Purity makes every edge case — midnight-wrapping
windows, limit-exactly-reached, expired grants, debt across reboots — a fast, deterministic
JVM table test instead of an on-device repro. It is also the safest possible surface for LLM
agents: behavior changes are fully visible in tests.

**Rejected: deciding inside the services** — untestable, and couples policy to Android APIs.

## Consequences

Resolution policy lives in the engine and its tests: most-strict rule wins
(`HARD > FRICTION > TAP_THROUGH`); grants never defeat `HARD`; wrapping schedule windows
belong to their start day's weekday mask; daily limits reset at local midnight.
