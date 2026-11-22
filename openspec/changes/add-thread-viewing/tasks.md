# Tasks

Tracked in bd epic `nubecita-hpl8`. Each group is one child issue and one PR in the
stack. Step-level detail: `docs/superpowers/plans/2026-11-21-thread-viewing.md`.

## 1. Thread position pill (`nubecita-hpl8.1`)

- [ ] 1.1 Add `ThreadPositionUi` with the validity factory and the `threadPosition` field on `PostUi` / `QuotedPostUi`; verified by `ThreadPositionUiTest`
- [ ] 1.2 Add the pill component, the inline text slot and strings in en / es-419 / pt-BR; verified by `ThreadPositionTextTest` and `ThreadPositionBadgeScreenshotTest`
- [ ] 1.3 Render the pill in `PostCard`, `FocusPostCard` and the quoted card; verified by new cases in their screenshot tests and unchanged baselines for un-numbered posts

## 2. Numbers in feeds and quotes (`nubecita-hpl8.2`)

- [ ] 2.1 Map the numbering fields from feed entries and quoted records, with the root / parent rule for reply clusters; verified by new cases in `FeedViewPostMapperTest`
- [ ] 2.2 Cover the profile author feed; verified by its mapper unit test

## 3. Numbers in the feed cache (`nubecita-hpl8.3`)

- [ ] 3.1 Add the `feed_item_member` columns and the 12 → 13 auto-migration; verified by `MigrationTest`
- [ ] 3.2 Keep the numbering keys in the entry context blob and write / read them in the projection; verified by feed-cache mapper and projection unit tests
- [ ] 3.3 Add the numbered thread to the bench timeline fixture; verified by the bench unit tests and the bench compile

## 4. Thread V2 repository (`nubecita-hpl8.4`)

- [ ] 4.1 Publish atproto-kotlin with the thread V2 lexicons to mavenLocal and check a real long-thread response; verified by the classes in the local jar and the recorded response facts
- [ ] 4.2 Replace `ThreadItem.Fold` with the read-more rows and add `getOrderedThread` / `getOtherReplies`; verified by `PostThreadV2MapperTest`
- [ ] 4.3 Implement the V2 read with the v1 fallback and the snapshot columns (13 → 14); verified by `DefaultPostThreadRepositoryTest`, `ThreadSnapshotStoreTest` and `MigrationTest`
- [ ] 4.4 Serve the numbered bench thread from the bench fake; verified by `BenchFakePostThreadRepositoryTest`
- [ ] 4.5 Bump `atproto` to the released version; verified by CI passing without `-PuseMavenLocal`

## 5. Ordered post detail (`nubecita-hpl8.5`)

- [ ] 5.1 Compute thread-line flags from the item list; verified by `ThreadConnectorsTest`
- [ ] 5.2 Read through `getOrderedThread`, handle read-more and other-replies events; verified by new cases in `PostDetailViewModelTest`
- [ ] 5.3 Render lines, read-more rows and the pinned opened post; verified by the post detail UI test for the first visible row and new cases in `PostDetailScreenScreenshotTest`
- [ ] 5.4 Device pass on the bench build and on a real long thread; verified by screenshots of the feed pill, the ordered thread, the continuation row and the cached relaunch

## Workflow follow-up

- Land the stack with `gh stack merge --squash --yes`, close `nubecita-hpl8` and its children, then archive this change.
