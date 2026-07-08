<!-- Task groups map 1:1 to beads epic nubecita-vw45 (tasks .1–.4). -->

## 1. Foundation — models + icon glyphs (bd nubecita-vw45.1)

- [x] 1.1 Add `enum VerifiedBadge { None, Verified, TrustedVerifier }` to `:data:models` (`@Stable`/`@Immutable` as appropriate).
- [x] 1.2 Add `VerificationUi(badge: VerifiedBadge, verifiers: ImmutableList<VerifierUi>)` and `VerifierUi(did, handle, displayName: String?, verifiedAt)` to `:data:models`. (`VerifierUi` is a *resolved* verifier — no `isValid` field; invalid entries are dropped and unresolved DIDs skipped in .3.)
- [x] 1.3 Add `verifiedBadge: VerifiedBadge = VerifiedBadge.None` to `AuthorUi`; add fixtures covering None/Verified/TrustedVerifier (mirror existing `*Fixtures`).
- [x] 1.4 Add the two Material Symbols glyphs — `check_circle` (Verified) and `verified` (Trusted Verifier) — to `:designsystem` `NubecitaIconName` + icon font via the careful SINGLE-glyph append (no full-font regen); verify against a shrink/release build path.
- [x] 1.5 Add a verified-blue design-system color token for the badge tint (distinct from the theme accent).
- [x] 1.6 Add the stateless `VerificationBadge(badge, modifier, size, ...)` atom in `:designsystem` that renders the glyph + tint for a given `VerifiedBadge` (renders nothing for `None`) with an accessible `contentDescription`; add `@Preview` + screenshot baselines.

## 2. Feed — mapping + PostCard badge (bd nubecita-vw45.2)

- [ ] 2.1 In `:core:feed-mapping` author mapper, derive `AuthorUi.verifiedBadge` from `verification` (`trustedVerifierStatus == "valid"` → TrustedVerifier; else `verifiedStatus == "valid"` → Verified; else None). Handle null `verification` and unknown status strings → None.
- [ ] 2.2 Unit-test the derivation rule (all tiers, null, unknown-string, precedence).
- [ ] 2.3 Render `VerificationBadge` (small, non-interactive) next to the author display name in `:designsystem` `PostCard`, so it shows in feed, thread cluster, and post detail.
- [ ] 2.4 Accessibility: badge `contentDescription` = "Verified account"/"Trusted verifier"; ensure a badge tap falls through to the post-card tap (no sheet in feed). Mind the onClickLabel-vs-contentDescription gotcha.
- [ ] 2.5 Add es (`values-b+es+419`) + pt (`values-pt-rBR`) strings for the badge descriptions.
- [ ] 2.6 Update/add `PostCard` screenshot baselines for Verified + TrustedVerifier authors.

## 3. Profile — mapping + lazy issuer resolution (bd nubecita-vw45.3)

- [ ] 3.1 In `:feature:profile:impl` `AuthorProfileMapper`, derive the profile badge (same rule via `toVerifiedBadge()`) and extract the valid `verification.verifications` (keep `isValid == true` only) as (`issuer` DID, `createdAt`) pairs. Do NOT build `VerifierUi` here — `handle`/`displayName` aren't known until resolution (.3.2); carry the (DID, createdAt) pairs on the profile model for the VM to complete.
- [ ] 3.2 Add a `ProfileRepository`/VM `loadVerifiers` path that resolves the issuer DIDs → handle/displayName via `app.bsky.actor.getProfiles` (`GetProfilesRequest`, batch ≤25) and assembles the `VerifierUi` list (pairing each resolved profile with its verification's `createdAt`; **skip DIDs that don't resolve**). Bound network at the Ktor `HttpTimeout` boundary (NOT `withTimeout` on the injected dispatcher).
- [ ] 3.3 Expose flat sheet sub-state on the profile `UiState` (`verifiersLoading`, `verifiers`, `verifiersError`) and a `UiEvent` for badge tap that flips sheet visibility + triggers `loadVerifiers` (no eager resolution on profile load).
- [ ] 3.4 Repository/VM tests: badge computation; lazy resolution success / empty / error; verify getProfiles is NOT called until the sheet opens.

## 4. Profile — ProfileHero badge + verification sheet (bd nubecita-vw45.4)

- [ ] 4.1 Render the larger `VerificationBadge` next to the display name in `ProfileHero`; tappable when badge != None → emits the badge-tap `UiEvent`.
- [ ] 4.2 Build the verification `ModalBottomSheet`: tier-specific title + explanation copy, verifier list (name/handle + formatted date), loading spinner while resolving, and a non-blocking "details unavailable" state on error.
- [ ] 4.3 Wire the sheet visibility to `UiState` via `LocalMainShellNavState`/effect per MVI conventions; collect once at the screen root.
- [ ] 4.4 Add all new strings (sheet title/copy per tier, "verified by", date/relative formatting, error) in en + `values-b+es+419` + `values-pt-rBR`.
- [ ] 4.5 Accessibility: profile badge exposes its open-details action; sheet content is screen-reader navigable.
- [ ] 4.6 Screenshot baselines: `ProfileHero` badge (both tiers), sheet loaded, sheet loading.
- [ ] 4.7 Run the compose gate (compose-expert Review Mode — new `@Composable`s) and fold Critical findings before PR.

## 5. Verification & rollout

- [ ] 5.1 `:app:assembleDebug` + touched-module `lintDebug` (`:designsystem`, `:core:feed-mapping`, `:feature:profile:impl`) green; spotless + commitlint clean.
- [ ] 5.2 Smoke-test on the bench flavor: seed a verified + trusted-verifier author fixture in the bench feed/profile fakes; confirm badges render and the sheet opens with resolved (fake) verifiers.
- [ ] 5.3 Confirm no missing-translation lint on the touched modules' own `lint` (not just `:app`).
- [ ] 5.4 Close bd `nubecita-vw45.*` tasks + epic after merge; archive this openspec change.
