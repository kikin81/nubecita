# Tasks

Epic: **nubecita-33bw**. Layers 1/2/4 are largely independent; 3 depends on 1; 5 depends on 1/2/4. Each layer lands as its own bd-workflow PR in dependency order, preceded by a per-layer adversarial review fan-out against this spec + the MVI/Compose conventions.

## 1. Icon + model — nubecita-33bw.1
- [ ] Add `PostAudience` / `ReplyAudience` (`Everyone` / `Nobody` / `Combination`) with `DEFAULT` to `:core:posting`, `@Stable`/`@Immutable`.
- [ ] Add `NubecitaIconName.Globe("")` (alphabetical) and regenerate the Material Symbols subset via the safe procedure (venv fonttools; verify `globe == U+E64C`; `drifted outlines: 0`); commit enum + `.ttf` together.
- [ ] Unit-test the model defaults and combination semantics.

## 2. AudiencePicker UI — nubecita-33bw.2
- [ ] Add `AudiencePicker` (ModalBottomSheet on Compact / Popup+Surface on Medium+) + `AudiencePickerContent`, mirroring `LanguagePicker`, holding its own draft with `onConfirm`/`onDismiss`.
- [ ] Implement Anyone/Nobody/Combination radio+checkbox mutual exclusion, the allow-quotes toggle, save-as-default checkbox, and reset.
- [ ] `@Preview` + screenshot tests for sheet and popup, light/dark.

## 3. Posting write (depends on 1) — nubecita-33bw.3
- [ ] Extend `PostingRepository.createPost(..., audience: PostAudience = PostAudience.DEFAULT)`.
- [ ] In `DefaultPostingRepository.createPost`, after the post `createRecord` returns, extract the rkey and write threadgate (if `reply != Everyone`) and postgate (if `!allowQuotes`) at that rkey, using named args.
- [ ] Make gate writes best-effort: failure logs (redacted) and returns post success via a typed result the composer maps to a snackbar.
- [ ] Unit-test each UI→record mapping (Everyone→none, Nobody→empty allow, Combination→exact rules, quotes-off→postgate) and that a gate-write failure doesn't fail the post.

## 4. Saved-default repository (depends on 1) — nubecita-33bw.4
- [x] Add `PostAudienceDefaultRepository` over `postInteractionSettingsPref`, mirroring `ModerationPreferencesRepository` (raw-JSON read-modify-write, `writeMutex`, seeded `MutableStateFlow<PostAudience>(DEFAULT)`, `refresh()`, `resetToDefault()`, optimistic+revert). Placed in `:core:moderation` (shared `getPreferences` array plumbing + flavor split); production `@Binds` + bench fake.
- [x] Unit-test parse/merge (preserving foreign entries, lexicon absent/empty semantics, round-trip), the `getPreferences`/`putPreferences` boundary over a Ktor `MockEngine`, optimistic publish + revert-on-failure, DEFAULT seed.
- [ ] (moved to layer 5) Wire `resetToDefault()` on sign-out + `refresh()` on sign-in via a coordinator — deferred to where the composer consumes the default and the session-lifecycle refresh becomes observable.

## 5. Chip + composer wiring (depends on 1, 2, 4) — nubecita-33bw.5
- [ ] Wire `PostAudienceDefaultRepository.refresh()` on sign-in / `resetToDefault()` on sign-out via a coordinator (mirror `ModerationPreferencesCoordinator`), registered in the production `ProductionBootstrapModule` `@IntoSet AppInitializer`.
- [ ] Add `ComposerAudienceChip` (mirror `ComposerLanguageChip`) in `ComposerOptionsChipRow`, gated on `replyToUri == null`, label "Visible to all"/"Interaction limited".
- [ ] Add `ComposerState.audience`, `ComposerEvent.AudienceSelectionConfirmed`, the VM reducer arm, pass `audience` into `createPost`, and pre-fill initial state from the saved-default repo (append-only constructor param).
- [ ] Add `showAudiencePicker` state in `ComposerScreen` and render the picker block; map the best-effort gate result to a snackbar.
- [ ] Unit-test (Composer VM) that `AudienceSelectionConfirmed` updates state, submit passes audience, pre-fill from default; VM/Compose test the chip is hidden when `replyToUri != null`.

## 6. Deferred ticket — nubecita-33bw.6
- [ ] File a follow-up bd issue: "select from your lists" (threadgate `listRule`), shown disabled in the picker until lists are supported.

## Verification
- [ ] `./gradlew :app:assembleDebug`
- [ ] `./gradlew :core:posting:testDebugUnitTest :core:moderation:testDebugUnitTest :feature:composer:impl:testDebugUnitTest`
- [ ] `./gradlew spotlessCheck :feature:composer:impl:lintDebug`
- [ ] Screenshot baselines regenerated on CI via the `update-baselines` label (icon showcase + picker).
