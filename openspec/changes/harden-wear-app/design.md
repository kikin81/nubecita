# Design

## Context

See `proposal.md` for motivation. The constraints that shape the approach:

- The watch is phone-relayed and offline-first (`add-wear-os-app`). The phone publishes feed and DM snapshots over the Wearable Data Layer; the watch caches them in Room and queues actions back as DataItems.
- **Battery is the top priority and beats reliability.** No Doze workarounds, no polling to paper over delivery gaps.
- The Wear libraries are pre-release on purpose: Wear Compose M3 `1.8.0-alpha03` (with `compose-navigation3`), Glance Wear `1.0.0-alpha22`, Remote Compose `1.0.0-alpha23`. All are the newest published.
- The bench flavor of `:wear:app` replaces the repositories with in-memory fakes, so it never touches Room or the Data Layer. It is the only build that runs without a phone.
- Screenshot baselines are authoritative only from CI's Linux host. A baseline update pushed to a lower branch restacks everything above it.
- Local hardware: one Wear AVD (`Wear_OS_Large_Round`, Wear OS 7, 227dp). A small-round AVD and a Wear OS 6 image are created in epic A.

Audit evidence (file and line per finding) lives in the bd issues; this document records only the decisions.

## Goals / Non-Goals

**Goals:**
- Every Wear change after this one can be verified the same way: flavored Gradle tasks, CI-generated baselines, a bench emulator pass, and journeys.
- Fix what the emulator pass showed, using the Wear Compose M3 component the library already ships rather than a hand-rolled one.
- Remove Data Layer and disk work that produces no visible change.
- Make a watch action survive the phone being offline, at no standing battery cost.

**Non-Goals:**
- Running Room or the real Data Layer inside the bench flavor. Bench stays fully fake; the production data path is covered by unit tests and one paired-device pass per sync change.
- Automating journeys in CI.

## Decisions

### D1. Three epics, three stacks, A → B → C
One stack of 21 branches would restack a dozen branches for every baseline update. A is first because B and C are verified through what it adds. B is independent of A's files (`:core:wear-sync`, `:wear:data`) but lands second so its UI-visible parts (pending and failed states) have journeys. C depends on A's bench scenarios for the signed-out widget states.
*Alternative:* one epic and one release. Rejected for the restack cost and review size.

### D2. Tests before fixes, and the first baselines pin today's defects
Child A4 adds the localized and clock baselines *before* the UI fixes, so each fix shows as a reviewed image diff in its own pull request. A baseline can pin a bug; here that is intended and each pinned defect is named in A4's description.
*Alternative:* fix first, baseline after. Rejected: the fix would have no before-image and a regression in the fix itself would be baselined silently.

### D3. Bench scenarios through a launch extra
A bench-only scenario holder, set from an intent extra on `MainActivity` and read by the `Bench*` fakes. It lives in `src/bench` only; `src/production` has no reference to it. The widget and complication read the same holder so their signed-out and zero states are reachable.
*Alternative:* a hidden debug menu on the watch. Rejected: a journey would have to drive it, and it would exist in the UI.

### D4. Journeys are XML evaluated by an agent, as on the phone
`wear/app/src/journeysTest/*.journey.xml`, same format and rules as `app/src/journeysTest`. Text entry taps on-screen keys, because text injected with `adb shell input text` is shown by the Wear keyboard but never returned to the app. Every command targets the emulator serial explicitly; Gradle install tasks are never used because they install to every attached device.
*Alternative:* UiAutomator tests. Rejected for now: no CI job can run them (D6), and the phone already has the agent-evaluated convention.

### D5. The clock: adopt the system status bar, keep a fixed time source for tests
The app passes its own `timeText` to `AppScaffold`, which opts out of the System Status Bar that Wear Compose 1.8 uses by default. Child A7 calls `AppScaffold` without `timeText` and pins the clock in bench and screenshots through `LocalTimeSource`, deleting the two flavor-split `WearTimeText` shims. Whether this alone removes the overlap is verified on Wear OS 7 and a Wear OS 6 image before the fade is touched; if the clock still covers resting content, the list's top inset is raised to clear it.
*Alternative:* keep the custom time text and enlarge the fade band. Rejected: it keeps a hand-rolled layer the library now owns.

### D6. No Wear emulator in pull-request CI
A release build with R8 on each pull request catches the class of breakage that has actually happened (a resource stripped, an XR dependency leaking). A Wear emulator job is not added: it is unverified that the emulator runner supports Wear images, and the cost is paid on every pull request for a surface with a handful of users. `:wear:data`'s Room test joins the existing nightly matrix, which already has a phone emulator.

### D7. Renovate keeps tracking pre-releases, in its own group, without automerge
A `wear-prerelease` package rule with `ignoreUnstable: false` and `respectLatest: false` for the Wear, Glance Wear and Remote Compose groups. Without it the shared preset follows `1.8.0` to stable and stops proposing alphas. Automerge is off because a Wear Compose bump drags the Compose floor and `compileSdk` with it.

### D8. Colour: the full scheme from the brand seed
All 29 roles generated from the Sky seed. Dynamic colour from the watch face is not used: the phone app is brand-coloured and the widget shares this scheme. A8 is a colour-only pull request so its rebaseline of nearly every image is reviewable on its own.

### D9. Ambient: render less, never lower the target SDK
With target SDK 37 on Wear OS 6+ the app is always-on. The root provides the ambient mode manager; in ambient the feed and post hide images, filled surfaces become outlines, and the relative-time ticker follows the ambient tick. Lowering `targetSdk` to avoid always-on is not an option (the widget needs 37).

### D10. Unchanged snapshots: compare on the phone, ignoring the publish time
The feed publisher reads back the last published snapshot and compares posts with `publishedAtMs` excluded. Equal means no image encode and no put. This is the pattern the DM poll already uses. The watch separately rate-limits `requestSync` with a monotonic cooldown, so returning from the text-input activity does not ask again.
This narrows "Pull on open refreshes feeds without new background work" in `add-wear-os-app`: the phone still answers every request, but republishes only what changed.
*Alternative:* debounce only on the watch. Rejected: the first open after a 15-minute background publish would still re-encode everything.

### D11. Action retry rides wake points that already exist
For a connectivity-class failure the phone leaves the action item unanswered and deletes nothing. Leftover action items are drained when the phone handles a sync request and at the end of the existing widget-refresh run — both already have network. A local "started" marker per action id prevents a DM reply or post being sent twice. On the watch, a pending row whose action item and result item are both gone is marked failed (DMs, posts) or dropped (toggles).
No WorkManager job with a `CONNECTED` constraint is added. It would be constraint-based deferral rather than a Doze workaround, but it is a new job on the phone for a path the field has not yet seen fail (0 of 11 actions in 60 days). Revisit if failures show up.
Rejected actions still revert silently (decision 9 of `add-wear-os-app`); only transient failures change.

### D12. Content-addressed images on the watch
Image files are named by content digest in one directory per account. An existing file is neither loaded from the Data Layer nor rewritten; unreferenced files are pruned after the database switch. If `Asset` does not expose a usable digest, the phone adds a stable key (the source URL's hash) to the post mapping — an additive contract field, still major version 1.

### D13. Widget session state comes from the watch's own store
The widget reads signed-in state from a value the ingester writes when a session snapshot arrives, not from a Play services call during render. `withTimeoutOrNull` remains as a floor. The document background branches on the container type: black for the tile form (required for tiles), a themed surface for widget containers.

### D14. The SMALL container is one target
A single tappable Messages row with the unread label. The two stacked pills do not fit a 54–60dp content height. The LARGE layout and the tile form keep three targets.

### D15. Destructive migration stays, except for watch-authored rows
The watch database keeps its destructive fallback for synced copies. The three pending tables (posts, DM replies, actions) hold content written on the watch, so they get AutoMigrations and are excluded from the drop.

## Risks / Trade-offs

- **Reverse layout on the thread may interact with the Reply edge button** → the library's scroll-info provider supports it in source; verified on the emulator in A5 before the baselines are regenerated. Fallback: keep the top-anchored list and scroll to the last item when the pending list grows.
- **Dropping the custom time text could show no clock on Wear OS 4–6** → verified on a Wear OS 6 image in A7; the explicit `timeText` stays if the fallback is missing.
- **Content transformation on long text has cropped text before** (per the comments on `roundEdgeFade`) → the fade is removed only from all-surface lists unconditionally; for plain text it stays wherever the transformation reads worse on a device.
- **Baseline churn across the stack** → A4 and A8 are isolated; fixes are batched per branch; `gh stack sync` runs before every push after the bot commits.
- **The bench flavor hides production data bugs** → B's children carry unit tests against the real ingester and DAOs and one paired-device pass; this is called out in the definition of done.
- **A skipped republish could hide a real change if the comparison is wrong** → the comparison covers every field the watch renders; a unit test mutates each field in turn.
- **The "started" marker could strand an action if the phone dies mid-send** → the marker carries a timestamp; a marker older than the action's expiry is treated as failed.
- **Requirements added here sit beside unarchived ones with the same capability names** → archive `add-wear-launcher-widget` and `add-wear-os-app` first; when archiving this change, read the other changes' requirement blocks for the capabilities listed in the proposal and reconcile "Pull on open…" with D10.

## Migration Plan

Each epic is one atomic stack merge and one release. Rollback is a revert of the squash commits of that stack. Epic B's watch database change is an AutoMigration for the pending tables; the synced tables keep the destructive fallback, so a downgrade loses only cached copies.

## Open Questions

- Does the update-baselines bot run on a pull request whose base is a stack branch rather than `main`? Answered by the first stack child that changes a baseline (A4); if not, baselines are regenerated from the CI artifact per the existing recipe.
- Does `android layout` expose nodes inside the host-rendered widget? Affects only how the widget journey taps; the annotated-screenshot path is the fallback.
