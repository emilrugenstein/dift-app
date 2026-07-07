# Feature spec: night usage-debt mode (M3) — flagship

After the configured evening time, phone use is throttled to short bursts, and every second
of use is punished with an equal second of total lockout.

## Rule parameters (`type = USAGE_DEBT`)

- Window: start/end minute-of-day (default 22:30 → 06:00) + weekday mask; wrapping windows
  belong to the start day's mask.
- `maxBurstSeconds` (default 60), `debtRatio` (default 1.0).
- Always `deviceWide = true` and `strictness = HARD` (no grants, no unblock path).

## Semantics (implemented as pure `DebtReducer` + RuleEngine device-wide verdict)

1. **Unlock-gated.** Enforcement engages only while the keyguard is dismissed. While locked,
   nothing is enforced — SOS/emergency calls, the flashlight tile, and the lockscreen camera
   are untouched by design (they live before the unlock or in SystemUI).
2. **Burst accrual.** While unlocked, screen interactive, inside the window, and no block
   overlay showing, a usage timer runs from `activeBurstStartedAt`.
3. **Cap hit.** At `maxBurstSeconds` of continuous use, the block fires immediately:
   countdown overlay + home-kick, `debtUntil = now + burstSeconds * debtRatio`.
4. **Early stop.** Locking or screen-off after X < cap seconds ends the burst and sets
   `debtUntil = now + X * debtRatio`.
5. **During debt.** Unlocking shows the full-screen countdown overlay over everything except
   `SafetyDenylist` apps (dialer stays reachable). Time on the debt overlay never accrues
   burst.
6. **Persistence.** `activeBurstStartedAt` / `debtUntil` live in DataStore; process death,
   locking, or reboot must not clear an active debt (smoke-test item 12).
7. **Window exit.** Leaving the window cancels burst accrual; an already-running debt
   countdown still completes (leaving at 05:59 with 40 s debt still costs 40 s).
8. Outcome `DEBT_SERVED` is logged to `block_events` when a debt countdown completes.

## Edge cases the DebtReducer tests must cover

- Burst spanning the window start (use started 22:29): accrual begins at window start.
- Lock at second 59, unlock 1 s later: remaining debt ≈ 59 s, not reset.
- Debt outlasting the window end; reboot mid-debt; rapid lock/unlock cycles.
- Multiple bursts back-to-back: overlay-close → immediate new burst starts at 0.
