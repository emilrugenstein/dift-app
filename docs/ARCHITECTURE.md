# Architecture

Single Gradle module (`:app`), strict package layering. Kotlin + Jetpack Compose (Material 3),
Hilt, Room + DataStore, WorkManager. minSdk = targetSdk-ish single-device app (Fairphone 6,
Android 15/16) — no backward-compat code.

## Layers

| Package   | Contents | Rules |
|-----------|----------|-------|
| `domain/` | `RuleEngine`, `DebtReducer`, `SessionDeriver`, models, `SafetyDenylist` | **Pure Kotlin.** No `android.*`/`androidx.*` imports, no wall-clock reads (time is a parameter). Enforced by `DomainPurityTest` (Konsist). |
| `data/`   | Room database (entities, DAOs), DataStore settings, repositories | Repositories are the only DB/DataStore consumers; expose Flows + suspend functions. |
| `system/` | Accessibility service, fallback monitor FGS, `ForegroundAppTracker`, `DeviceStateMonitor`, `BlockingCoordinator`, `OverlayComposeHost`/`OverlayController`, `UsageStatsIngester`, WorkManager workers, `BootReceiver`, `PermissionsChecker` | Android machinery. Services stay thin — business logic lives in `domain/`, orchestration in `BlockingCoordinator`. |
| `ui/`     | Compose screens (one folder per screen: `XScreen.kt` + `XViewModel.kt`), theme, navigation, overlay content (`BlockScreen`) | ViewModels talk to repositories/coordinator only. All text via `strings.xml`. |
| `di/`     | Hilt modules | — |

## Runtime flow (blocking pipeline)

```
DiftAccessibilityService ─┐                        ┌→ OverlayController → BlockScreen
  (TYPE_WINDOW_STATE_     ├→ ForegroundAppTracker ─┤    (Compose in a WindowManager window)
   CHANGED events)        │    StateFlow<String?>  └→ home-kick via performGlobalAction
FallbackMonitorService  ──┘                             (only while accessibility is bound)
  (1 s queryEvents poll,           │
   screen-on only)                 ▼
DeviceStateMonitor ────→  BlockingCoordinator ──→ RuleEngine.evaluate(input)   [pure]
  (unlock/lock/screen)       ▲     ▲    ▲                  │
                     RuleRepo┘ UsageRepo└GrantRepo         └→ BlockEventRepository (audit)
```

- **Detection** is an interface (`ForegroundAppDetector`) with two implementations:
  accessibility (primary, instant) and usage-events polling (fallback when accessibility is
  disabled). Both feed the same tracker.
- **`ForegroundAppTracker`** filters window events: ignores SystemUI, the current IME, and
  Dift's own overlay window (otherwise showing the overlay re-enters the pipeline — infinite
  loop); accepts only classes that resolve to real Activities (cached PackageManager lookups);
  exposes a debounced `StateFlow<String?>`.
- **`BlockingCoordinator`** (singleton) is the only orchestrator: assembles `EvaluationInput`
  from hot in-memory rule/grant caches (no DB on the hot path), calls the pure `RuleEngine`,
  acts on the verdict (overlay show/hide, home-kick, block-event logging). Runs a 1 s ticker
  while a limit rule or usage-debt window is active so blocks fire mid-session. Hides the
  overlay whenever the foreground app is on the `SafetyDenylist` (dialer stays reachable).
- **Blocking UI** is drawn by `OverlayComposeHost` (TYPE_APPLICATION_OVERLAY via
  WindowManager). Never via an Activity — ADR-0003.
- **Usage tracking**: `UsageStatsIngester` reads `UsageEvents` since a DataStore checkpoint,
  derives sessions via pure `SessionDeriver` (sessions pre-split at local midnight), upserts
  Room tables in one transaction. Runs every 15 min (WorkManager), on dashboard open, and
  feeds live "used today" to the coordinator. Nightly `RollupWorker` finalizes and prunes.

## Threading model

- Services/receivers callbacks arrive on the main thread; hand work to coroutines immediately.
- `BlockingCoordinator` runs on a single dedicated dispatcher; overlay show/hide always hops
  to `Dispatchers.Main`.
- Room/DataStore access via `Dispatchers.IO` (repositories are `suspend`/Flow-based).

## State & persistence

- **Room**: rules, rule↔app cross-refs, unblock grants, usage sessions, daily aggregates,
  block events. Schema JSONs committed under `app/schemas/`.
- **DataStore**: scalar settings + ingest checkpoint + persisted night-debt state
  (`activeBurstStartedAt`, `debtUntil` — must survive process death and reboot).
- Split rationale: docs/DATA_MODEL.md.
