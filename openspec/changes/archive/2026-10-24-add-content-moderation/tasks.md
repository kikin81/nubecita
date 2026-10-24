# Tasks — add-content-moderation (bd nubecita-twmt.1, epic nubecita-twmt)

> This change is the `:core:moderation` foundation only. Settings UI (`twmt.2`), feed-mapping + model decision (`twmt.3`), and the feed/search/profile/post-detail render surfaces (`twmt.4`) are separate children/changes.

## 1. Module scaffold

- [x] 1.1 Create `:core:moderation` (`nubecita.android.library` + `nubecita.android.hilt`, `environment` flavor split) and add it to `settings.gradle.kts`.
- [x] 1.2 Dependencies: `:core:auth`, `:core:common`, atproto models/runtime, kotlinx-serialization-json, kotlinx-coroutines; test deps mirror `:core:feeds`.

## 2. Domain model

- [x] 2.1 `ContentLabel` enum (PORN/SEXUAL/GRAPHIC_MEDIA/NUDITY) with atproto label value + `isAdult`; `LabelVisibility { SHOW, WARN, HIDE }` (wire `ignore`/`show` → SHOW).
- [x] 2.2 `ModerationPrefs(adultContentEnabled, visibilities: Map<ContentLabel, LabelVisibility>)` with a `DEFAULT` seed (adult off; porn=HIDE, sexual=WARN, graphic-media=WARN, nudity=SHOW).
- [x] 2.3 `MediaModerationDecision` (Show | Warn(category, overridable) | FilterFromFeed(category)).

## 3. ContentModerator (pure)

- [x] 3.1 `ContentModerator.decide(labels, authorDid, prefs): MediaModerationDecision` — adult master-gate force-hide+noOverride rule, strongest-wins, viewer's-own-content exemption, honor self-labels + default labeler.
- [x] 3.2 Exhaustive unit tests: adult on/off × each category, multi-label strongest-wins, self vs author exemption, no-override when gate off.

## 4. ModerationPreferencesRepository

- [x] 4.1 Interface: `prefs: StateFlow<ModerationPrefs>`, `suspend fun refresh()`, `suspend fun setAdultContentEnabled(enabled)`, `suspend fun setVisibility(label, visibility)`.
- [x] 4.2 Default impl: `getPreferences` → decode raw `JsonObject` → parse the `preferences` array (reuse the `:core:feeds` approach) into `ModerationPrefs`; cache in a `MutableStateFlow`.
- [x] 4.3 Write-through: read-modify-write the whole preferences array (swap only our `adultContentPref`/`contentLabelPref` entries, preserve the rest) via `putPreferences` (`XrpcClient.procedure`, raw array body); update the cache.
- [x] 4.4 Hilt module binding the interface (`@Singleton`).
- [x] 4.5 Pure unit test for the prefs parse + read-modify-write merge (defaults when absent; other kinds preserved).

## 5. Verify

- [x] 5.1 `:core:moderation:testDebugUnitTest`, `:core:moderation:lintDebug`, `:app:assembleDebug`, spotless/sorted-deps green.
- [x] 5.2 Validate the OpenSpec change (`openspec validate add-content-moderation --strict`).
