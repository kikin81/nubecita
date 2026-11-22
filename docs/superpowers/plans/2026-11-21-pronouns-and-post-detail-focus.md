# Pronouns, cache-first profiles & threads, post-detail focus — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Bluesky profile pronouns (read + edit), a container-less focused post with a Follow toggle, and Room-backed cache-first profile headers and post-detail threads — as one `gh stack`.

**Architecture:** Two new Room grains (`cached_profile`, `thread_snapshot` + `thread_snapshot_item`) in one v11→v12 AutoMigration. Profile headers cache the wire `profileViewDetailed` blob behind a `:core:profile` `ProfileCache`; threads cache the mapper's *raw* flattened entries (kind/uri/depth) with post bodies in `cached_post`, re-mapped with current moderation on read. Repositories gain an additive `cached…()` read; ViewModels render it immediately, then the existing network fetch replaces it. UI: a new `FocusPostCard` in `:designsystem` reusing PostCard's internal body/embed/action pieces.

**Tech Stack:** Kotlin 2.3, Compose M3 Expressive, Room (AutoMigration), Hilt, kotlinx-serialization, atproto-kotlin 9.17.1, JUnit5 + MockK + Turbine, AGP screenshot test suites.

**Spec:** `docs/superpowers/specs/2026-11-21-pronouns-and-post-detail-focus-design.md`

## Global Constraints

- No atproto SDK bump: `atproto = "9.17.1"`; use `ProfileViewBasic.pronouns`, `ProfileView.pronouns`, `ProfileViewDetailed.pronouns`, record key `pronouns`.
- Pronouns: `maxGraphemes = 20`, `maxLength = 200` bytes; free-form; saved value is exactly what was picked/typed; blank ⇒ key removed.
- Room: exactly one bump, **v11 → v12**, `@AutoMigration(from = 11, to = 12)`, commit `core/database/schemas/.../12.json`.
- Caps: **200** `cached_profile` rows per account (own DID never evicted); **50** thread snapshots per account, by `last_opened_at`.
- Cache JSON config identical to `:core:feed-cache` `CacheJson`: `ignoreUnknownKeys = true`, `explicitNulls = false`.
- Presets: en `she/her`, `he/him`, `they/them`; es-419 `ella`, `él`, `elle`; pt-BR `ela`, `ele`, `elu`. Every new string ships en + es-419 + pt-BR in the same commit.
- Feed/reply `PostCard` output unchanged — no baseline churn outside: post detail, profile hero, edit profile, composer reply parent.
- Follow-toggle visibility comes only from a **fresh** network emission, never from Room.
- `Scaffold`s set `containerColor = MaterialTheme.colorScheme.surface`; focused post sits on `surface` with a **16dp** horizontal gutter, no container.
- Flavored modules (use `…ProductionDebug…` task names): `:core:profile`, `:core:posts`, `:core:post-interactions`, `:feature:profile:impl`, `:feature:composer:impl`. Unflavored: `:core:database`, `:core:feed-mapping`, `:data:models`, `:designsystem`, `:feature:postdetail:impl`.
- Interface changes: update **every** implementor, including bench fakes (`BenchFakeProfileRepository`, `BenchFakePostThreadRepository`). New interface methods get a default body so fakes compile unchanged where that's semantically right.
- Commits: Conventional Commits, `Refs: <child-bd-id>` footer, no `--no-verify`, no session footer.

## Review Focus

1. **Handle vs DID lookups** — profile routes may carry a handle; a cached row must be found by either. (Task 1.2 `byActor` test; Task 3.1.)
2. **Account switch** — a cached profile/thread of account A must never render for account B. (Task 1.3 clear test; every DAO query keyed by `account_did`.)
3. **Pronouns over 20 graphemes / emoji / whitespace-only** — Done disabled when over limit; whitespace-only saves as removal. (Task 4.1 VM tests.)
4. **Post deleted or author blocked since caching** — definitive refresh failure must drop the snapshot and show the existing NotFound/Blocked state, not stale content. (Task 5.2 VM test.)
5. **Rapid Follow/Following taps** — a second tap while a request is pending is ignored; failure rolls back and snackbars. (Task 6.2 VM tests.)

---

## Stack layout

One `gh stack`, bottom → top. Each child = one bd child issue = one branch `<type>/<child-id>-<slug>` = one PR titled as a Conventional Commit. Every child PR is titled `feat:`, so the epic cuts one minor release and reaches Play.

| # | Branch scope | PR title |
|---|---|---|
| 1 | Room v12 grains | `feat(database): add profile and thread snapshot caches (v12)` |
| 2 | Pronouns + follow plumbing in models/mappers | `feat(models): carry pronouns and follow state on authors and profiles` |
| 3 | Cache-first profile header + hero pronouns | `feat(profile): open profiles from cache and show pronouns` |
| 4 | Edit pronouns sheet + save | `feat(profile): edit pronouns` |
| 5 | Cache-first post detail | `feat(postdetail): open threads from cache` |
| 6 | Focused-post redesign + Follow toggle | `feat(postdetail): redesign the focused post` |
| 7 | Composer reply-parent pronouns | `feat(composer): show the reply parent's pronouns` |

**Branching:** each child starts with its branch — Child 1 from an up-to-date `main` with `gh stack init feat/<child-id>-<slug>`, every later child from the top of the stack with `gh stack add feat/<child-id>-<slug>` — before its first task.

Per-child gate before `gh stack submit --auto --open`: lint of the touched modules + their unit tests. Top-branch gate before `gh stack merge --squash --yes`: `./gradlew :app:assembleDebug :app:assembleBenchDebug`, all touched modules' lint, compose-expert review on `git diff origin/main...HEAD`, bench device pass.

---

## Child 1 — Room v12 grains (`:core:database`, `:core:feed-cache`)

### Task 1.1: Entities, migration, schema

**Files:**
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/model/CachedProfileEntity.kt`
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/model/ThreadSnapshotEntity.kt` (both snapshot entities)
- Modify: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/NubecitaDatabase.kt` (entities, `version = 12`, `AutoMigration(from = 11, to = 12)`, two abstract DAO getters)
- Modify: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/di/DaosModule.kt`
- Create (generated): `core/database/schemas/net.kikin.nubecita.core.database.NubecitaDatabase/12.json`
- Test: `core/database/src/test/.../MigrationTest` (extend the existing migration test class; find it with `git grep -l MigrationTestHelper core/database`)

**Interfaces — Produces:**
```kotlin
@Entity(tableName = "cached_profile", primaryKeys = ["account_did", "did"],
        indices = [Index(value = ["account_did", "handle"])])
data class CachedProfileEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    val did: String,
    val handle: String,
    @ColumnInfo(name = "profile_blob") val profileBlob: String,   // wire profileViewDetailed JSON
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Instant,
)

@Entity(tableName = "thread_snapshot", primaryKeys = ["account_did", "focus_uri"])
data class ThreadSnapshotEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "focus_uri") val focusUri: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Instant,
)

@Entity(tableName = "thread_snapshot_item", primaryKeys = ["account_did", "focus_uri", "position"],
        indices = [Index(value = ["account_did", "post_uri"])])
data class ThreadSnapshotItemEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "focus_uri") val focusUri: String,
    val position: Int,
    val kind: String,          // ThreadSnapshotKind.* constants below
    @ColumnInfo(name = "post_uri") val postUri: String,
    val depth: Int,            // 0 for ancestor/focus; reply depth ≥ 1
    @ColumnInfo(name = "author_did") val authorDid: String?,  // Blocked only
)

object ThreadSnapshotKind { const val ANCESTOR = "ancestor"; const val FOCUS = "focus"
  const val REPLY = "reply"; const val BLOCKED = "blocked"; const val NOT_FOUND = "notfound" }
```

- [ ] **Step 1:** Add the entities and register them; bump to 12 with the AutoMigration.
- [ ] **Step 2:** Generate the schema: `./gradlew :core:database:compileDebugKotlin` → expect `12.json` created. `git add` it.
- [ ] **Step 3: Failing test** `migrate11To12_createsCacheTables` — open v11 via `MigrationTestHelper`, insert one `cached_post` row, `runMigrationsAndValidate(12)`, assert the row survives and `SELECT count(*) FROM cached_profile` / `thread_snapshot` / `thread_snapshot_item` each return 0.
- [ ] **Step 4:** `./gradlew :core:database:testDebugUnitTest --tests '*Migration*'` → PASS.
- [ ] **Step 5:** Commit `feat(database): add cached_profile and thread snapshot tables (v12)`.

### Task 1.2: DAOs

**Files:**
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/CachedProfileDao.kt`
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/ThreadSnapshotDao.kt`
- Modify: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/CachedPostDao.kt` (`deleteUnreferenced` adds `AND NOT EXISTS (SELECT 1 FROM thread_snapshot_item WHERE thread_snapshot_item.account_did = cached_post.account_did AND thread_snapshot_item.post_uri = cached_post.uri)`)
- Test: `core/database/src/test/.../CachedProfileDaoTest.kt`, `ThreadSnapshotDaoTest.kt`, extend the existing `CachedPostDao` test

**Interfaces — Produces:**
```kotlin
@Dao interface CachedProfileDao {
    @Upsert suspend fun upsert(row: CachedProfileEntity)
    /** Matches [actor] against did OR handle. */
    suspend fun byActor(accountDid: String, actor: String): CachedProfileEntity?
    suspend fun touch(accountDid: String, did: String, at: Instant)
    suspend fun delete(accountDid: String, did: String)
    suspend fun deleteByActor(accountDid: String, actor: String)
    /** Deletes all but the [cap] most-recently-opened rows, never [keepDid]. */
    suspend fun trimToCap(accountDid: String, cap: Int, keepDid: String)
    suspend fun clearAccount(accountDid: String)
}

@Dao abstract class ThreadSnapshotDao {
    /** Deletes old items for the focus, inserts [snapshot] + [items]. */
    @Transaction open suspend fun replace(snapshot: ThreadSnapshotEntity, items: List<ThreadSnapshotItemEntity>)
    abstract suspend fun snapshot(accountDid: String, focusUri: String): ThreadSnapshotEntity?
    abstract suspend fun items(accountDid: String, focusUri: String): List<ThreadSnapshotItemEntity> // ORDER BY position
    abstract suspend fun touch(accountDid: String, focusUri: String, at: Instant)
    @Transaction open suspend fun delete(accountDid: String, focusUri: String)  // both tables
    /** Deletes snapshots (and their items) beyond the [cap] most recently opened. */
    @Transaction open suspend fun evictBeyond(accountDid: String, cap: Int)
    @Transaction open suspend fun clearAccount(accountDid: String)
}
```

- [ ] **Step 1: Failing tests** (in-memory Room, Robolectric as the existing DAO tests do):
  - `byActor_matchesDidOrHandle` — upsert did=`did:plc:a`, handle=`a.test`; `byActor(acct,"did:plc:a")` and `byActor(acct,"a.test")` both non-null; other account → null.
  - `trimToCap_keepsOwnAndMostRecent` — 5 rows, cap 2, keepDid = oldest → remaining = {oldest, newest}.
  - `replace_overwritesItems` — replace with 3 items then 1 item → `items()` size 1.
  - `evictBeyond_dropsLeastRecentlyOpened` — 3 snapshots, cap 2 → least-recent snapshot and its items gone.
  - `deleteUnreferenced_keepsPostsReferencedBySnapshot` — a `cached_post` row referenced only by a `thread_snapshot_item` survives; after `ThreadSnapshotDao.delete`, it is collected.
- [ ] **Step 2:** Run → FAIL (unresolved). **Step 3:** Implement queries. **Step 4:** `./gradlew :core:database:testDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit `feat(database): add profile and thread snapshot DAOs`.

### Task 1.3: Account purge

**Files:**
- Modify: `core/feed-cache/src/main/kotlin/net/kikin/nubecita/core/feedcache/FeedRepository.kt` (impl `clearAccount` ~L502: add `cachedProfileDao.clearAccount` and `threadSnapshotDao.clearAccount` inside the same `transactionRunner.run`; inject both DAOs)
- Test: the existing `FeedRepository` clearAccount test in `core/feed-cache/src/test`

- [ ] **Step 1: Failing test** `clearAccount_alsoClearsProfileAndThreadCaches` — verify both DAOs' `clearAccount(did)` called (MockK) or rows gone (in-memory, matching the existing test's style).
- [ ] **Step 2–4:** Implement; `./gradlew :core:feed-cache:testProductionDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit; `./gradlew :core:database:lintDebug :core:feed-cache:lintProductionDebug`; `gh stack submit --auto --open`.

---

## Child 2 — Pronouns + follow plumbing (`:data:models`, `:core:feed-mapping`, `:feature:profile:impl`, `:feature:composer:impl`)

### Task 2.1: AuthorUi fields

**Files:**
- Modify: `data/models/src/main/kotlin/net/kikin/nubecita/data/models/AuthorUi.kt` — add `val pronouns: String? = null`, `val viewerFollowUri: String? = null` (after `verifiedBadge`), KDoc.
- Modify: `core/feed-mapping/src/main/kotlin/net/kikin/nubecita/core/feedmapping/FeedMapping.kt` — `ProfileViewBasic.toAuthorUi()` and `ProfileView.toAuthorUi()` set `pronouns = pronouns?.trim()?.takeIf { it.isNotEmpty() }`, `viewerFollowUri = viewer?.following?.raw`.
- Test: `core/feed-mapping/src/test/.../FeedMappingTest` (existing)

- [ ] **Step 1: Failing tests** `toAuthorUi_mapsPronounsAndFollow` (pronouns `"he/him"`, `viewer.following` = an AtUri → both mapped) and `toAuthorUi_blankPronounsIsNull` (`"  "` → null).
- [ ] **Step 2–4:** Implement; `./gradlew :core:feed-mapping:testDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit `feat(models): carry pronouns and follow uri on AuthorUi`.

### Task 2.2: ProfileHeaderUi + ParentPostUi

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileContract.kt` — `ProfileHeaderUi.pronouns: String? = null`.
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/AuthorProfileMapper.kt` — `toProfileHeaderUi` maps trimmed non-blank pronouns.
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/state/ParentPostUi.kt` — `val pronouns: String? = null`.
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/data/DefaultParentFetchSource.kt` (~L142) — map `author.pronouns` trimmed non-blank.
- Test: existing `AuthorProfileMapperTest`, `DefaultParentFetchSourceTest`

- [ ] **Step 1: Failing tests** `toProfileHeaderUi_mapsPronouns`, `parentFetch_mapsPronouns` (`"she/her"` in, `"she/her"` out; blank → null).
- [ ] **Step 2–4:** Implement; `./gradlew :feature:profile:impl:testProductionDebugUnitTest :feature:composer:impl:testProductionDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit; lint `:data:models:lintDebug :core:feed-mapping:lintDebug :feature:profile:impl:lintProductionDebug :feature:composer:impl:lintProductionDebug`; `gh stack submit --auto --open`.

---

## Child 3 — Cache-first profile header + hero pronouns

### Task 3.1: `ProfileCache` in `:core:profile`

**Files:**
- Create: `core/profile/src/main/kotlin/net/kikin/nubecita/core/profile/ProfileCache.kt` (interface)
- Create: `core/profile/src/main/kotlin/net/kikin/nubecita/core/profile/internal/RoomProfileCache.kt`
- Create: `core/profile/src/main/kotlin/net/kikin/nubecita/core/profile/di/ProfileCacheModule.kt` (`@Binds`, in `main` — Room is local, so bench uses it too)
- Modify: `core/profile/build.gradle.kts` — add `implementation(project(":core:database"))`, `libs.kotlinx.serialization.json`, `libs.atproto.models`, kotlin-serialization plugin only if needed.
- Test: `core/profile/src/test/.../RoomProfileCacheTest.kt`

**Interfaces — Produces:**
```kotlin
interface ProfileCache {
    /** Cached profile for [actor] (DID or handle) under the signed-in account, touching last_opened_at; null if none or signed out. */
    suspend fun read(actor: String): ProfileViewDetailed?
    /** Upsert under the signed-in account, then trim to 200 keeping the account's own DID. */
    suspend fun write(profile: ProfileViewDetailed)
    /** Drop the row matching [actor] (DID or handle). */
    suspend fun remove(actor: String)
}
```
Constructor deps: `CachedProfileDao`, `SessionStateProvider`, `Clock` (whatever `:core:common` already injects for time; else `kotlin.time.Clock.System` behind a constructor default), `@IoDispatcher`. Private `ProfileCacheJson` with the Global Constraints config.

- [ ] **Step 1: Failing tests** (MockK DAO): `write_upsertsAndTrimsKeepingOwnDid` (verify `trimToCap(acct, 200, acct)`), `read_decodesAndTouches`, `read_signedOut_returnsNull_noDaoCall`, `read_corruptBlob_returnsNull_andDeletes`.
- [ ] **Step 2–4:** Implement; `./gradlew :core:profile:testProductionDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit `feat(profile): add a Room-backed profile cache`.

### Task 3.2: Repository read-through/write-through

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/ProfileRepository.kt` — add
  `suspend fun cachedHeader(actor: String): ProfileHeaderWithViewer? = null` (default ⇒ `BenchFakeProfileRepository` unchanged).
- Modify: `.../data/DefaultProfileRepository.kt` — inject `ProfileCache`; `cachedHeader` = `profileCache.read(actor)?.toProfileHeaderWithViewer(viewerDid, currentPrefs)`; `fetchHeader` success → `profileCache.write(response)`; failure that `isDefinitiveProfileGone()` → `profileCache.remove(actor)`. No extra write in `updateProfile`: a save emits `ownProfileUpdates`, the own-profile screen re-runs `fetchHeader`, and that writes through.
- Create: `.../data/ProfileErrors.kt` — `internal fun Throwable.isDefinitiveProfileGone(): Boolean` = `this is XrpcError && (errorName in setOf("AccountDeactivated", "AccountTakedown") || (errorName == "InvalidRequest" && message?.contains("not found", ignoreCase = true) == true))`. Verify the error names against a live `getProfile` of a deactivated handle during the device pass; adjust the set if the appview differs.
- Test: `DefaultProfileRepositoryTest` (existing), new `ProfileErrorsTest`

- [ ] **Step 1: Failing tests:** `fetchHeader_success_writesCache`, `fetchHeader_deactivated_removesCache`, `fetchHeader_networkError_keepsCache` (no `remove`), `cachedHeader_mapsWithCurrentPrefs`, `isDefinitiveProfileGone` table test (AccountDeactivated → true, AccountTakedown → true, InvalidRequest "Profile not found" → true, IOException → false, 502 XrpcError → false).
- [ ] **Step 2–4:** Implement; run `:feature:profile:impl:testProductionDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit.

### Task 3.3: ProfileViewModel renders cache first

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt` — in the header load: `cachedHeader(actor)?.let { apply it as the header with status Idle/Loaded }` **before** `fetchHeader`. On `fetchHeader` failure: if a cached header is showing and `!isDefinitiveProfileGone()` → keep it, `sendEffect(ShowError(...))`; if definitive → clear header and use today's error state.
- Test: `ProfileViewModelTest` (existing; remember `MainDispatcherExtension` is a *Standard* dispatcher — advance the scheduler)

- [ ] **Step 1: Failing tests:** `load_withCache_showsCachedHeaderBeforeNetwork` (gate `fetchHeader` with a `CompletableDeferred`; assert header present, no initial-loading status), `load_cacheThenNetworkFailure_keepsHeaderAndEmitsError`, `load_cacheThenDefinitiveGone_showsErrorState`.
- [ ] **Step 2–4:** Implement; PASS.
- [ ] **Step 5:** Commit.

### Task 3.4: Hero pronouns

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileHero.kt` (~L225) — wrap the `@handle` `Text` in a `Row` that appends `header.pronouns` (same `extendedTypography.handle` style, `onSurfaceVariant`, `spacedBy(8.dp)`, each `maxLines = 1`, pronouns `TextOverflow.Ellipsis`) when non-null. Keep the centered alignment.
- Test: add a screenshot preview `ProfileHeroWithPronouns` in the module's `src/screenshotTest`; existing hero previews (no pronouns) must not change.

- [ ] **Step 1:** Add preview + implementation.
- [ ] **Step 2:** `./gradlew :feature:profile:impl:compileProductionDebugScreenshotTestKotlin` → compiles. Baselines come from CI (`update-baselines` label); do not commit local renders.
- [ ] **Step 3:** Commit `feat(profile): show pronouns next to the handle`; lint `:core:profile:lintProductionDebug :feature:profile:impl:lintProductionDebug`; `gh stack submit --auto --open`.

---

## Child 4 — Edit pronouns

### Task 4.1: Route, state, events, save

**Files:**
- Modify: `feature/profile/api/src/main/kotlin/net/kikin/nubecita/feature/profile/api/EditProfile.kt` — `val pronouns: String? = null`.
- Modify: `feature/profile/impl/.../ProfileViewModel.kt` (~L259) — pass `pronouns = header.pronouns`.
- Modify: `feature/profile/impl/.../EditProfileContract.kt` — `internal const val MAX_PRONOUNS_GRAPHEMES = 20`; state: `pronouns: String = ""`, `pronounsSheet: PronounsSheetState? = null` where `internal data class PronounsSheetState(val input: String = "", val graphemes: Int = 0) { val isOverLimit get() = graphemes > MAX_PRONOUNS_GRAPHEMES }`; events `PronounsRowTapped`, `PronounsInputChanged(value: String)`, `PronounPresetTapped(value: String)`, `PronounsDone`, `PronounsRemoved`, `PronounsSheetDismissed`.
- Modify: `.../EditProfileViewModel.kt` — reducers; `isDirty` includes pronouns vs. `route.pronouns.orEmpty()`; save passes `pronouns = state.pronouns`. Grapheme count via the same helper `displayNameGraphemes` uses.
- Modify: `.../data/ProfileRepository.kt` + `DefaultProfileRepository.kt` — `updateProfile(..., pronouns: String?, links = …)`; `buildMergedRecord` calls `mergeText(merged, KEY_PRONOUNS, pronouns)` with `KEY_PRONOUNS = "pronouns"`. Update `BenchFakeProfileRepository` signature.
- Test: `EditProfileViewModelTest`, `DefaultProfileRepositoryUpdateProfileTest`

- [ ] **Step 1: Failing tests:**
  - `presetTapped_fillsInput`; `done_copiesInputToPronounsAndCloses`; `remove_clearsPronounsAndCloses`; `dismiss_discardsInput`.
  - `input21Graphemes_isOverLimit_doneIgnored`; `input "🏳️‍⚧️ they"` counts graphemes not chars.
  - `whitespaceOnly_done_savesAsRemoval` (state.pronouns = "" → repository called with `pronouns = ""`).
  - `updateProfile_setsPronounsKey` (`"he/him"` in body), `updateProfile_blankPronouns_removesKey`, and the existing `pronouns` preservation test still passes when `pronouns` param equals the existing value.
- [ ] **Step 2–4:** Implement; `./gradlew :feature:profile:impl:testProductionDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit.

### Task 4.2: Pronouns row + sheet UI

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/EditPronounsSheet.kt` — `@Composable internal fun EditPronounsSheet(state: PronounsSheetState, presets: ImmutableList<String>, onPreset: (String) -> Unit, onInput: (String) -> Unit, onDone: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)` — `ModalBottomSheet`; title, helper text, `FlowRow` of `FilterChip`s (selected = `input == preset`), `OutlinedTextField` with `n/20` supporting text (error colour when over), full-width `Button` Done (disabled when over limit), full-width `FilledTonalButton` Remove (disabled when input blank).
- Modify: `.../EditProfileScreen.kt` — after the Bio field (~L280), a read-only "Pronouns" field row (value or placeholder) that dispatches `PronounsRowTapped`; host the sheet when `state.pronounsSheet != null`. Presets from `stringArrayResource(R.array.edit_profile_pronoun_presets)`.
- Modify: `feature/profile/impl/src/main/res/{values,values-b+es+419,values-pt-rBR}/strings.xml` — `edit_profile_pronouns_label`, `edit_profile_pronouns_placeholder`, `edit_pronouns_title` ("Edit pronouns"), `edit_pronouns_helper` ("Your pronouns show next to your handle on your profile."), `edit_pronouns_done` ("Done"), `edit_pronouns_remove` ("Remove pronouns"), `edit_pronouns_close_content_description`, string-array `edit_profile_pronoun_presets` (values per Global Constraints).
- Test: screenshot previews `EditPronounsSheetEmpty`, `EditPronounsSheetPresetSelected`, `EditPronounsSheetOverLimit`, `EditProfileScreenWithPronouns` (wrap with `NubecitaCanvasPreviewTheme`).

- [ ] **Step 1:** Implement + previews. **Step 2:** `./gradlew :feature:profile:impl:compileProductionDebugScreenshotTestKotlin :feature:profile:impl:lintProductionDebug` → PASS (lint catches missing translations).
- [ ] **Step 3:** Commit `feat(profile): edit pronouns from a bottom sheet`; `gh stack submit --auto --open`.

---

## Child 5 — Cache-first post detail (`:core:posts`, `:feature:postdetail:impl`)

### Task 5.1: Raw thread entries + snapshot store

**Files:**
- Modify: `core/posts/src/main/kotlin/net/kikin/nubecita/core/posts/internal/PostThreadMapper.kt` — split into two stages:
  `internal data class RawThreadEntry(val kind: String /* ThreadSnapshotKind */, val uri: String, val depth: Int, val authorDid: String?, val post: PostView?)`
  `internal fun GetPostThreadResponseThreadUnion.toRawThreadEntries(): List<RawThreadEntry>` (same walk/order as today, no moderation) and
  `internal fun List<RawThreadEntry>.toThreadItems(prefs: ModerationPrefs, viewerDid: String?): ImmutableList<ThreadItem>` (today's moderation + `toPostUiCore`; entries whose post fails to map are dropped exactly as today). The existing `toThreadItems(prefs, viewerDid)` becomes `toRawThreadEntries().toThreadItems(prefs, viewerDid)`.
- Create: `core/posts/src/main/kotlin/net/kikin/nubecita/core/posts/internal/ThreadSnapshotStore.kt` — `internal class ThreadSnapshotStore @Inject constructor(threadSnapshotDao, cachedPostDao, sessionStateProvider, @IoDispatcher)` with
  `suspend fun read(focusUri: String): List<RawThreadEntry>?` (snapshot items joined to `cachedPostDao.byUris`, decoded; touches `last_opened_at`; null if no snapshot) ,
  `suspend fun readFocusOnly(focusUri: String): List<RawThreadEntry>?` (a `cached_post` row with a blob → single FOCUS entry),
  `suspend fun write(focusUri: String, entries: List<RawThreadEntry>)` (one transaction: upsert posts into `cached_post`, `replace` snapshot, `evictBeyond(acct, 50)`, then `cachedPostDao.deleteUnreferenced(acct)`),
  `suspend fun remove(focusUri: String)`.
  Private `ThreadCacheJson` (Global Constraints config); `PostView` → `CachedPostEntity` mirrors `:core:feed-cache` `toCachedPost` (uri, cid, author_did, indexed_at, text, post_blob).
- Modify: `core/posts/build.gradle.kts` — `implementation(project(":core:database"))`, `libs.kotlinx.serialization.json`.
- Test: `PostThreadMapperTest` (existing — must still pass unchanged), new `ThreadSnapshotStoreTest`

- [ ] **Step 1: Failing tests:** `rawEntries_thenItems_equalsLegacyMapping` (feed the existing mapper fixtures through both paths, assert equal), `rawEntries_replyDepths` (depth 1,2,1), `store_writeThenRead_roundTrips` (in-memory Room), `store_read_reappliesCurrentModeration` (write a post, read with prefs that hide its label → `toThreadItems` result marks it hidden/covered), `store_write_evictsBeyond50`.
- [ ] **Step 2–4:** Implement; `./gradlew :core:posts:testProductionDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit.

### Task 5.2: Repository + ViewModel

**Files:**
- Modify: `core/posts/src/main/kotlin/net/kikin/nubecita/core/posts/PostThreadRepository.kt` — add
  `public suspend fun cachedPostThread(uri: String): ImmutableList<ThreadItem>? = null` (default ⇒ `BenchFakePostThreadRepository` and the comments sheet untouched).
- Modify: `core/posts/src/main/kotlin/net/kikin/nubecita/core/posts/internal/DefaultPostThreadRepository.kt` — `cachedPostThread` = `(store.read(uri) ?: store.readFocusOnly(uri))?.toThreadItems(currentPrefs, viewerDid)`; `getPostThread` success → `store.write(uri, raw)`; success whose first item is NotFound/Blocked with no Focus, or an empty list → `store.remove(uri)`; failure classified NotFound (the same check `toPostDetailError()` uses for `PostDetailError.NotFound`) → `store.remove(uri)`.
- Modify: `feature/postdetail/impl/src/main/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailViewModel.kt` — `load()`: after setting `InitialLoading`, `cachedPostThread(route.postUri)?.let { seed interactions; setState(items = …, loadStatus = Refreshing) }`; then the existing `getPostThread`. On failure with cached items showing: non-NotFound → keep items, `loadStatus = Idle`, `sendEffect(ShowError(...))`; NotFound → today's `InitialError(NotFound)`. Add a `isShowingCache: Boolean` field to `PostDetailState` (true until the network result lands) — consumed by Child 6 for the Follow toggle.
- Test: `DefaultPostThreadRepositoryTest`, `PostDetailViewModelTest`

- [ ] **Step 1: Failing tests:** repo `getPostThread_success_writesSnapshot`, `getPostThread_notFound_removesSnapshot`, `getPostThread_networkError_keepsSnapshot`; VM `load_withCache_rendersCachedThenFresh` (gate network; assert cached items + `isShowingCache = true`, then fresh items + `false`), `load_cacheThenNetworkError_keepsItemsAndEmitsError`, `load_cacheThenNotFound_showsNotFound`, `load_focusOnlyCache_rendersFocusAlone`.
- [ ] **Step 2–4:** Implement; `./gradlew :core:posts:testProductionDebugUnitTest :feature:postdetail:impl:testDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit; lint `:core:posts:lintProductionDebug :feature:postdetail:impl:lintDebug`; `gh stack submit --auto --open`.

---

## Child 6 — Focused-post redesign + Follow toggle

### Task 6.1: `FocusPostCard` (`:designsystem`)

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/FocusPostCard.kt`
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt` — change `BodyText`, `TranslationFooter`, `TranslateChip`, `EmbedSlot`, `ActionRow` from `private` to `internal` (no behaviour change; PostCard baselines stay identical).
- Modify: `designsystem/src/main/res/{values,values-b+es+419,values-pt-rBR}/strings.xml` — `focus_post_follow` ("Follow"), `focus_post_following` ("Following"), plurals `focus_post_reposts`, `focus_post_quotes`, `focus_post_likes`, `focus_post_saves` ("%1$s repost(s)" …), `focus_post_handle_pronouns` ("@%1$s · %2$s").
- Test: screenshot previews in `designsystem/src/screenshotTest`: `FocusPostCardTextOnly`, `FocusPostCardWithEmbedAndPronouns`, `FocusPostCardFollowing`, `FocusPostCardZeroCounts`, `FocusPostCardLongNameNarrow` (320dp width); unit test `FocusPostCountsTest`.

**Interfaces — Produces:**
```kotlin
enum class FocusFollowState { Hidden, NotFollowing, Following }

@Composable
fun FocusPostCard(
    post: PostUi,
    modifier: Modifier = Modifier,
    followState: FocusFollowState = FocusFollowState.Hidden,
    onFollowToggle: () -> Unit = {},
    callbacks: PostCallbacks = PostCallbacks.None,
    videoEmbedSlot: (@Composable (EmbedUi.Video, cover: MediaCover?) -> Unit)? = null,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
    onQuotedImageClick: ((quotedPostUri: String, imageIndex: Int) -> Unit)? = null,
    animateLikeTap: Boolean = false,
    animateRepostTap: Boolean = false,
    isMediaRevealed: Boolean = false,
    onRevealMedia: () -> Unit = {},
    translation: PostTranslationUi? = null,
    onTranslate: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
)

/** Non-zero counts in display order: reposts, quotes, likes, saves. */
internal fun PostStatsUi.focusCounts(): List<Pair<FocusCountKind, Int>>
internal enum class FocusCountKind { Reposts, Quotes, Likes, Saves }
```
Layout (spec "Visual: the focused post"): `Column(Modifier.padding(horizontal = 16.dp))` → header `Row` [avatar 44dp · `Column(weight 1f)`{name `titleMedium` + badge, 1 line ellipsis; handle line via `focus_post_handle_pronouns` or plain `@handle`, `bodyMedium` `onSurfaceVariant`, 1 line ellipsis} · Follow `ToggleButton` (M3 Expressive; `checked = followState == Following`; small size; hidden when `Hidden`)] → `BodyText` with a style one step above PostCard's `bodyLarge` (builder picks the token) → `EmbedSlot` → absolute time (`DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)`-style split as `time · date`, from `LocalLocale`, keyed so screenshots are deterministic — see existing NOON-UTC preview convention) → counts row (`FlowRow`, bold number + label; omitted when empty) → `HorizontalDivider` → `ActionRow`.

- [ ] **Step 1: Failing test** `focusCounts_omitsZeros_inOrder` (`PostStatsUi(repostCount=44, quoteCount=0, likeCount=193, bookmarkCount=102)` → `[Reposts 44, Likes 193, Saves 102]`).
- [ ] **Step 2–4:** Implement; `./gradlew :designsystem:testDebugUnitTest :designsystem:compileDebugScreenshotTestKotlin` → PASS. Existing PostCard baselines must be untouched (`git status designsystem/src/screenshotTestDefaultDebug` clean).
- [ ] **Step 5:** Commit `feat(designsystem): add FocusPostCard`.

### Task 6.2: Follow state + wiring in post detail

**Files:**
- Modify: `feature/postdetail/impl/.../PostDetailContract.kt` — state `focusFollow: FocusFollowUi = FocusFollowUi()` with `internal data class FocusFollowUi(val state: FocusFollowState = FocusFollowState.Hidden, val followUri: String? = null, val isPending: Boolean = false, val followedInSession: Boolean = false)`; event `FocusFollowTapped`.
- Modify: `.../PostDetailViewModel.kt` — inject `FollowRepository` and read viewer DID from `SessionStateProvider` (already a dep of `:core:auth`). Derivation, applied only on **fresh** (non-cache) results:
  - author DID == viewer DID, or `post.viewer.isAuthorBlockedByViewer || isAuthorBlockingViewer` → `Hidden`;
  - `followedInSession` → keep current state;
  - `author.viewerFollowUri != null` → `Hidden`;
  - else `NotFollowing`.
  While `isShowingCache` → `Hidden`. Tap: ignore if `isPending`; `NotFollowing` → optimistic `Following(isPending = true, followedInSession = true)` → `follow(did)` → store uri / rollback + `ShowError`; `Following` → optimistic `NotFollowing(pending)` → `unfollow(uri)` → rollback + `ShowError` on failure.
- Modify: `.../PostDetailScreen.kt` (~L603–635) — replace the `Surface { PostCard(...) }` focus block with `FocusPostCard(...)` passing the same per-item arguments plus `followState`, `onFollowToggle = { onEvent(FocusFollowTapped) }`; add a `HorizontalDivider` after it. Delete `FOCUS_CONTAINER_CORNER_RADIUS`.
- Test: `PostDetailViewModelTest`; refresh `PostDetailScreen` screenshot previews (CI regenerates baselines via `update-baselines`).

- [ ] **Step 1: Failing tests:** `follow_hiddenWhileShowingCache`, `follow_hiddenForOwnPost`, `follow_hiddenWhenAlreadyFollowing`, `follow_notFollowing_showsFollow`, `followTap_optimisticThenStoresUri`, `followTap_failure_rollsBackAndEmitsError`, `followTap_whilePending_ignored`, `followingTap_unfollows`, `refreshAfterInSessionFollow_staysVisibleAsFollowing`.
- [ ] **Step 2–4:** Implement; `./gradlew :feature:postdetail:impl:testDebugUnitTest :feature:postdetail:impl:lintDebug` → PASS.
- [ ] **Step 5:** Commit `feat(postdetail): redesign the focused post with a follow toggle`; `gh stack submit --auto --open`.

**Baselines (whole stack, once):** add the `update-baselines` label to the PRs whose previews changed — Children 3, 4, 6, 7 — lowest first, waiting for each bot commit, then `gh stack sync` + `gh stack submit --auto --open` so the branches above pick it up.

---

## Child 7 — Composer reply-parent pronouns

### Task 7.1

**Files:**
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerReplyParentSection.kt` (~L100) — pass `pronouns = post.pronouns` to `ComposerContextPostBody`.
- Modify: `ComposerContextPostBody` (find with `git grep -n "fun ComposerContextPostBody" feature/composer/impl`) — new param `pronouns: String? = null`, declared after `modifier` (defaulted params go after `modifier`, or ktlint/lint fail); render after the display name, `onSurfaceVariant`, 1 line, ellipsized before the name. The quote section passes nothing (unchanged).
- Test: screenshot preview `ComposerReplyParentWithPronouns`.

- [ ] **Step 1:** Implement + preview. **Step 2:** `./gradlew :feature:composer:impl:compileProductionDebugScreenshotTestKotlin :feature:composer:impl:lintProductionDebug` → PASS.
- [ ] **Step 3:** Commit `feat(composer): show the reply parent's pronouns`; `gh stack submit --auto --open`.

---

## Top-of-stack gate (before `gh stack merge --squash --yes`)

- [ ] `./gradlew :app:assembleDebug :app:assembleBenchDebug` → BUILD SUCCESSFUL (bench catches fake-implementor drift).
- [ ] Lint every touched module (flavored names per Global Constraints) and `./gradlew checkSortDependencies`.
- [ ] compose-expert skill review on `git diff origin/main...HEAD`.
- [ ] Device pass, bench build on the foldable: profile hero pronouns; edit → sheet → save → hero updates; kill + reopen profile ⇒ header instant; post detail reopen ⇒ instant thread; Follow → Following → Unfollow; airplane mode reopen ⇒ cached view + snackbar; composer reply parent pronouns. Then production build: a deactivated account's profile (verifies `isDefinitiveProfileGone` names).
- [ ] Every PR: CI green, all review threads resolved, newest bot review newer than last commit.
- [ ] After merge: `bd close` all children, the epic, `nubecita-dwmf`; count `Release` runs (expect 1).
