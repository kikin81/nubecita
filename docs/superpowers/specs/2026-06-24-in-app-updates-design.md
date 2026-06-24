# In-app updates (Play app-update) — design

**Repo:** nubecita (Android). **Epic:** `nubecita-cf13`. **Branch:** `feat/nubecita-cf13-in-app-updates`.

## Purpose

Offer a Google Play in-app update when a newer build is available, with a practical, non-spammy
cadence: gentle by default, able to force a critical update, and naturally frequent on the internal
test track yet rare in production — without per-track build infrastructure.

## Library (researched)

`com.google.android.play:app-update:2.1.0` + `app-update-ktx:2.1.0` (the split-out Play libs, NOT the
deprecated monolithic `play-core`). Requires API 21+ (nubecita `minSdk = 28` ✓) and the app to be
installed from Play — off-Play installs **no-op** (`UPDATE_NOT_AVAILABLE`, never throws).

- Two flows: **FLEXIBLE** (consent → background download → app shows a "restart to install" prompt →
  `completeUpdate()` restarts) and **IMMEDIATE** (full-screen blocking; Play installs + restarts).
- Launched via `registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult())` +
  `appUpdateManager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())`.
  The launcher must be registered in the **Activity** before RESUMED (a ViewModel can't hold it).
- `AppUpdateInfo` signals: `updateAvailability()`, `isUpdateTypeAllowed(FLEXIBLE|IMMEDIATE)`,
  `updatePriority()` (0–5, set per-release via the Play Developer API, **immutable**, **absent in
  Internal App Sharing**), `clientVersionStalenessDays()` (nullable), `installStatus()`,
  `availableVersionCode()`.
- Lifecycle: re-query in `onResume`; resume an interrupted IMMEDIATE
  (`DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`); surface a FLEXIBLE `DOWNLOADED` install prompt;
  register/unregister the `InstallStateUpdatedListener` with lifecycle. `AppUpdateInfo` is single-use.
- The Play Store itself rate-limits how often the FLEXIBLE consent dialog appears.

## Decisions (locked)

1. **FLEXIBLE default + IMMEDIATE for high priority.**
2. **Version-scoped throttle, single policy** (no runtime internal-vs-production branch — the app can't
   distinguish them: internal testing is the same `production` artifact on Play's internal track).
3. **New `:core:update` module**, mirroring the existing `:core:review` almost 1:1.

## Architecture — `:core:update` (mirror `:core:review`)

SDK-agnostic boundary: no `com.google.android.play.core.appupdate.*` type leaks past the module. Files
(paralleling `:core:review`'s `ReviewManager`/`DefaultReviewManager`/`ReviewPolicy`/`ReviewPreferences`/
`ReviewClient`/`ReviewState` + flavor split):

- `InAppUpdateController` (interface) + `DefaultInAppUpdateController` (production impl).
- `AppUpdateClient` (interface) + `PlayAppUpdateClient` (the only file importing the Play SDK; wraps
  `AppUpdateManagerFactory.create(context)`, exposes `appUpdateInfo`/`startUpdate`/`completeUpdate`/
  listener registration in our own types).
- `UpdatePolicy` — **pure** `decide(signals, lastPromptedVersionCode): UpdateAction`.
- `UpdatePreferences` (interface) + `DefaultUpdatePreferences` (DataStore) + `di/UpdateDataStore` qualifier.
- `UpdateState` (`Idle` / `Downloading(downloaded, total)` / `ReadyToInstall` / `Failed`),
  `UpdateAction` (`None` / `Flexible` / `Immediate`), `UpdateSignals` (our copy of the AppUpdateInfo
  fields the policy needs).
- `di/UpdateModule` — **production** binds `DefaultInAppUpdateController` + provides `PlayAppUpdateClient`/
  `AppUpdateManager`. **bench** (`src/bench`) binds a `BenchNoOpInAppUpdateController` (zero Play/network
  calls — same posture as `BenchFakeReviewManager`).
- Catalog: add `playAppUpdate = "2.1.0"` to `[versions]` and `google-play-app-update` /
  `google-play-app-update-ktx` to `[libraries]` (alphabetically beside `google-play-review`).

### The policy (pure, the heart of the cadence)

```
decide(signals, lastPromptedVersionCode):
  if availability != UPDATE_AVAILABLE: return None
  if isImmediateAllowed && (updatePriority >= 4 || (stalenessDays ?: 0) >= 60): return Immediate
  if isFlexibleAllowed && signals.availableVersionCode != lastPromptedVersionCode: return Flexible
  return None
```

- **IMMEDIATE is never throttled** (a critical update should always prompt; it also auto-resumes on
  `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`). Thresholds (`>= 4`, `>= 60d`) are Google's high-priority
  gate and are tunable constants.
- **Graceful degradation (intentional, not a bug):** the `Immediate` branch is gated on
  `isImmediateAllowed`. If a high-priority update arrives but Play reports IMMEDIATE *not* allowed
  (device/Play state), the policy falls through to `Flexible` — a high-priority update then shows the
  gentle flow rather than nothing. This is deliberate; a policy test pins it so it isn't mistaken for a
  bug.
- **FLEXIBLE is throttled once per `availableVersionCode`.** `lastPromptedVersionCode` persists in the
  DataStore and is written **at `startUpdateFlowForResult` fire time** (when the intent is generated),
  NOT on the returned `ActivityResult` — if the user backgrounds the app while Play's dialog is up the
  result can be dropped/delayed, and writing at fire time guarantees we don't double-nag the same
  version. A new, higher `availableVersionCode` re-arms the prompt. Net cadence with **no per-track
  code**: internal (new versionCode every commit) → ~one prompt per build; production (one versionCode
  per release) → ~one prompt per release.

### Controller + state

`InAppUpdateController` is a `@Singleton` and exposes `val state: StateFlow<UpdateState>` and:
- `suspend fun checkAndMaybePrompt(launcher)` — query signals, run `UpdatePolicy.decide`, and on
  `Flexible`/`Immediate` call `startUpdate` (writing `lastPromptedVersionCode` at fire time for FLEXIBLE).
- `fun onResume(launcher)` — the **catch-up** path only: resume an interrupted IMMEDIATE, and surface a
  FLEXIBLE update that finished downloading while the app was **backgrounded**.
- `suspend fun completeFlexibleUpdate()` — `completeUpdate()` (restart to install).
- Owns the `InstallStateUpdatedListener` and maps install status → `UpdateState` **in real time**: when
  a foreground download reaches `DOWNLOADED`, the listener pushes `UpdateState.ReadyToInstall` to the
  StateFlow immediately (the snackbar appears the moment the download finishes — not only on the next
  `onResume`). The listener is registered when a FLEXIBLE flow starts and unregistered on a **terminal
  install state** (`INSTALLED`/`FAILED`/`CANCELED`) — **not** on Activity destroy.
- **Lifecycle ownership:** because it's a singleton, its `state` and any in-flight download survive
  Activity recreation (rotation/theme change). It does **not** persist the per-Activity
  `ActivityResultLauncher` — the launcher is passed in per call, so a recreated Activity simply hands
  in its fresh launcher. There is no `controller.onDestroy()`; the listener lifecycle is tied to the
  download (terminal state), the StateFlow lives in the singleton, and the `AppUpdateManager` is itself
  app-scoped so the listener can't leak the Activity. Not a ViewModel (the launcher is Activity-scoped
  — same boundary as `:core:review`, called from the Activity, never a VM).

### Activity wiring — `MainActivity` (mirror `ActivityPipBridge` / `LocalPipController`)

- `@Inject` the `InAppUpdateController` (Hilt singleton).
- In `onCreate` (before `setContent`): `val updateLauncher = registerForActivityResult(StartIntentSenderForResult()) { … }`
  (re-registered on each Activity instance — correct), then
  `lifecycleScope.launch { controller.checkAndMaybePrompt(updateLauncher) }`.
- Add `override fun onResume()` → `controller.onResume(updateLauncher)`.
- **No `controller.onDestroy()`** — the singleton must survive config changes (a teardown there would
  reset `state` from `Downloading`→`Idle` on every rotation). The controller manages its own listener
  on terminal install states.
- **No `BuildConfig.FLAVOR` branch** — the bench no-op binding makes the whole thing inert in
  benchmarks; MainActivity always calls the controller.

### The "restart to install" surface

No app-wide SnackbarHost exists, and the prompt must outlive screen changes → an Activity-level
`InAppUpdateHost` composable placed in `MainActivity`'s outer `Surface` (above `MainNavigation`,
alongside the `LocalPipController` provider). It collects `controller.state` and shows a Snackbar when
`ReadyToInstall`, action **Restart** → `completeFlexibleUpdate()`. IMMEDIATE renders its own
full-screen Play UI — no app surface needed. Strings localized (en/es/pt): "Update downloaded" /
"Restart" (+ es/pt).

## Error handling

The controller swallows Play failures into `UpdateState.Failed` and otherwise stays `Idle` — an update
prompt failing must never disrupt the app. Logging uses **`Timber.w`** (expected/recoverable → visible
in logcat but kept off Crashlytics non-fatals), `javaClass.name` only (no PII). Any `runCatching` /
`Throwable` catch in the suspend functions **rethrows `CancellationException`** first (these run in the
Activity `lifecycleScope`; swallowing it breaks cooperative cancellation). Off-Play / no-update →
silent `None`. `RESULT_CANCELED` (user declined) → already recorded as prompted at fire time, return to
`Idle`. IMMEDIATE `RESULT_OK` is unreliable (Play may restart directly) — only handle CANCELED /
`RESULT_IN_APP_UPDATE_FAILED`.

## Testing

- **`UpdatePolicy`** → JVM unit tests: the full matrix (availability × FLEXIBLE/IMMEDIATE allowed ×
  priority × staleness × lastPromptedVersionCode → expected `UpdateAction`), incl. the throttle
  (same versionCode → `None`; new versionCode → `Flexible`), the IMMEDIATE gate, and the **graceful
  fallback** (high priority but `isImmediateAllowed == false` → `Flexible`, explicitly asserted so it
  reads as intended behavior).
- **`DefaultUpdatePreferences`** → JVM test of the DataStore round-trip (mirrors
  `DefaultReviewPreferencesTest`).
- **`DefaultInAppUpdateController`** → instrumented test with `FakeAppUpdateManager`
  (`setUpdateAvailable`, `setUpdatePriority`, `userAcceptsUpdate`, `downloadStarts/Completes`,
  `completeUpdate`, `installCompletes`) asserting: the real-time `state` transitions (foreground
  `downloadCompletes()` → `ReadyToInstall` without an `onResume`); the throttle is written **at
  startUpdate fire time** (assert `lastPromptedVersionCode` is set before the result, and a subsequent
  check for the same versionCode → `None`); and config-change survival (the singleton keeps
  `Downloading`/`ReadyToInstall` across a simulated Activity teardown — no `onDestroy` reset). (Behind
  the `run-instrumented` PR label.)
- **Bench no-op** keeps Macrobench offline.
- **Manual E2E** → Internal App Sharing only (sideloaded builds no-op); `updatePriority` isn't exposed
  via App Sharing, so the IMMEDIATE path is covered by the Fake, not on-device.

## Out of scope

A "what's new" / changelog screen; per-track build flags (the version-scoped throttle covers cadence);
any non-Play distribution (the library no-ops there); changing the release process (priority is set
per-release via the Play Developer API when a critical update warrants IMMEDIATE).

## Conventions

bd workflow (`nubecita-cf13` + child tasks), Conventional Commits (lowercase-leading), `Refs:
nubecita-cf13`; `:core:update` uses the standard convention plugins + `checkSortDependencies`;
pull-first/push-last bd/Dolt discipline (`docs/beads-multi-machine.md`).
