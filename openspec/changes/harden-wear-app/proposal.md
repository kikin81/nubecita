# Proposal

## Why

The watch app shipped fast across six epics and is on current pre-release Wear libraries, but a six-way audit on 2026-11-14 (UI code, widget and complication, versions and build, data layer and battery, testing, and a hands-on pass on a Wear OS 7 emulator) found three kinds of problem a watch user or a future change will hit:

- **Things a user sees.** The clock is drawn over text on screens that open scrolled. A reply you just sent lands clipped at the bottom with the Reply button off-screen. "Open on phone" is cut off at large font. Post counts are text glyphs that turn into a red emoji heart. Feed cards collapse to one line at large font. The app stays lit in always-on with no ambient rendering.
- **Battery and reliability nobody sees.** Every time the watch app resumes, the phone re-encodes and republishes every feed image and the watch rewrites all of them. A like sent while the phone has no network silently reverts and is never retried.
- **No regression net.** The watch has no journeys, nothing tests its navigation wiring, no pull request runs R8 on it, all 151 screenshot baselines are English and render without the clock, and the bench build reaches only happy paths.

Nothing here is a crash and no library needs bumping. It is worth doing now because the watch is about to reach production, usage is still small, and every later Wear change needs the test surface this adds.

## What Changes

Three epics, each one `gh stack`, landed in this order.

**A — Regression safety net and visible UI fixes (`nubecita-z9s4`)**
- Bench scenario switch so gate, empty and failure states are reachable on a device; a posted text that stays in the bench feed.
- A Wear journey suite (nine journeys) and a written definition of done for Wear pull requests.
- CI: a release (R8) build of the watch on every pull request; a non-automerged Renovate group that keeps tracking the newest Wear pre-releases; the watch database test in the nightly run.
- Screenshot baselines in es-419 and pt-BR, with the clock, and for the states that have none today.
- UI fixes: thread follows the newest message; edge buttons that can start a scroll; end-of-list content clear of the bezel; the review screen opens on its text; item transformations instead of a full-list fade; a clock that never covers content; a complete brand colour scheme; icon post counts; button groups; readable feed cards at large font; ambient rendering; an honest "Open on phone" result; a visible gate loading state.

**B — Sync battery and action reliability (`nubecita-8x3y`)**
- The phone skips republishing an unchanged feed snapshot; the watch rate-limits its sync requests.
- The phone waits for its session before answering the watch, and tags watch actions as their own token-refresh caller.
- Watch actions survive a transient failure and are retried from wake points that already exist. No new periodic or constraint-based job.
- Watch images are stored by content, so an unchanged image is never rewritten.
- One catch-up per process, off the main thread, serialized with the listener; the widget and complication refresh only when what they show changed.
- A payload size guard for DM snapshots and pinned wire-format fixtures for the contract.

**C — Widget, complication and startup profile (`nubecita-8ynp`)**
- Widget: themed background in the widget form (black stays for the tile form), a SMALL container, no Play services call in the render path, 48dp targets, the full preview set with picker images generated from code.
- Complication: `LONG_TEXT`, and a tappable state when there is no data.
- A baseline and startup profile for the watch app.

No breaking changes. The Data Layer contract stays at major version 1.

## Non-goals

- **No dependency bumps.** Every Wear library is already the newest published version (read from Google Maven on 2026-11-14).
- **The widget stays a launcher.** No sender names, message text, avatars or feed content. The `add-wear-launcher-widget` decision of 2026-11-12 stands.
- **No new background work.** No WorkManager job, alarm, foreground service or Doze workaround for action retries. Battery beats reliability.
- **No dynamic colour.** The watch keeps the Nubecita brand scheme and does not follow the watch face.
- **No "Updated N min ago" label.** It was removed with the compact cards and is not reintroduced here.
- **No two-way schema negotiation.** A minimum-supported version and an "update your phone" state wait for the first contract major bump.
- **No Horologist**, and no move off pre-release Wear libraries.
- **No Wear emulator in pull-request CI.** Journeys stay agent-evaluated; the emulator pass is a local gate.
- The Wear OS 4–6 tile-renderer check needs a physical older watch and is tracked separately (`nubecita-zc6v`).

## Capabilities

### New Capabilities
- `wear-quality-gates`: how watch changes are verified — bench scenarios, journeys, screenshot coverage, the pull-request release build, and pre-release dependency tracking.

### Modified Capabilities

None under `openspec/specs/` — the Wear capabilities still live in the unarchived `add-wear-os-app` and `add-wear-launcher-widget` changes. This change adds requirements to the same capability names so they merge at archive:

- `wear-app-shell`: edge and clock layout rules, list item transformations, colour roles, ambient rendering, the gate loading state, the startup profile.
- `wear-messages`: the thread stays on the newest message.
- `wear-post-actions`: icon counts, an honest "Open on phone" result, retry after a transient failure.
- `wear-data-sync`: unchanged snapshots are not republished, rate-limited sync requests, session resolution on a cold process, content-addressed images, serialized ingest, change-only surface refreshes, the payload bound, wire fixtures.
- `wear-launcher-widget`: container backgrounds, the SMALL container, rendering without Play services, touch targets, generated previews.
- `wear-complication`: `LONG_TEXT` and the tappable no-data state.

## Impact

- **Watch:** `:wear:app` (every screen, theme, widget, complication, bench source set, new `journeysTest` directory, screenshot tests), `:wear:data` (ingester, image store, Room migrations for the pending tables).
- **Phone:** `:core:wear-sync` (feed publish, action dispatcher, sync-request handler, listener service), `:core:auth` (`RefreshCaller.WearAction`), `:core:wear-contract` tests (golden fixtures). No phone UI changes.
- **Build and CI:** `.github/workflows/ci.yaml` and `instrumented-nightly.yaml`, `.github/renovate.json`, the Wear convention plugin comment, `gradle/libs.versions.toml` (Hilt view-model artifact, Robolectric), a new `:wear:benchmark` module in epic C.
- **Docs:** `docs/wear-testing.md`, a pointer in `CLAUDE.md`.
- **Baselines:** epic A rebaselines most Wear screenshots (colour scheme, transformations, clock). Baselines come from the `update-baselines` bot, never from macOS.
- **Baseline deviations:** none from MVI, Hilt, Room or Coil. The watch keeps using pre-release Wear Compose by design (D8 of `add-wear-os-app`).
