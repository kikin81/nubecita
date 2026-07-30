## 1. Resolve the ordering conflict (design D5)

- [ ] 1.1 Write a failing test for the disputed case: a `Single` for post P appearing ABOVE a `ReplyCluster` whose root is P. Pin whichever rule is chosen so the two passes can never drift apart.
- [ ] 1.2 Decide whether the new pass subsumes `dedupeClusterContext` entirely (preferred) or runs alongside it, and record the choice in the KDoc.

## 2. Thread-root de-duplication

- [ ] 2.1 Add `List<FeedItemUi>.dedupeByThreadRoot()` to `FeedItemDedupe.kt` as a pure first-wins pass, with the root derivation from the spec (`ReplyCluster` → `root.id`, `SelfThreadChain` → `posts.first().id`, `Single` → `post.id`, tombstones → no root).
- [ ] 2.2 Exempt items whose leaf carries `repostedBy` from the drop while still registering their root.
- [ ] 2.3 Document the newest-first assumption in the KDoc (design D2 / risks) so a future non-chronological feed surface does not inherit the rule silently.
- [ ] 2.4 Unit-test every scenario in the spec: same-page pair, cross-page pair, standalone-reserves-root, repost exemption, tombstones retained.
- [ ] 2.5 Add the negative test that matters most — unrelated items are NEVER dropped — using the real 216-entry shape from the design as the fixture basis.
- [ ] 2.6 Mutation-check the suite: a pass keyed on the leaf instead of the root MUST fail, and dropping the repost exemption MUST fail.

## 3. Wire into the feed

- [ ] 3.1 Apply the pass in `FeedViewModel` alongside the existing dedupe passes in the initial-load, refresh and append reducers, over the accumulated list.
- [ ] 3.2 Verify cross-page behaviour holds via `loadMore`'s `trimmedExisting + newPage`, and that refresh resets the seen set (design D3).
- [ ] 3.3 Confirm the existing duplication classes still work: cluster-vs-standalone and repost-vs-original.

## 4. Suppressed-reply count (design D6)

- [ ] 4.1 Add a suppressed-sibling count to every `FeedItemUi` variant that can survive de-duplication in `:data:models` — `Single` included, not just the cluster variants — defaulting to zero.
- [ ] 4.2 Populate it from `dedupeByThreadRoot`, counting POSTS not items: a dropped `SelfThreadChain` contributes `posts.size`, a dropped `ReplyCluster` contributes one, and a dropped post already rendered elsewhere (e.g. a `Single` that is the survivor's `root` context) contributes zero.
- [ ] 4.2a Test the two counting edge cases directly: a dropped `SelfThreadChain` of three posts, and a dropped `Single` already visible as context.
- [ ] 4.3 Render the affordance on the surviving card, routing to the thread; render nothing when the count is zero.
- [ ] 4.4 Add strings in en, es-419 and pt-BR in the same commit, and verify with `:feature:feed:impl:lintDebug` — `:app` lint does not catch `MissingTranslation`.
- [ ] 4.5 Add screenshot baselines covering zero-count and non-zero-count, and confirm the pair hashes distinctly.

## 5. Verify

- [ ] 5.1 `:app:assembleProductionDebug`, `:app:assembleBenchDebug`, `:feature:feed:impl:lintDebug`, `validateProductionDebugScreenshotTest`.
- [ ] 5.2 Run the compose-expert review if the diff adds `@Composable` lines.
- [ ] 5.3 Device pass on the Pixel Fold against the production account: scroll Following and confirm no repeated thread roots, the affordance appears where siblings were suppressed, and unrelated posts are still present.
- [ ] 5.4 Re-run the temporary wire probe from the design if the device pass is ambiguous, and revert it before committing.
