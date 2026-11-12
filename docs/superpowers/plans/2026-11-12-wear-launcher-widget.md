# Wear Launcher Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Nubecita widget (a full-screen tile on Pixel / Wear OS 4–6) one swipe from the watch face: a Messages card with the unread count, plus Home and New post pills, each opening the app at that destination.

**Architecture:** Child 1 adds launch targets to the watch app (deep links for feed and New post, beside the existing Messages one). Child 2 adds one `GlanceWearWidgetService` serving both the widget and tile protocols, rendering a Remote Compose layout from a pure `LauncherWidgetModel`. Child 3 feeds it the synced unread count and pushes updates through the existing `DmSyncObserver` hook. Child 4 makes it release-ready.

**Tech Stack:** Kotlin, Glance Wear (`androidx.glance.wear`), Remote Compose (`androidx.compose.remote`, `androidx.wear.compose.remote:remote-material3`), Hilt, Navigation 3, JUnit 5; Robolectric (JUnit 4 via the vintage engine) for widget and intent tests in `:wear:app` only.

**Spec:** `docs/superpowers/specs/2026-11-12-wear-launcher-widget-design.md`

## Global Constraints

- Libraries: glance-wear `1.0.0-alpha22`, compose-remote `1.0.0-alpha23`, wear compose remote-material3 `1.0.0-alpha15` (or newer at implementation time); versions in `gradle/libs.versions.toml` only.
- `compileSdk = 37`, `minSdk = 33` (already set by `nubecita.android.wear.application`); `WearForbiddenDependencies` must still pass.
- One service, both protocols: `androidx.glance.wear.action.BIND_WIDGET_PROVIDER` **and** `androidx.wear.tiles.action.BIND_TILE_PROVIDER`, permission `com.google.android.wearable.permission.BIND_TILE_PROVIDER`.
- Large container only; picker preview PNG `464x288` px in `res/drawable-nodpi/`.
- No periodic refresh: no `UPDATE_PERIOD`, no WorkManager, no timers. Updates only via `DmSyncObserver`.
- Deep links honoured only on a fresh launch (`savedInstanceState == null`); landing screen always underneath; no trampoline activity (`WO-V15`); `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`, `FLAG_IMMUTABLE`.
- Robolectric `4.17` (latest stable) + `org.junit.vintage:junit-vintage-engine` `6.1.3` (matches `junitPlatform`), `testImplementation` in `:wear:app` only — no change to `build-logic`'s JUnit Platform setup. Robolectric test classes use `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [35])` (the highest the version supports at or above `minSdk 33`), and `@SuppressLint("RestrictedApi")` for the alpha `WearWidgetParams` / `captureRawContent`. Fallback if the vintage engine can't run alongside Jupiter 6: drop Robolectric and rely on the emulator checks — don't modify shared test config to force it.
- Counts above 99 show as `99+` (reuse `unreadDmsLabel`).
- Every new string ships in `values`, `values-b+es+419`, `values-pt-rBR` in the same commit.
- Gate per child: `:wear:app:testProductionDebugUnitTest :wear:app:lintProductionDebug :wear:app:spotlessCheck :wear:app:compileBenchDebugKotlin`. Top of stack: plus `:wear:app:assembleBenchDebug`, a bench pass on a Wear emulator, and the compose-expert review of `git diff origin/main...HEAD`.

## Review Focus

1. **Widget tapped while the app is open on another screen** (e.g. inside a thread): the tap must land on the tile's destination, not resume the old screen — pinned in Task 2 (`LaunchIntentTest`, Robolectric).
2. **New post tapped while posting is unavailable** (phone app missing the capability, or it resolves late): stay on the landing screen and never pop the input open later out of nowhere — pinned in Task 1 (`resolvePendingNewPost` tests).
3. **Rotation / process-death restore after a deep link** must not replay it (no second input, no extra Messages push) — pinned in Task 1 (`launchTargetOf` with saved state).
4. **Sign-out or account switch** while the widget is placed: it must stop showing the old account's count — pinned in Task 4 (observer fires on DM clear) and Task 3 (`null` → no-count model).
5. **Plural/limit edges** — 0, 1, 99, 100 unread — pinned in Task 3 model tests.

---

## Child 1 — `feat(wear): open the watch app on the feed or New post`

### Task 1: Launch targets and deep links

**Files:**
- Create: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/launch/WearLaunch.kt`
- Modify: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/MainActivity.kt` (actions, parse target)
- Modify: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/NubecitaWearApp.kt` (`openMessages: Boolean` → `launchTarget: WearLaunchTarget`; landing entry consumes New post)
- Modify: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/complication/UnreadDmsComplicationService.kt` (use `launchIntent`, delete `openMessagesIntent`)
- Test: `wear/app/src/test/kotlin/net/kikin/nubecita/wear/launch/WearLaunchTest.kt`

**Interfaces — Produces:**
- `enum class WearLaunchTarget { Landing, Feed, Messages, NewPost }`
- `fun launchTargetOf(action: String?, restoring: Boolean): WearLaunchTarget` — `restoring = true` → `Landing`.
- `fun initialBackStack(target: WearLaunchTarget): List<NavKey>` — Landing/NewPost → `[WearLanding]`; Feed → `[WearLanding, WearHome]`; Messages → `[WearLanding, WearConvos]`.
- `enum class PendingNewPost { Open, Drop, Wait }` and `fun resolvePendingNewPost(feedStatus: WatchFeedStatus, postingAvailable: Boolean): PendingNewPost` — Loading → Wait; otherwise Open if available else Drop.
- `fun launchIntent(context: Context, target: WearLaunchTarget): PendingIntent` — explicit `MainActivity` intent with the target's action, `NEW_TASK | CLEAR_TASK`, `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, request code = `target.ordinal` (distinct per target so the PendingIntents don't collapse).
- Actions on `MainActivity`: keep `ACTION_OPEN_MESSAGES` value; add `ACTION_OPEN_FEED = "net.kikin.nubecita.wear.action.OPEN_FEED"`, `ACTION_NEW_POST = "net.kikin.nubecita.wear.action.NEW_POST"`.

- [ ] **Step 1: Write the failing tests** (`WearLaunchTest`, JUnit 5)
  - `launchTargetOf_mapsEachAction`: OPEN_FEED→Feed, OPEN_MESSAGES→Messages, NEW_POST→NewPost, null→Landing, `"bogus"`→Landing.
  - `launchTargetOf_ignoresActionWhenRestoring`: `launchTargetOf(ACTION_NEW_POST, restoring = true) == Landing` (Review Focus 3).
  - `initialBackStack_keepsLandingUnderneath`: the four expected lists above.
  - `resolvePendingNewPost`: (Loading, false)→Wait, (Loading, true)→Wait, (Ready, true)→Open, (Ready, false)→Drop, (NoFeeds, true)→Open (Review Focus 2).
  - `launchIntent` needs an Android `Context`; it is pinned by Robolectric in Task 2 (`LaunchIntentTest`, Review Focus 1).
- [ ] **Step 2: Run** `./gradlew :wear:app:testProductionDebugUnitTest --tests '*WearLaunchTest*'` — expect compile failure (unresolved names).
- [ ] **Step 3: Implement** `WearLaunch.kt` per the Interfaces block.
- [ ] **Step 4: Wire it.** `MainActivity`: `NubecitaWearApp(launchTarget = launchTargetOf(intent?.action, restoring = savedInstanceState != null))`. `NubecitaWearApp`: `rememberNavBackStack(*initialBackStack(launchTarget).toTypedArray())`; landing entry holds `var pendingNewPost by rememberSaveable { mutableStateOf(launchTarget == NewPost) }` and a `LaunchedEffect(pendingNewPost, feedState.status, feedState.postingAvailable)` that applies `resolvePendingNewPost` (Open → `writePost()` then clear; Drop → clear; Wait → nothing). Complication tap → `launchIntent(this, WearLaunchTarget.Messages)`. Update KDoc that mentions `openMessages`.
- [ ] **Step 5: Run** the test class (PASS, confirm 4 cases ran in the XML report) and the child gate.
- [ ] **Step 6: Bench smoke** on the Wear emulator: `adb shell am start -a <each action> -n net.kikin.nubecita/.wear.MainActivity` lands on feed / conversations / text input; back → landing. Rotate or `am kill` + relaunch after NEW_POST: no second input.
- [ ] **Step 7: Commit** `feat(wear): open the watch app on the feed or New post` (footer `Refs: <child-1 id>`).

---

## Child 2 — `feat(wear): add the Nubecita launcher widget`

### Task 2: Widget service, layout and picker preview

**Files:**
- Modify: `gradle/libs.versions.toml` (versions `glanceWear`, `composeRemote`, `wearComposeRemote`; libraries per the Global Constraints)
- Modify: `wear/app/build.gradle.kts` (implementation deps; tooling-preview as `debugImplementation` if the module convention allows, else `implementation`)
- Create: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/widget/LauncherWidgetModel.kt`
- Create: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/widget/LauncherWidget.kt` (`GlanceWearWidget` + `@RemoteComposable` content)
- Create: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/widget/LauncherWidgetService.kt`
- Create: `wear/app/src/main/res/xml/launcher_widget_info.xml`, `wear/app/src/main/res/drawable-nodpi/launcher_widget_preview_large.png`
- Modify: `wear/app/src/main/AndroidManifest.xml`, strings (3 locales)
- Test: `wear/app/src/test/kotlin/net/kikin/nubecita/wear/widget/LauncherWidgetRenderTest.kt` (Robolectric)
- Test: `wear/app/src/test/kotlin/net/kikin/nubecita/wear/launch/LaunchIntentTest.kt` (Robolectric)

**Interfaces — Produces:**
- `data class LauncherWidgetModel(val countLabel: String?, val hasUnread: Boolean)` — `countLabel == null` means no data (show "Messages", no number).
- `class LauncherWidget(private val unreadCount: suspend () -> Int?) : GlanceWearWidget()` — `provideWidgetData` builds the model from `unreadCount()` (Child 2 passes `{ null }`), returns `WearWidgetDocument` with a black background.
- `@AssociateWithGlanceWearWidget(LauncherWidget::class) class LauncherWidgetService : GlanceWearWidgetService()`.
- Strings: `wear_widget_label` ("Nubecita"), `wear_widget_description` ("Messages, Home and New post"), `wear_widget_new_messages` plural ("new message" / "new messages"), `wear_widget_no_new_messages` ("No new messages"); reuse `wear_dms_open`, `wear_landing_home`, `wear_post_new`.

- [ ] **Step 1: Dependencies.** Add the catalog entries and module deps; run `./gradlew :wear:app:compileProductionDebugKotlin` and the forbidden-deps check (part of the module build) — PASS before writing UI.
- [ ] **Step 1b: Robolectric harness.** Add `robolectric` and `junit-vintage-engine` (Global Constraints) as `testImplementation` (vintage as `testRuntimeOnly`) in `wear/app/build.gradle.kts`, plus `junit:junit` for the API; `testOptions.unitTests.isIncludeAndroidResources = true` for `:wear:app` if not already set. Write `LaunchIntentTest` first: for each of `Feed`, `Messages`, `NewPost`, `launchIntent(context, target)` resolves (via `shadowOf(pendingIntent).savedIntent`) to `MainActivity` with the matching action and `NEW_TASK | CLEAR_TASK` flags, and the three `PendingIntent`s are distinct (`!=`). Run `./gradlew :wear:app:testProductionDebugUnitTest --tests '*LaunchIntentTest*'` → PASS, and confirm in the XML report that **both** the new Robolectric tests and the existing JUnit 5 tests ran (count before/after). If the vintage engine won't run beside Jupiter 6, apply the Global Constraints fallback and say so in the PR.
- [ ] **Step 2: Layout** (`LauncherWidgetContent(model, isLarge)`, Remote Compose): header (logo + `wear_widget_label`); a row of the Messages card (weight ~1.15, `primaryContainer`-family colour, messages icon, `countLabel` large + plural label, or "No new messages" / "Messages") and a column of two pills (Home, New post, `primary`-family). Each surface is `RemoteModifier.clickable(pendingIntentAction { ctx -> launchIntent(ctx, target) })` with Messages / Feed / NewPost. Use `.rdp` / `.rs` / `.rc`; no `mutableStateOf`.
- [ ] **Step 3: Service + manifest + provider XML.** Service declares both intent filters and the provider meta-data; `<wearwidget-provider label=… description=… preferredType="LARGE">` with one `LARGE` container → `@drawable/launcher_widget_preview_large`.
- [ ] **Step 4: Previews.** `@Preview` functions with `RoundAllWidgetPreviewParams`, `SquircleAllWidgetPreviewParams`, `RectangularAllWidgetPreviewParams` calling `WearWidgetPreview(LauncherWidget { 3 }, params)`. Render with `android preview render` (or Studio) and save the rectangular LARGE render as the `464x288` picker PNG — it must show the real tiles and a count, never a blank shape.
- [ ] **Step 4b: Render test.** `LauncherWidgetRenderTest` (Robolectric): for `LauncherWidget { null }` and `LauncherWidget { 3 }`, `provideWidgetData(context, params)` with a LARGE `WearWidgetParams`, then `captureRawContent(context, params)` returns non-null content without throwing — the guard against Remote Compose / Glance Wear alpha bumps breaking the document. Run → PASS.
- [ ] **Step 5: Gate + emulator check**: install bench on the Wear OS 7 emulator (widget form) and the existing Wear AVD (tile form, if below 7 / non-partial-height); add it from the picker; each tile opens its destination.
- [ ] **Step 6: Commit** `feat(wear): add the Nubecita launcher widget` (`Refs: <child-2 id>`).

---

## Child 3 — `feat(wear): show the unread count on the launcher widget`

### Task 3: Model from the unread count

**Files:**
- Modify: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/widget/LauncherWidgetModel.kt`
- Modify: `LauncherWidgetService.kt` (`@AndroidEntryPoint`, inject `WatchDmRepository`, `widget = LauncherWidget { repository.latestUnreadCount() }`)
- Test: `wear/app/src/test/kotlin/net/kikin/nubecita/wear/widget/LauncherWidgetModelTest.kt`

**Interfaces — Produces:** `fun launcherWidgetModel(unread: Int?): LauncherWidgetModel`.

- [ ] **Step 1: Failing tests** — `null` → `(countLabel = null, hasUnread = false)`; `0` → `("0", false)` rendered as "No new messages"; `1` → `("1", true)`; `99` → `("99", true)`; `100` → `("99+", true)` (Review Focus 5).
- [ ] **Step 2: Run** — FAIL (unresolved).
- [ ] **Step 3: Implement** using `unreadDmsLabel`; widget content branches on `countLabel == null` / `hasUnread`.
- [ ] **Step 4: Run** — PASS; extend `LauncherWidgetRenderTest` with `LauncherWidget { 0 }` and `LauncherWidget { 100 }` so every model state serializes; child gate.
- [ ] **Step 5: Commit** (with Task 4) `feat(wear): show the unread count on the launcher widget`.

### Task 4: Push updates on DM sync

**Files:**
- Create: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/widget/LauncherWidgetUpdater.kt`
- Modify: `wear/app/src/main/kotlin/net/kikin/nubecita/wear/di/WearComplicationModule.kt` → add `@Binds @IntoSet fun bindLauncherWidgetUpdater(impl: LauncherWidgetUpdater): DmSyncObserver` (rename module to `WearSurfacesModule` only if trivial)
- Test: `wear/data/src/test/kotlin/net/kikin/nubecita/wear/data/WatchSyncIngesterTest.kt` (only if the case below is missing)

**Interfaces — Consumes:** `DmSyncObserver.onDmsChanged()`; `LauncherWidget(...).triggerUpdateAll(context)`.

- [ ] **Step 1:** Confirm `WatchSyncIngester` notifies `DmSyncObserver`s when DMs are **cleared** (sign-out / account switch), not only on new snapshots. If no existing test pins it, add `clearingDms_notifiesObservers` to `WatchSyncIngesterTest` (Review Focus 4); if it fails, make the ingester notify on clear.
- [ ] **Step 2: Implement** `LauncherWidgetUpdater @Inject constructor(@ApplicationContext context) : DmSyncObserver` — `onDmsChanged()` launches `triggerUpdateAll` on an application-scoped coroutine scope the app already provides (no new scope type).
- [ ] **Step 3: Gate**, then bench smoke: place the widget; change the bench DM fixture's unread count (or trigger the bench sync) and see the widget update without opening the app.
- [ ] **Step 4: Commit** (squashed into the Child 3 commit is fine).

---

## Child 4 — `chore(wear): ready the launcher widget for release`

### Task 5: Bench, quality and listing

**Files:** bench DM fixture (only if it lacks an unread count), `fastlane/metadata/android/*/` Wear listing text (the builder finds the Wear full-description file), `docs/` or openspec quality notes.

- [ ] **Step 1:** Bench render: the widget shows the bench unread count with no network (airplane mode on the emulator).
- [ ] **Step 2:** Check `WO-V9` and `WO-V10` (and the tile/widget items they point to) in the current Wear app quality guidelines; record pass/fail with evidence (screenshots) in the PR body; fix any fail.
- [ ] **Step 3:** Add the widget to the Wear Play listing text in the source locale and the localized locales the repo ships listings for.
- [ ] **Step 4: Top-of-stack gate:** `:app:assembleDebug`, `:wear:app:assembleBenchDebug`, all touched modules' lint, compose-expert review on `git diff origin/main...HEAD`, and the full emulator journey: picker → place → each tile → back → landing; sign out (bench toggle) → widget shows "Messages" with no count.
- [ ] **Step 5: Commit** `chore(wear): ready the launcher widget for release`.
