# Feature spec: usage-debt blocking (the core feature)

Usage-debt is Dift's **only** blocking mechanism. There are no allow/deny lists, daily limits,
or schedule blocks any more — just *blocks*, each a window in which continuous phone use is
rationed and repaid.

## A "block"

A block (`Block`, persisted in `block_rules` — see docs/DATA_MODEL.md for why the table keeps
its old name) has:

- **name** — free text, e.g. "Night", "Night (weekend)", "Work".
- **days mask** — which weekdays the block's window *starts* on (bit 0 = Monday).
- **window** — `startMinuteOfDay` .. `endMinuteOfDay`. Windows may wrap past midnight
  (22:30 → 06:00); a wrapping window belongs to its **start day's** mask (so a Friday-night
  block covers Fri 22:30 → Sat 06:00 even though most of it is Saturday).
- **maxBurstSeconds** — the longest *continuous* stretch of blockable use allowed before the
  device locks. Default **60**.
- **exempt packages** — apps that stay usable during the block and never accrue debt. Default:
  **none** (everything opened while unlocked is blocked). Stored in `rule_apps`, repurposed
  from its M2 meaning. The `SafetyDenylist` (dialer, Settings, SystemUI, launcher, IME, Dift)
  is *always* exempt on top of this.

Blocks are always device-wide and always HARD — there is no unblock/grant/friction path. The
only escape is time.

### Multiple blocks — strictest wins

Several blocks may be active at once (e.g. a permissive "Work" block and a strict "Focus"
block overlapping 09:00–12:00). The **strictest active block wins**: the enabled block whose
window is active *now* with the **smallest `maxBurstSeconds`** (ties broken by lowest id). That
one block's `maxBurstSeconds` and exempt set define the current regime; the others are ignored
while it wins. If no block's window is active, nothing is enforced.

## Semantics — the pure `DebtReducer`

State (`DebtState`): a **burst** (continuous-use accumulator, in memory only) and a **cooldown**
(an absolute wall-clock deadline, persisted). At most one is "live" at a time.

1. **Unlock-gated.** Nothing is enforced while the keyguard is up — SOS calls, the flashlight
   tile, and the lockscreen camera are untouched (they live before the unlock / in SystemUI).
2. **Burst accrual.** While unlocked, screen interactive, inside the active block's window, no
   block overlay showing, **and the foreground app is blockable** (not exempt, not on the
   `SafetyDenylist`), a burst timer accumulates. Switching between two blockable apps keeps
   accruing; opening an **exempt** app (or the launcher) **pauses** the burst — it neither grows
   nor resets, so "continuous" survives a glance at an allowed app.
3. **Cap hit → lock.** When the burst reaches `maxBurstSeconds`, the device locks: full-screen
   countdown overlay, `cooldownUntil = now + maxBurstSeconds` (1:1).
4. **Early stop → lock.** Locking or screen-off after *X* < cap seconds ends the burst and sets
   `cooldownUntil = now + X` (1:1).
5. **Cooldown is a wall-clock deadline.** It counts down whether the phone is locked or not.
   Lock at 45 s used → 45 s cooldown; stay locked 15 s → unlock shows 30 s remaining
   (`45 − 15`). Serving-time on the block overlay is free (never accrues a new burst).
6. **Persistence.** Only the cooldown (`cooldownStartedAt` / `cooldownUntil`) is persisted
   (DataStore); it must survive process death, locking, and reboot. The burst is ephemeral —
   a process restart breaks "continuous", which is correct.
7. **Window exit.** Leaving the window cancels an in-flight burst (no debt) but an already-set
   cooldown still counts down to completion (leaving at 05:59 owing 40 s still costs 40 s).
   While a cooldown bleeds past the window, only the `SafetyDenylist` is exempt.
8. Outcome `DEBT_SERVED` is logged to `block_events` when a cooldown completes.

## The corner indicator (separate small overlay)

A small always-on indicator sits in the **top-right corner** whenever a block window is active
or a cooldown is running (`IndicatorState`):

- **Counting** — a circular ring that **fills** 0 → full as the burst approaches the cap
  (`liveElapsed / maxBurst`). Frozen while paused on an exempt app; empty on the home screen.
- **Blocked** — replaced by a "blocked" tag while a cooldown is active; the ring **drains in
  reverse** (`remaining / total`) to show time left.
- **Hidden** — outside every window with no cooldown.

The indicator window is `TYPE_APPLICATION_OVERLAY`, not focusable and **not touchable** (taps
pass through), so it never interferes with the phone. The full-screen block overlay is a
*separate* window (touchable, so it consumes input to the app underneath) — ADR-0003:
blocking never launches an Activity.

## Edge cases the `DebtReducerTest` must cover

- Burst reaching the cap on a tick fires an equal cooldown and clears the burst.
- Lock at second 45 → 45 s cooldown; lock at second 0 → nothing.
- Cooldown outlasting the window; reboot mid-cooldown (restore keeps counting); rapid
  lock/unlock keeps the cooldown intact.
- Exempt-app pause freezes the accumulator; returning to a blockable app resumes it.
- Strictest-active-block resolution across overlapping windows and weekday masks.
