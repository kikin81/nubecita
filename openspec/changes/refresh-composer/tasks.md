Epic: `nubecita-5nbm`. Delivered as one `gh stack` of three PRs, one per group below.

## 1. Shared current-account source (`nubecita-5nbm.1`, PR 1)

- [x] 1.1 Add `CurrentAccount` + `CurrentAccountRepository` (`StateFlow<CurrentAccount?>`) to `:core:profile`, with a `@Singleton` default implementation seeded from `SessionStateProvider` and refreshed once per session via `ActorProfileRepository.fetchProfile`; emit `null` on sign-out. Unit tests: immediate seed, enrichment, handle fallback, fetch failure keeps the seed, one fetch per session, sign-out clears (`CurrentAccountRepositoryTest`).
- [x] 1.2 Hilt binding in `:core:profile`'s DI module. Unit test: none (wiring); covered by 1.4's bench smoke.
- [x] 1.3 Make `:core:profile` flavored (`nubecita.android.flavors`) and add a `src/bench/` fake binding a fixed `CurrentAccount` with no network. Update any `:core:profile:` task references to the `…ProductionDebug…` names (only a historical plan doc referenced them; left as is). Verified by bench smoke instead of a unit test: no module has bench-flavor unit tests and CI runs only production ones.
- [x] 1.4 Move `DefaultOutgoingPostStore` onto `CurrentAccountRepository` (drop its private profile fetch). Update `DefaultOutgoingPostStoreTest` to fake the repository and assert the outgoing `AuthorUi` uses its display name and avatar. Bench smoke: queue a post and confirm the outgoing card renders the bench identity.

## 2. Identity row, borderless field, scrolling body, summary line (`nubecita-5nbm.2`, PR 2)

- [ ] 2.1 Add `author: AuthorUi?` to `ComposerState`; `ComposerViewModel` collects `CurrentAccountRepository` into it; add `:core:profile` to `:feature:composer:impl`. Unit tests in `ComposerViewModelTest`: author populated from the repository; updates when the repository enriches.
- [ ] 2.2 Avatar (40dp) + display-name line (`labelMedium` / `onSurfaceVariant`, handle fallback); re-align suggestions, attachments, cards and quote to the text column. Screenshot: update `ComposerScreenScreenshotTest` fixtures (empty, typed, with photos, reply) to include the identity row, light + dark.
- [ ] 2.3 Replace `OutlinedTextField` with `BasicTextField(state = textFieldState, decorator = …)` (placeholder only), keeping `composer_text_field`, focus requester and `contentReceiver`. Screenshot: same fixtures, no outline/label; unit: existing paste test still passes.
- [ ] 2.4 Scrolling body + single IME owner (`contentWindowInsets = safeDrawing.exclude(ime)`, `consumeWindowInsets`, bottom padding). Screenshot: add a long-draft + quoted-post fixture showing the last card reachable.
- [ ] 2.5 Bring inline mention suggestions into view when they appear (`BringIntoViewRequester`). Screenshot: suggestions fixture under a long draft. Device pass: type `@` near the bottom of a long post on the Fold cover screen.
- [ ] 2.6 Quiet summary line (language label + audience on top-level posts, `" · "` separator, reusing the chip label formatting). Unit: label formatting tests for default / single / overflow / reply. Screenshot: summary line in the default and reply fixtures.
- [ ] 2.7 Device pass on the Fold 8 cover screen and an unfolded foldable (dialog), bench build: identity row, borderless field, scroll with keyboard open.

## 3. Floating toolbar with Post FAB, taller thumbnails (`nubecita-5nbm.3`, PR 3)

- [ ] 3.1 `ComposerFloatingToolbar`: `HorizontalFloatingToolbar` (`standardFloatingToolbarColors`) with Add image, GIF, Language, Audience (`NubecitaIconName.Settings`, top-level only), each an icon-only `IconButton` named by its icon's `contentDescription` (no extra `onClickLabel`, so the label is announced once); existing enablement rules. Compose test: each action exposes exactly one label and the button role. Screenshot: toolbar in empty / with photos / with GIF / reply fixtures, light + dark, phone + `adaptiveDialog()` preview.
- [ ] 3.2 Post as `StandardFloatingActionButton`: disabled emphasis + `semantics { disabled() }` + no-op when `canPost` is false; `NubecitaWavyProgressIndicator` while `Submitting`. Unit/Compose test: FAB disabled semantics and submitting state. Screenshot: submitting fixture.
- [ ] 3.3 Anchor the toolbar bottom-center with `imePadding()`; set the body's bottom padding from the toolbar height. Screenshot: keyboard-free fixture shows content clear of the toolbar.
- [ ] 3.4 Remove `ComposerOptionsChipRow` and the chip composables' chip role, the top-bar Post button (keep close + counter, with the `// reserved for drafts entry point` note) and the attachment row's leading add button. Update or delete `ComposerScreenLanguageChipScreenshotTest` accordingly.
- [ ] 3.5 Taller attachment thumbnails (~120dp), reorder / ALT badge / remove / tap-for-alt unchanged. Screenshot: photos fixture; unit: existing reorder test still passes.
- [ ] 3.6 Close `nubecita-86m` as superseded by `nubecita-5nbm`; delete its placeholder comments in `ComposerScreen.kt` / `ComposerOptionsChipRow.kt`.
- [ ] 3.7 Top-of-stack gate: `:app:assembleDebug`, touched-module lint, compose-expert review on the cumulative stack diff, CI baseline regeneration (`update-baselines`), and a device pass on the Fold cover screen + unfolded foldable (toolbar above the keyboard in both).
