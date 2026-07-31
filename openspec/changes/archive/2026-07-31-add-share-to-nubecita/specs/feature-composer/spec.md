## MODIFIED Requirements

### Requirement: `:feature:composer:api` exposes exactly one `NavKey`

The system SHALL expose `net.kikin.nubecita.feature.composer.api.ComposerRoute` as the sole `NavKey` for the composer capability. `ComposerRoute` MUST be declared as `data class ComposerRoute(val replyToUri: String? = null, val quotePostUri: String? = null, val mentionHandle: String? = null, val sharedText: String? = null, val sharedImageUri: String? = null) : NavKey`. Every field is typed as `String?`, NOT the lexicon `AtUri` value class — keeps `:feature:composer:api` atproto-runtime-free, mirroring the existing `:feature:postdetail:api`'s `PostDetailRoute(postUri: String)` precedent. Consumers wrap to `AtUri` at the call site to the atproto runtime. The `:api` module MUST NOT contain Composables, ViewModels, repositories, Hilt modules, or any dependency on Compose runtime, atproto SDK record types, or `:feature:composer:impl`. A `null` `replyToUri` MUST mean "compose a new top-level post"; a non-null `replyToUri` MUST mean "compose a reply to that post". No second `NavKey` (e.g. `NewPostRoute`, `ReplyRoute`, `ShareRoute`) SHALL exist for any mode.

`sharedText` and `sharedImageUri` carry inbound Android share-target (`ACTION_SEND`) content and both default to `null`. They are carried on the serialized `NavKey` so navigation restores them across process death. `ComposerViewModel` SHALL seed its `TextFieldState` from `sharedText` and resolve `sharedImageUri` into a `ComposerAttachment`. `sharedText` is lower precedence than `mentionHandle` (an explicit compose-from-profile action). `sharedImageUri` MUST be an app-owned file URI — never a transient `content://` grant — so it survives process death with the serialized route. Existing `replyToUri` / `quotePostUri` / `mentionHandle` behavior and all existing call sites are unchanged, since the new params default to `null`.

#### Scenario: Single NavKey for both modes

- **WHEN** the `:feature:composer:api` source tree is searched for types implementing `androidx.navigation3.runtime.NavKey`
- **THEN** the only match SHALL be `ComposerRoute`

#### Scenario: API module has no UI dependencies

- **WHEN** `:feature:composer:api`'s `build.gradle.kts` is inspected
- **THEN** the `dependencies { }` block SHALL declare `androidx.navigation3.runtime` (the module exporting `NavKey`) and `kotlinx.serialization.json` as `api` deps, and SHALL NOT depend on Compose, Hilt, or `:feature:composer:impl`. The module does not need an `AtUri` dependency because every AT-URI-bearing field is typed `String?`, not the SDK's `AtUri`.

#### Scenario: Existing composer entry points unaffected

- **WHEN** the composer is opened from the feed FAB, a reply, a quote, or a mention (no shared params)
- **THEN** behavior is identical to before this change; `sharedText` / `sharedImageUri` are null and ignored.
