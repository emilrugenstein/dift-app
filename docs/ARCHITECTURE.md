# Architecture

Single Gradle module (`:app`), strict package layering. Kotlin + Jetpack Compose (Material 3),
Hilt, Room + DataStore, WorkManager. minSdk = targetSdk-ish single-device app (Fairphone 6,
Android 15/16) — no backward-compat code.

Dift has two features: **usage-debt blocking** (the only blocking mechanism —
docs/features/usage-debt.md) and a **night-aligned usage overview** (docs/features/usage-overview.md).

## Layers

| Package   | Contents | Rules |
|-----------|----------|-------|
| `domain/` | `BlockEngine`, `DebtReducer`, `NightTimeline`, `SessionDeriver`, models, `SafetyDenylist` | **Pure Kotlin.** No `android.*`/`androidx.*` imports, no wall-clock reads (time is a parameter). Enforced by `DomainPurityTest` (Konsist). |
| `data/`   | Room database (entities, DAOs), DataStore settings, repositories | Repositories are the only DB/DataStore consumers; expose Flows + suspend functions. |
| `system/` | Accessibility service, fallback monitor FGS, `ForegroundAppTracker`, `DeviceStateMonitor`, `BlockingCoordinator`, `OverlayComposeHost` + `OverlayController`/`IndicatorOverlayController`, `UsageStatsIngester`, workers, `BootReceiver`, `PermissionsChecker` | Android machinery. Services stay thin — logic in `domain/`, orchestration in `BlockingCoordinator`. |
| `ui/`     | Compose screens (one folder per screen: `XScreen.kt` + `XViewModel.kt`), theme, navigation, overlay content (`BlockScreenContent`, `IndicatorContent`) | ViewModels talk to repositories/coordinator only. All text via `strings.xml`. |
| `di/`     | Hilt modules | — |

## Runtime flow (blocking pipeline)

```
DiftAccessibilityService ─┐                        ┌→ OverlayController → BlockScreenContent
  (TYPE_WINDOW_STATE_     ├→ ForegroundAppTracker ─┤    (full-screen lockout, touch-consuming)
   CHANGED events)        │    StateFlow<String?>  └→ IndicatorOverlayController → IndicatorContent
FallbackMonitorService  ──┘                             (small top-right, click-through)
  (1 s queryEvents poll,           │
   screen-on only)                 ▼
DeviceStateMonitor ────→  BlockingCoordinator ──→ BlockEngine.activeBlock(now)   [pure]
  (unlock/lock/screen)       ▲                          DebtReducer.reduce(state, event)  [pure]
                     BlockRepo┘                          └→ BlockEventRepository (audit)
```

- **Detection** is an interface (`ForegroundAppDetector`) with two implementations:
  accessibility (primary, instant) and usage-events polling (fallback when accessibility is
  disabled). Both feed the same tracker.
- **`ForegroundAppTracker`** filters window events: ignores SystemUI, the current IME, and
  Dift's own overlay windows (otherwise showing an overlay re-enters the pipeline — infinite
  loop); accepts only classes that resolve to real Activities; exposes a debounced
  `StateFlow<String?>`.
- **`BlockingCoordinator`** (singleton) is the only orchestrator. Each second (and on every
  foreground/block/device change) it asks `BlockEngine` which block window is in force (strictest
  wins), feeds a `DebtEvent` to the pure `DebtReducer`, and acts on the result:
  - the burst accumulator fills toward the active block's `maxBurstSeconds`;
  - hitting the cap (or a lock mid-burst) banks an equal **cooldown** (an absolute deadline,
    persisted so it survives reboot);
  - while a cooldown is live and the foreground app is *blockable* (not exempt, not on the
    `SafetyDenylist`), the full-screen lockout shows; the corner indicator shows the fill
    (counting) or drain (blocked) fraction the whole time a window/cooldown is active.
  There is **no home-kick and no Activity** (ADR-0003): pressing home is always allowed because
  the launcher is on the `SafetyDenylist`, and any app launch re-raises the lockout.
- **Blocking UI** is drawn by `OverlayComposeHost` (TYPE_APPLICATION_OVERLAY via WindowManager) —
  two windows, full-screen (touch-consuming, non-focusable) and indicator (click-through).
- **Usage tracking**: `UsageStatsIngester` reads `UsageEvents` since a DataStore checkpoint,
  derives sessions via pure `SessionDeriver` (pre-split at local midnight), upserts Room in one
  transaction. Runs every 15 min (WorkManager) and on overview open. Nightly `RollupWorker`
  finalizes and prunes. The overview reads sessions back through the pure `NightTimeline`.

## Threading model

- Service/receiver callbacks arrive on the main thread; hand work to coroutines immediately.
- `BlockingCoordinator` runs on its own `SupervisorJob` scope; overlay show/hide always hops to
  `Dispatchers.Main` (inside the overlay controllers).
- Room/DataStore access via `Dispatchers.IO` (repositories are `suspend`/Flow-based).

## State & persistence

- **Room** (`dift.db`, v2): blocks (`block_rules`), block↔exempt-app cross-refs (`rule_apps`),
  usage sessions, daily aggregates, block events. `unblock_grants` survives from v1 for schema
  stability but is unused. `MIGRATION_1_2` deletes retired rows without touching structure — see
  docs/DATA_MODEL.md and `Migrations.kt`.
- **DataStore**: scalar settings + ingest checkpoint + the persisted cooldown
  (`cooldownStartedAt`, `cooldownUntil` — must survive process death and reboot). The burst is
  in-memory only.
- Split rationale: docs/DATA_MODEL.md.
