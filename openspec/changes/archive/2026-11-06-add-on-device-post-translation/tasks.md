# Tasks — Add On-Device Post Translation

Beads epic `nubecita-s6xk`. Lands as one `gh stack`, bottom to top; nothing reaches
`main` until `gh stack merge`.

## 1. `nubecita-s6xk.1` — plumb `langs` into `PostUi` (PR 1, base `main`)

- [ ] 1.1 Add `langs: ImmutableList<String> = persistentListOf()` to `PostUi`.
- [ ] 1.2 Map it in `:core:feed-mapping` from the wire record.
- [ ] 1.3 Retain it through `:core:feed-cache` (projection + persisted row).
- [ ] 1.4 Update `PostUiFixtures` and every fixture construction site.
- [ ] 1.5 Unit tests: present / absent / empty / multi-tag.

## 2. `nubecita-s6xk.2` — resolve the reader's languages (PR 2)

- [ ] 2.1 `ReaderLanguages` in `:core:preferences`: device `LocaleListCompat`
      unioned with the stored never-translate list, ordered, BCP-47 subtag-matched.
- [ ] 2.2 Reuse the legacy-alias normalisation from `TranslationUrl.kt`
      (`iw`→`he`, `in`→`id`, `ji`→`yi`); extract it so both callers share it.
- [ ] 2.3 Target language = first device locale entry, overridable.
- [ ] 2.4 Unit tests: `es` matches `es-419`; `en-GB` matches `en`; empty locale list.

> No atproto source: `app.bsky.actor` has no content-language preference at SDK
> 9.13.0. Do not add an XRPC call for this.

## 3. `nubecita-s6xk.3` — `:core:translation` engine seam (PR 3)

- [ ] 3.1 New module (`nubecita.android.library` + `nubecita.android.hilt`), with an
      empty `consumer-rules.pro` — CI Build/Lint fails without it even though a
      local assemble passes.
- [ ] 3.2 `TranslationEngine`, `LanguageIdentifier`, `TranslationModelRepository`.
- [ ] 3.3 ML Kit implementations; no SDK type in any public signature.
- [ ] 3.4 Wi-Fi-gated download with `NotDownloaded` / `WaitingForUnmeteredNetwork` /
      `Downloading` / `Downloaded(sizeBytes)`; deferred downloads resume on Wi-Fi.
- [ ] 3.5 Per-language measured on-disk size, total, and delete.
- [ ] 3.6 Close translators on `ON_STOP`.
- [ ] 3.7 In-memory fakes in `src/test` (not test-fixtures), mirroring `:core:billing`.
- [ ] 3.8 Add `mlkit-translate`, `mlkit-language-id`, `kotlinx-coroutines-play-services`
      to the version catalog; run the **root** `checkSortDependencies`.

## 4. `nubecita-s6xk.4` — facet restoration (PR 4)

- [ ] 4.1 Pure-Kotlin `restoreFacets(original, translated, facets)` in `:core:translation`.
- [ ] 4.2 No-facet fast path → translated text, rendered plain.
- [ ] 4.3 Per-kind candidate detection tolerating `@ `, `# `, `https: //`.
- [ ] 4.4 Count-match gate per kind; on match restore **original span text** +
      original facet feature, in order; on mismatch render that kind plain.
- [ ] 4.5 Never remap original byte offsets.
- [ ] 4.6 Property tests: punctuation-adjacent mentions, CJK, zero-of-a-kind,
      count mismatch, hashtag-only post, emoji passthrough.
- [ ] 4.7 Regression cases pinned from the spike:
      `#PalacesandGardens`→`#palaceandGarden`, `@alice`→`@ alice`,
      `https://…`→`https: //…`, `#おはよう`→`# Good morning`.

## 5. `nubecita-s6xk.6` — cache translations (PR 5)

- [ ] 5.1 Room entity keyed by `(postUri, targetLanguage)`.
- [ ] 5.2 `@Transaction` write; `Flow` read; `asExternalModel()`.
- [ ] 5.3 Bounded + evictable.
- [ ] 5.4 Schema bump: commit the new `{N}.json`, add an `@AutoMigration`.

## 6. `nubecita-s6xk.7` — Translation settings page (PR 6)

- [ ] 6.1 `TranslationSettingsScreen` + VM, `NubecitaListGroup` rows.
- [ ] 6.2 Master toggle, default off.
- [ ] 6.3 Auto-translate list; never-translate list (doubles as "languages I read").
- [ ] 6.4 Downloaded languages with measured sizes, total, delete.
- [ ] 6.5 Extract the composer's `LanguagePicker` for reuse rather than forking it.
- [ ] 6.6 Strings + `es-419` + `pt-BR` in the same commit; run the **module's own** lint.
- [ ] 6.7 Screenshot tests for the page.

## 7. `nubecita-s6xk.5` — feed UI (PR 7, top of stack)

- [ ] 7.1 Feed-local translation state wrapper — **not** on `PostUi`.
- [ ] 7.2 Scroll-idle dispatch on `isScrollInProgress → false`, visible items only,
      `Dispatchers.Default`.
- [ ] 7.3 Language chip on the card (not the overflow sheet).
- [ ] 7.4 One prompt stating the size; accept → auto list + master toggle on;
      cellular → "Waiting for Wi-Fi" chip state.
- [ ] 7.5 Shimmer during flight; animated in-place swap.
- [ ] 7.6 Attribution + "Show original" (session-scoped, survives scroll).
- [ ] 7.7 `PostCard` translation params default to null so other surfaces are
      pixel-identical and their baselines do not churn.
- [ ] 7.8 Analytics incl. a counter on the facet count-mismatch branch; no new GA4
      custom dimensions.
- [ ] 7.9 Foreign-language posts in the bench fixture so the states are reachable offline.
- [ ] 7.10 Screenshot baselines via CI's `update-baselines` label — never committed
      from macOS.

## 8. Gate the stack top

- [ ] 8.1 `./gradlew :app:assembleDebug`.
- [ ] 8.2 Lint every touched module (flavored names where applicable —
      `:feature:feed:impl` and `:core:feed-cache` are flavored; check with
      `git grep -l nubecita.android.flavors`).
- [ ] 8.3 `compose-expert` review against the **cumulative** stack diff
      (`git diff origin/main...HEAD` from the top branch).
- [ ] 8.4 Device pass on a real device, bench flavor.
- [ ] 8.5 Re-run the frame-timing A/B and record the numbers on the top PR.
