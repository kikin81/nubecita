# Group chat Phase 3c-3a — Join Link Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a group **owner** create, share, enable/disable, and configure their group's single join link, from a dedicated `ManageJoinLink` sub-route off Group details.

**Architecture:** MVI on `MviViewModel<S,E,F>`; a dedicated `@MainShell` adaptiveDialog sub-route reached from an owner-only row in `GroupDetailsScreen`. Repository wraps atproto 9.4.0 `chat.bsky.group.GroupService` (`createJoinLink`/`editJoinLink`/`enableJoinLink`/`disableJoinLink`) plus a raw `ConvoService.getConvo` read for the current link. Optimistic mutation + single in-flight guard + rollback; a fail-closed `JoinRule.Unsupported` state locks editing of links created by newer clients.

**Tech Stack:** Kotlin 2.3, Jetpack Compose + Material 3 Expressive, Hilt assisted injection, Navigation 3, atproto-kotlin 9.4.0 (`AtField` optional-field encoding), JUnit5 + MockK + Turbine, Compose screenshot tests.

**Spec:** `docs/superpowers/specs/2026-06-22-group-chat-phase3c3a-join-link-management-design.md`
**bd:** `nubecita-hwix.8` (epic `nubecita-hwix`). **Branch:** `feat/nubecita-hwix.8-join-link-management`. **Draft PR:** #566.

---

## File structure

**Create:**
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/JoinLinkMapper.kt` — `JoinLinkView.toJoinLinkUi()` (fail-closed `JoinRule` mapping, URL composition) + `JoinRule.toWire()`.
- `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/JoinLinkMapperTest.kt`
- `feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/` — add `ManageJoinLink` to `Chats.kt`.
- `feature/chats/api/src/test/kotlin/net/kikin/nubecita/feature/chats/api/ManageJoinLinkNavKeyTest.kt`
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkContract.kt` — `JoinLinkUi`, `JoinRule`, state/events/effects.
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkViewModel.kt`
- `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkViewModelTest.kt`
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkScreen.kt`
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/JoinLinkCard.kt`
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/CreateJoinLinkContent.kt`
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/JoinRuleSelector.kt`
- `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ui/JoinLinkCardScreenshotTest.kt`
- `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ui/CreateJoinLinkContentScreenshotTest.kt`

**Modify:**
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatRepository.kt` — 5 new methods.
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/DefaultChatRepository.kt` — impls.
- `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/FakeChatRepository.kt`
- `feature/chats/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/chats/impl/FakeChatRepository.kt`
- `feature/chats/impl/src/bench/kotlin/net/kikin/nubecita/feature/chats/impl/data/BenchFakeChatRepository.kt`
- `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/store/ChatsUnreadPollingObserverTest.kt` — inline fake.
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupDetailsContract.kt` — `InviteLinkTapped` event.
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupDetailsScreen.kt` — owner "Invite link" row.
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupDetailsViewModel.kt` — handle `InviteLinkTapped`.
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ChatsNavigationModule.kt` — `entry<ManageJoinLink>`.
- `feature/chats/impl/src/main/res/values/strings.xml`, `values-es/strings.xml`, `values-pt/strings.xml`.

**Decision (deviation from spec wording):** `JoinLinkUi` + `JoinRule` live in the feature `:impl` module (`ManageJoinLinkContract.kt`), mirroring C2's `JoinRequestUi` placement in `GroupJoinRequestsContract.kt`. The model is chats-only; `:data:models` is reserved for cross-module UI models. Fixtures are inline test/preview helpers, as in C2.

---

## Task 1: JoinRule + JoinLinkUi + mapper

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkContract.kt` (model portion only this task)
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/JoinLinkMapper.kt`
- Test: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/JoinLinkMapperTest.kt`

- [ ] **Step 1: Write the model file** (state/events come in Task 4; define the model + enum now so the mapper compiles)

`ManageJoinLinkContract.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

/** How a join link admits new members. Mirrors `chat.bsky.group.defs#joinRule`. */
enum class JoinRule {
    /** Anyone with the link may join / request. Wire value `"anyone"`. */
    Anyone,

    /** Only accounts the owner follows may join / request. Wire value `"followedByOwner"`. */
    FollowedByOwner,

    /**
     * The link carries a rule this build does not understand (a newer client created it).
     * Fail-closed: never sent back to the server and the UI locks editing while it is active.
     */
    Unsupported,
}

/** A group's single join link, projected for the manage-link screen. */
@Immutable
data class JoinLinkUi(
    val code: String,
    val url: String,
    val enabled: Boolean,
    val joinRule: JoinRule,
    val requireApproval: Boolean,
    val createdAt: Instant,
)
```

- [ ] **Step 2: Write the failing mapper test**

`JoinLinkMapperTest.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.group.JoinLinkView
import io.github.kikin81.atproto.runtime.Datetime
import net.kikin.nubecita.feature.chats.impl.JoinRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class JoinLinkMapperTest {
    private fun view(
        code: String = "abc123",
        enabledStatus: String = "enabled",
        joinRule: String = "anyone",
        requireApproval: Boolean = true,
    ) = JoinLinkView(
        code = code,
        createdAt = Datetime("2026-05-13T12:00:00Z"),
        enabledStatus = enabledStatus,
        joinRule = joinRule,
        requireApproval = requireApproval,
    )

    @Test
    fun `maps url enabled and timestamp`() {
        val ui = view(code = "xyz").toJoinLinkUi()
        assertEquals("https://nubecita.app/group/join/xyz", ui.url)
        assertEquals("xyz", ui.code)
        assertEquals(true, ui.enabled)
        assertEquals(Instant.parse("2026-05-13T12:00:00Z"), ui.createdAt)
        assertEquals(true, ui.requireApproval)
    }

    @Test
    fun `disabled status maps to enabled false`() {
        assertEquals(false, view(enabledStatus = "disabled").toJoinLinkUi().enabled)
    }

    @Test
    fun `known join rules map`() {
        assertEquals(JoinRule.Anyone, view(joinRule = "anyone").toJoinLinkUi().joinRule)
        assertEquals(JoinRule.FollowedByOwner, view(joinRule = "followedByOwner").toJoinLinkUi().joinRule)
    }

    @Test
    fun `unknown join rule fails closed to Unsupported`() {
        assertEquals(JoinRule.Unsupported, view(joinRule = "adminsOnly").toJoinLinkUi().joinRule)
    }

    @Test
    fun `toWire round-trips the two supported rules`() {
        assertEquals("anyone", JoinRule.Anyone.toWire())
        assertEquals("followedByOwner", JoinRule.FollowedByOwner.toWire())
    }
}
```

- [ ] **Step 3: Run it — verify it fails**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*JoinLinkMapperTest"`
Expected: FAIL — `toJoinLinkUi` / `toWire` unresolved.

- [ ] **Step 4: Write the mapper**

`JoinLinkMapper.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.group.JoinLinkView
import net.kikin.nubecita.feature.chats.impl.JoinLinkUi
import net.kikin.nubecita.feature.chats.impl.JoinRule
import kotlin.time.Instant

private const val JOIN_LINK_BASE_URL = "https://nubecita.app/group/join"

// chat.bsky.group.defs#linkEnabledStatus sentinel for an active link.
private const val LINK_STATUS_ENABLED = "enabled"

// chat.bsky.group.defs#joinRule known values.
private const val JOIN_RULE_ANYONE = "anyone"
private const val JOIN_RULE_FOLLOWED_BY_OWNER = "followedByOwner"

/**
 * Wire → UI for a group join link.
 *
 * `joinRule` mapping is **fail-closed**: any value other than the two known rules becomes
 * [JoinRule.Unsupported] (never the permissive [JoinRule.Anyone]) so an older build can't silently
 * widen a group when it re-saves a link created by a newer client. [JoinRule.Unsupported] has no
 * wire form (see [toWire]) and the UI locks editing while it is active.
 */
internal fun JoinLinkView.toJoinLinkUi(): JoinLinkUi =
    JoinLinkUi(
        code = code,
        url = "$JOIN_LINK_BASE_URL/$code",
        enabled = enabledStatus == LINK_STATUS_ENABLED,
        joinRule =
            when (joinRule) {
                JOIN_RULE_ANYONE -> JoinRule.Anyone
                JOIN_RULE_FOLLOWED_BY_OWNER -> JoinRule.FollowedByOwner
                else -> JoinRule.Unsupported
            },
        requireApproval = requireApproval,
        createdAt = Instant.parse(createdAt.raw),
    )

/**
 * UI → wire for the editable rules. [JoinRule.Unsupported] has no wire representation and must
 * never reach this function — the UI + ViewModel both gate it out (an unsupported link is read-only).
 */
internal fun JoinRule.toWire(): String =
    when (this) {
        JoinRule.Anyone -> JOIN_RULE_ANYONE
        JoinRule.FollowedByOwner -> JOIN_RULE_FOLLOWED_BY_OWNER
        JoinRule.Unsupported -> error("JoinRule.Unsupported has no wire form")
    }
```

- [ ] **Step 5: Run it — verify it passes**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*JoinLinkMapperTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkContract.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/JoinLinkMapper.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/JoinLinkMapperTest.kt
git commit -m "feat(chats): add join link UI model and fail-closed mapper

Refs: nubecita-hwix.8"
```

---

## Task 2: ManageJoinLink NavKey

**Files:**
- Modify: `feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt`
- Test: `feature/chats/api/src/test/kotlin/net/kikin/nubecita/feature/chats/api/ManageJoinLinkNavKeyTest.kt`

- [ ] **Step 1: Write the failing NavKey test** (mirror `GroupJoinRequestsNavKeyTest`)

`ManageJoinLinkNavKeyTest.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.api

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManageJoinLinkNavKeyTest {
    @Test
    fun `round-trips through json`() {
        val key = ManageJoinLink(convoId = "convo-1")
        val json = Json.encodeToString(ManageJoinLink.serializer(), key)
        assertEquals(key, Json.decodeFromString(ManageJoinLink.serializer(), json))
    }
}
```

- [ ] **Step 2: Run it — verify it fails**

Run: `./gradlew :feature:chats:api:testDebugUnitTest --tests "*ManageJoinLinkNavKeyTest"`
Expected: FAIL — `ManageJoinLink` unresolved.

- [ ] **Step 3: Add the NavKey to `Chats.kt`**

Add (next to `GroupJoinRequests`):
```kotlin
/**
 * Owner-only sub-route to manage the group's single join link (create / share / enable /
 * disable / configure). Pushed from the "Invite link" row in [GroupDetails]. Rendered as an
 * adaptiveDialog (full-screen on Compact, centered dialog on Medium/Expanded).
 */
@Serializable
data class ManageJoinLink(
    val convoId: String,
) : NavKey
```

- [ ] **Step 4: Run it — verify it passes**

Run: `./gradlew :feature:chats:api:testDebugUnitTest --tests "*ManageJoinLinkNavKeyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt \
        feature/chats/api/src/test/kotlin/net/kikin/nubecita/feature/chats/api/ManageJoinLinkNavKeyTest.kt
git commit -m "feat(chats): add the manage-join-link NavKey

Refs: nubecita-hwix.8"
```

---

## Task 3: Repository — read + 4 mutations + all fakes

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatRepository.kt`
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/DefaultChatRepository.kt`
- Modify: `feature/chats/impl/src/test/.../FakeChatRepository.kt`, `.../androidTest/.../FakeChatRepository.kt`, `.../bench/.../data/BenchFakeChatRepository.kt`, `.../test/.../store/ChatsUnreadPollingObserverTest.kt`

**Context — `AtField`:** optional request fields are `AtField<T>`; `import io.github.kikin81.atproto.runtime.present` gives `present(value): AtField.Defined<T>`, and `AtField.Missing` omits the field. `editJoinLink` sends only the changed field.

- [ ] **Step 1: Add the interface methods to `ChatRepository.kt`** (after `rejectJoinRequest`, before the closing `}`)

```kotlin
    /**
     * The group's current join link, or `null` if none exists yet. Read from
     * `chat.bsky.convo.getConvo` → `GroupConvo.joinLink` (there is no standalone get-link XRPC).
     */
    suspend fun getJoinLink(convoId: String): Result<JoinLinkUi?>

    /** Create the group's join link with [joinRule] + [requireApproval]. */
    suspend fun createJoinLink(
        convoId: String,
        joinRule: JoinRule,
        requireApproval: Boolean,
    ): Result<JoinLinkUi>

    /**
     * Edit the existing link. Only the non-null args are sent (each maps to an `AtField`), so a
     * single-field edit never re-sends the other and cannot clobber a rule this build can't map.
     */
    suspend fun editJoinLink(
        convoId: String,
        joinRule: JoinRule? = null,
        requireApproval: Boolean? = null,
    ): Result<JoinLinkUi>

    /** Re-enable the previously disabled link. */
    suspend fun enableJoinLink(convoId: String): Result<JoinLinkUi>

    /** Disable the active link. */
    suspend fun disableJoinLink(convoId: String): Result<JoinLinkUi>
```

Add imports at the top of `ChatRepository.kt`:
```kotlin
import net.kikin.nubecita.feature.chats.impl.JoinLinkUi
import net.kikin.nubecita.feature.chats.impl.JoinRule
```

- [ ] **Step 2: Implement in `DefaultChatRepository.kt`**

Add imports:
```kotlin
import io.github.kikin81.atproto.chat.bsky.convo.GroupConvo
import io.github.kikin81.atproto.chat.bsky.group.CreateJoinLinkRequest
import io.github.kikin81.atproto.chat.bsky.group.DisableJoinLinkRequest
import io.github.kikin81.atproto.chat.bsky.group.EditJoinLinkRequest
import io.github.kikin81.atproto.chat.bsky.group.EnableJoinLinkRequest
import io.github.kikin81.atproto.runtime.AtField
import io.github.kikin81.atproto.runtime.present
import net.kikin.nubecita.feature.chats.impl.JoinLinkUi
import net.kikin.nubecita.feature.chats.impl.JoinRule
import net.kikin.nubecita.feature.chats.impl.data.toJoinLinkUi
import net.kikin.nubecita.feature.chats.impl.data.toWire
```
(`toJoinLinkUi`/`toWire` are same-package; the explicit imports are belt-and-suspenders — drop if the linter flags same-package imports.)

Add the methods inside the repository class (next to the join-request impls):
```kotlin
        override suspend fun getJoinLink(convoId: String): Result<JoinLinkUi?> =
            withContext(dispatcher) {
                runCatching {
                    val convo = ConvoService(xrpcClientProvider.authenticated())
                        .getConvo(GetConvoRequest(convoId = convoId))
                        .convo
                    (convo.kind as? GroupConvo)?.joinLink?.toJoinLinkUi()
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "getJoinLink failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun createJoinLink(
            convoId: String,
            joinRule: JoinRule,
            requireApproval: Boolean,
        ): Result<JoinLinkUi> =
            joinLinkMutation("createJoinLink") { service ->
                service.createJoinLink(
                    CreateJoinLinkRequest(
                        convoId = convoId,
                        joinRule = joinRule.toWire(),
                        requireApproval = present(requireApproval),
                    ),
                ).joinLink
            }

        override suspend fun editJoinLink(
            convoId: String,
            joinRule: JoinRule?,
            requireApproval: Boolean?,
        ): Result<JoinLinkUi> =
            joinLinkMutation("editJoinLink") { service ->
                service.editJoinLink(
                    EditJoinLinkRequest(
                        convoId = convoId,
                        joinRule = joinRule?.let { present(it.toWire()) } ?: AtField.Missing,
                        requireApproval = requireApproval?.let { present(it) } ?: AtField.Missing,
                    ),
                ).joinLink
            }

        override suspend fun enableJoinLink(convoId: String): Result<JoinLinkUi> =
            joinLinkMutation("enableJoinLink") { service ->
                service.enableJoinLink(EnableJoinLinkRequest(convoId = convoId)).joinLink
            }

        override suspend fun disableJoinLink(convoId: String): Result<JoinLinkUi> =
            joinLinkMutation("disableJoinLink") { service ->
                service.disableJoinLink(DisableJoinLinkRequest(convoId = convoId)).joinLink
            }

        // Parallel to [groupMutation] but returns the mapped JoinLinkUi each link op yields.
        private suspend inline fun joinLinkMutation(
            op: String,
            crossinline block: suspend (GroupService) -> io.github.kikin81.atproto.chat.bsky.group.JoinLinkView,
        ): Result<JoinLinkUi> =
            withContext(dispatcher) {
                runCatching {
                    block(GroupService(xrpcClientProvider.authenticated())).toJoinLinkUi()
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "%s failed: %s", op, throwable.javaClass.name)
                }
            }
```

- [ ] **Step 3: Update the JVM test fake** (`src/test/.../FakeChatRepository.kt`)

Add settable results + call captures + a gate (mirrors `approveJoinRequestGate`). Place near the other group fields:
```kotlin
    var getJoinLinkResult: Result<JoinLinkUi?> = Result.success(null)
    var createJoinLinkResult: Result<JoinLinkUi> = Result.success(DEFAULT_JOIN_LINK)
    var editJoinLinkResult: Result<JoinLinkUi> = Result.success(DEFAULT_JOIN_LINK)
    var enableJoinLinkResult: Result<JoinLinkUi> = Result.success(DEFAULT_JOIN_LINK.copy(enabled = true))
    var disableJoinLinkResult: Result<JoinLinkUi> = Result.success(DEFAULT_JOIN_LINK.copy(enabled = false))
    val editJoinLinkCalls = mutableListOf<Triple<String, JoinRule?, Boolean?>>()
    val enableJoinLinkCalls = mutableListOf<String>()
    val disableJoinLinkCalls = mutableListOf<String>()

    /** Optional gate: when set, the four link mutations suspend on it before returning. */
    var joinLinkMutationGate: CompletableDeferred<Unit>? = null

    override suspend fun getJoinLink(convoId: String): Result<JoinLinkUi?> = getJoinLinkResult

    override suspend fun createJoinLink(
        convoId: String,
        joinRule: JoinRule,
        requireApproval: Boolean,
    ): Result<JoinLinkUi> {
        joinLinkMutationGate?.await()
        return createJoinLinkResult
    }

    override suspend fun editJoinLink(
        convoId: String,
        joinRule: JoinRule?,
        requireApproval: Boolean?,
    ): Result<JoinLinkUi> {
        editJoinLinkCalls += Triple(convoId, joinRule, requireApproval)
        joinLinkMutationGate?.await()
        return editJoinLinkResult
    }

    override suspend fun enableJoinLink(convoId: String): Result<JoinLinkUi> {
        enableJoinLinkCalls += convoId
        joinLinkMutationGate?.await()
        return enableJoinLinkResult
    }

    override suspend fun disableJoinLink(convoId: String): Result<JoinLinkUi> {
        disableJoinLinkCalls += convoId
        joinLinkMutationGate?.await()
        return disableJoinLinkResult
    }
```
Add the fixture constant in the file's `companion object` (or top-level `private val`):
```kotlin
    // top-level private val in FakeChatRepository.kt (mirrors DEFAULT_SENT_MESSAGE)
    private val DEFAULT_JOIN_LINK = JoinLinkUi(
        code = "code-1",
        url = "https://nubecita.app/group/join/code-1",
        enabled = true,
        joinRule = JoinRule.Anyone,
        requireApproval = true,
        createdAt = kotlin.time.Instant.parse("2026-05-13T12:00:00Z"),
    )
```
Add imports: `import net.kikin.nubecita.feature.chats.impl.JoinLinkUi`, `import net.kikin.nubecita.feature.chats.impl.JoinRule`.

- [ ] **Step 4: Update the androidTest fake, the bench fake, and the inline polling-test fake**

For each of `src/androidTest/.../FakeChatRepository.kt`, `src/bench/.../data/BenchFakeChatRepository.kt`, and the inline `object : ChatRepository` in `src/test/.../store/ChatsUnreadPollingObserverTest.kt`, add the five overrides returning inert success (they aren't exercised there):
```kotlin
    override suspend fun getJoinLink(convoId: String): Result<JoinLinkUi?> = Result.success(null)

    override suspend fun createJoinLink(
        convoId: String,
        joinRule: JoinRule,
        requireApproval: Boolean,
    ): Result<JoinLinkUi> = Result.success(error("not used"))

    override suspend fun editJoinLink(
        convoId: String,
        joinRule: JoinRule?,
        requireApproval: Boolean?,
    ): Result<JoinLinkUi> = Result.success(error("not used"))

    override suspend fun enableJoinLink(convoId: String): Result<JoinLinkUi> = Result.success(error("not used"))

    override suspend fun disableJoinLink(convoId: String): Result<JoinLinkUi> = Result.success(error("not used"))
```
For the bench fake, prefer returning a real `JoinLinkUi(...)` (bench runs offline UI) rather than `error(...)`; use the same `DEFAULT_JOIN_LINK` shape. Add the `JoinLinkUi`/`JoinRule` imports to each file.

- [ ] **Step 5: Compile everything (interface change touches all implementors)**

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:compileBenchDebugKotlin :feature:chats:impl:compileDebugUnitTestKotlin :feature:chats:impl:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL — no "class … must override" errors. (Per the verify-all-implementors rule.)

- [ ] **Step 6: Run the existing chats unit suite (no regressions)**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatRepository.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/DefaultChatRepository.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/FakeChatRepository.kt \
        feature/chats/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/chats/impl/FakeChatRepository.kt \
        feature/chats/impl/src/bench/kotlin/net/kikin/nubecita/feature/chats/impl/data/BenchFakeChatRepository.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/store/ChatsUnreadPollingObserverTest.kt
git commit -m "feat(chats): repository join-link read, create, edit, enable, disable

Refs: nubecita-hwix.8"
```

---

## Task 4: ManageJoinLink contract (state/events/effects)

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkContract.kt`

- [ ] **Step 1: Append the MVI contract** (the model + enum from Task 1 stay at the top)

```kotlin
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/** Mutually-exclusive load lifecycle for the manage-link screen. */
sealed interface ManageJoinLinkStatus {
    data object Loading : ManageJoinLinkStatus

    /** Settled load. [link] is `null` when the group has no join link yet (show the create UI). */
    data class Loaded(
        val link: JoinLinkUi?,
    ) : ManageJoinLinkStatus

    data class Error(
        val error: ChatError,
    ) : ManageJoinLinkStatus
}

@Immutable
data class ManageJoinLinkViewState(
    val status: ManageJoinLinkStatus = ManageJoinLinkStatus.Loading,
    val mutationInFlight: Boolean = false,
) : UiState

sealed interface ManageJoinLinkEvent : UiEvent {
    data object Retry : ManageJoinLinkEvent

    data class CreateTapped(
        val joinRule: JoinRule,
        val requireApproval: Boolean,
    ) : ManageJoinLinkEvent

    data class JoinRuleChanged(
        val joinRule: JoinRule,
    ) : ManageJoinLinkEvent

    data class RequireApprovalChanged(
        val requireApproval: Boolean,
    ) : ManageJoinLinkEvent

    data object EnableTapped : ManageJoinLinkEvent

    data object DisableTapped : ManageJoinLinkEvent
}

sealed interface ManageJoinLinkEffect : UiEffect {
    data class ShowError(
        val error: ChatError,
    ) : ManageJoinLinkEffect
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkContract.kt
git commit -m "feat(chats): manage-join-link contract

Refs: nubecita-hwix.8"
```

---

## Task 5: ManageJoinLinkViewModel + tests

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkViewModel.kt`
- Test: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkViewModelTest.kt`

- [ ] **Step 1: Write the failing VM test** (mirror `GroupJoinRequestsViewModelTest`: `@ExtendWith(MainDispatcherExtension::class)`, Turbine on `effects`, fake repo)

`ManageJoinLinkViewModelTest.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.api.ManageJoinLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Instant

@ExtendWith(MainDispatcherExtension::class)
class ManageJoinLinkViewModelTest {
    private val route = ManageJoinLink(convoId = "convo-1")

    private fun link(
        enabled: Boolean = true,
        joinRule: JoinRule = JoinRule.Anyone,
        requireApproval: Boolean = true,
    ) = JoinLinkUi(
        code = "code-1",
        url = "https://nubecita.app/group/join/code-1",
        enabled = enabled,
        joinRule = joinRule,
        requireApproval = requireApproval,
        createdAt = Instant.parse("2026-05-13T12:00:00Z"),
    )

    private fun vm(fake: FakeChatRepository) = ManageJoinLinkViewModel(route, fake)

    @Test
    fun `load surfaces existing link`() = runTest {
        val fake = FakeChatRepository().apply { getJoinLinkResult = Result.success(link()) }
        val state = vm(fake).uiState.value
        assertEquals(ManageJoinLinkStatus.Loaded(link()), state.status)
    }

    @Test
    fun `load with no link shows create state`() = runTest {
        val fake = FakeChatRepository().apply { getJoinLinkResult = Result.success(null) }
        assertEquals(ManageJoinLinkStatus.Loaded(null), vm(fake).uiState.value.status)
    }

    @Test
    fun `load failure is sticky error and retry reloads`() = runTest {
        val fake = FakeChatRepository().apply { getJoinLinkResult = Result.failure(RuntimeException("x")) }
        val model = vm(fake)
        assertTrue(model.uiState.value.status is ManageJoinLinkStatus.Error)
        fake.getJoinLinkResult = Result.success(link())
        model.handleEvent(ManageJoinLinkEvent.Retry)
        assertEquals(ManageJoinLinkStatus.Loaded(link()), model.uiState.value.status)
    }

    @Test
    fun `create success moves to loaded`() = runTest {
        val fake = FakeChatRepository().apply {
            getJoinLinkResult = Result.success(null)
            createJoinLinkResult = Result.success(link())
        }
        val model = vm(fake)
        model.handleEvent(ManageJoinLinkEvent.CreateTapped(JoinRule.Anyone, requireApproval = true))
        assertEquals(ManageJoinLinkStatus.Loaded(link()), model.uiState.value.status)
    }

    @Test
    fun `create failure stays in create state and emits error`() = runTest {
        val fake = FakeChatRepository().apply {
            getJoinLinkResult = Result.success(null)
            createJoinLinkResult = Result.failure(RuntimeException("x"))
        }
        val model = vm(fake)
        model.effects.test {
            model.handleEvent(ManageJoinLinkEvent.CreateTapped(JoinRule.Anyone, requireApproval = true))
            assertTrue(awaitItem() is ManageJoinLinkEffect.ShowError)
        }
        assertEquals(ManageJoinLinkStatus.Loaded(null), model.uiState.value.status)
    }

    @Test
    fun `disable optimistically flips then reconciles`() = runTest {
        val fake = FakeChatRepository().apply {
            getJoinLinkResult = Result.success(link(enabled = true))
            disableJoinLinkResult = Result.success(link(enabled = false))
        }
        val model = vm(fake)
        model.handleEvent(ManageJoinLinkEvent.DisableTapped)
        assertEquals(listOf("convo-1"), fake.disableJoinLinkCalls)
        assertEquals(false, (model.uiState.value.status as ManageJoinLinkStatus.Loaded).link?.enabled)
    }

    @Test
    fun `disable failure rolls back to enabled`() = runTest {
        val fake = FakeChatRepository().apply {
            getJoinLinkResult = Result.success(link(enabled = true))
            disableJoinLinkResult = Result.failure(RuntimeException("x"))
        }
        val model = vm(fake)
        model.handleEvent(ManageJoinLinkEvent.DisableTapped)
        assertEquals(true, (model.uiState.value.status as ManageJoinLinkStatus.Loaded).link?.enabled)
    }

    @Test
    fun `requireApproval edit sends only that field`() = runTest {
        val fake = FakeChatRepository().apply {
            getJoinLinkResult = Result.success(link(requireApproval = true))
            editJoinLinkResult = Result.success(link(requireApproval = false))
        }
        val model = vm(fake)
        model.handleEvent(ManageJoinLinkEvent.RequireApprovalChanged(requireApproval = false))
        assertEquals(Triple("convo-1", null, false), fake.editJoinLinkCalls.single())
    }

    @Test
    fun `joinRule edit sends only that field`() = runTest {
        val fake = FakeChatRepository().apply {
            getJoinLinkResult = Result.success(link(joinRule = JoinRule.Anyone))
            editJoinLinkResult = Result.success(link(joinRule = JoinRule.FollowedByOwner))
        }
        val model = vm(fake)
        model.handleEvent(ManageJoinLinkEvent.JoinRuleChanged(JoinRule.FollowedByOwner))
        assertEquals(Triple("convo-1", JoinRule.FollowedByOwner, null), fake.editJoinLinkCalls.single())
    }

    @Test
    fun `in-flight guard drops a second concurrent mutation`() = runTest {
        val fake = FakeChatRepository().apply {
            getJoinLinkResult = Result.success(link(enabled = true))
            disableJoinLinkResult = Result.success(link(enabled = false))
            joinLinkMutationGate = CompletableDeferred()
        }
        val model = vm(fake)
        model.handleEvent(ManageJoinLinkEvent.DisableTapped) // suspends on the gate
        model.handleEvent(ManageJoinLinkEvent.EnableTapped) // dropped by the guard
        fake.joinLinkMutationGate!!.complete(Unit)
        assertEquals(listOf("convo-1"), fake.disableJoinLinkCalls)
        assertTrue(fake.enableJoinLinkCalls.isEmpty())
    }

    @Test
    fun `unsupported link drops all setting events`() = runTest {
        val fake = FakeChatRepository().apply {
            getJoinLinkResult = Result.success(link(joinRule = JoinRule.Unsupported))
        }
        val model = vm(fake)
        model.handleEvent(ManageJoinLinkEvent.RequireApprovalChanged(false))
        model.handleEvent(ManageJoinLinkEvent.JoinRuleChanged(JoinRule.Anyone))
        model.handleEvent(ManageJoinLinkEvent.DisableTapped)
        assertTrue(fake.editJoinLinkCalls.isEmpty())
        assertTrue(fake.disableJoinLinkCalls.isEmpty())
        assertEquals(JoinRule.Unsupported, (model.uiState.value.status as ManageJoinLinkStatus.Loaded).link?.joinRule)
    }
}
```

- [ ] **Step 2: Run it — verify it fails**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*ManageJoinLinkViewModelTest"`
Expected: FAIL — `ManageJoinLinkViewModel` unresolved.

- [ ] **Step 3: Write the ViewModel**

`ManageJoinLinkViewModel.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.api.ManageJoinLink
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toMemberMgmtError

/**
 * MVI presenter for the owner's manage-join-link screen.
 *
 * Loads the group's single link via [ChatRepository.getJoinLink] on init. Create / enable / disable /
 * edit are optimistic: state updates immediately, the call runs, and a failure rolls back + emits
 * [ManageJoinLinkEffect.ShowError]. A single [ManageJoinLinkViewState.mutationInFlight] flag guards
 * against concurrent mutations (the link is one object). A [JoinRule.Unsupported] link is read-only:
 * every setting event is dropped, the backstop for the screen's disabled controls.
 */
@HiltViewModel(assistedFactory = ManageJoinLinkViewModel.Factory::class)
class ManageJoinLinkViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: ManageJoinLink,
        private val repository: ChatRepository,
    ) : MviViewModel<ManageJoinLinkViewState, ManageJoinLinkEvent, ManageJoinLinkEffect>(
            ManageJoinLinkViewState(),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: ManageJoinLink): ManageJoinLinkViewModel
        }

        private val convoId: String = route.convoId

        init {
            load()
        }

        override fun handleEvent(event: ManageJoinLinkEvent) {
            when (event) {
                ManageJoinLinkEvent.Retry -> load()
                is ManageJoinLinkEvent.CreateTapped -> create(event.joinRule, event.requireApproval)
                is ManageJoinLinkEvent.JoinRuleChanged -> editRule(event.joinRule)
                is ManageJoinLinkEvent.RequireApprovalChanged -> editApproval(event.requireApproval)
                ManageJoinLinkEvent.EnableTapped -> setEnabled(enable = true)
                ManageJoinLinkEvent.DisableTapped -> setEnabled(enable = false)
            }
        }

        private fun load() {
            setState { copy(status = ManageJoinLinkStatus.Loading) }
            viewModelScope.launch {
                repository.getJoinLink(convoId)
                    .onSuccess { setState { copy(status = ManageJoinLinkStatus.Loaded(it)) } }
                    .onFailure { setState { copy(status = ManageJoinLinkStatus.Error(it.toMemberMgmtError())) } }
            }
        }

        private fun create(
            joinRule: JoinRule,
            requireApproval: Boolean,
        ) {
            if (uiState.value.mutationInFlight) return
            setState { copy(mutationInFlight = true) }
            viewModelScope.launch {
                try {
                    repository.createJoinLink(convoId, joinRule, requireApproval)
                        .onSuccess { setState { copy(status = ManageJoinLinkStatus.Loaded(it)) } }
                        .onFailure { sendEffect(ManageJoinLinkEffect.ShowError(it.toMemberMgmtError())) }
                } finally {
                    setState { copy(mutationInFlight = false) }
                }
            }
        }

        private fun editRule(joinRule: JoinRule) {
            val current = editableLink() ?: return
            applyOptimistic(current, current.copy(joinRule = joinRule)) {
                repository.editJoinLink(convoId, joinRule = joinRule)
            }
        }

        private fun editApproval(requireApproval: Boolean) {
            val current = editableLink() ?: return
            applyOptimistic(current, current.copy(requireApproval = requireApproval)) {
                repository.editJoinLink(convoId, requireApproval = requireApproval)
            }
        }

        private fun setEnabled(enable: Boolean) {
            val current = editableLink() ?: return
            applyOptimistic(current, current.copy(enabled = enable)) {
                if (enable) repository.enableJoinLink(convoId) else repository.disableJoinLink(convoId)
            }
        }

        /**
         * The current link if it exists AND is editable. Returns `null` (no-op) when there is no
         * link or its rule is [JoinRule.Unsupported] — the read-only backstop.
         */
        private fun editableLink(): JoinLinkUi? {
            if (uiState.value.mutationInFlight) return null
            val link = (uiState.value.status as? ManageJoinLinkStatus.Loaded)?.link ?: return null
            return link.takeIf { it.joinRule != JoinRule.Unsupported }
        }

        private inline fun applyOptimistic(
            prior: JoinLinkUi,
            optimistic: JoinLinkUi,
            crossinline call: suspend () -> Result<JoinLinkUi>,
        ) {
            setState { copy(status = ManageJoinLinkStatus.Loaded(optimistic), mutationInFlight = true) }
            viewModelScope.launch {
                try {
                    call()
                        .onSuccess { setState { copy(status = ManageJoinLinkStatus.Loaded(it)) } }
                        .onFailure {
                            setState { copy(status = ManageJoinLinkStatus.Loaded(prior)) }
                            sendEffect(ManageJoinLinkEffect.ShowError(it.toMemberMgmtError()))
                        }
                } finally {
                    setState { copy(mutationInFlight = false) }
                }
            }
        }
    }
```

- [ ] **Step 4: Run it — verify it passes**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*ManageJoinLinkViewModelTest"`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkViewModel.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkViewModelTest.kt
git commit -m "feat(chats): manage-join-link view model

Refs: nubecita-hwix.8"
```

---

## Task 6: Screen + components

**Files:**
- Create: `ui/JoinRuleSelector.kt`, `ui/CreateJoinLinkContent.kt`, `ui/JoinLinkCard.kt`, `ManageJoinLinkScreen.kt`

**Context — controls & icons:** Use `SingleChoiceSegmentedButtonRow` + `SegmentedButton` for the two-option `joinRule` (precedent: `designsystem/.../tabs/ProfilePillTabs.kt`). Use `Switch` for `requireApproval`. Empty-state tonal glyph = existing `NubecitaIconName.PersonAdd` (no new font glyph). Share = `NubecitaIconName.IosShare`; Copy = a text-label button + a "Link copied" snackbar (no copy glyph). Standalone loader = `NubecitaWavyProgressIndicator`; the in-button create spinner uses a raw `CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)` with the `// nubecita-allow-raw-progress: in-button micro-spinner` marker.

- [ ] **Step 1: Write `JoinRuleSelector.kt`** (shared by create + card)

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.kikin.nubecita.feature.chats.impl.JoinRule
import net.kikin.nubecita.feature.chats.impl.R

/**
 * Two-option segmented control for the editable [JoinRule]s (Anyone / Followed by owner).
 * [JoinRule.Unsupported] is never passed here — callers disable the control for unsupported links.
 */
@Composable
internal fun JoinRuleSelector(
    selected: JoinRule,
    enabled: Boolean,
    onSelected: (JoinRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(JoinRule.Anyone, JoinRule.FollowedByOwner)
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, rule ->
            SegmentedButton(
                selected = selected == rule,
                onClick = { onSelected(rule) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    text =
                        stringResource(
                            if (rule == JoinRule.Anyone) {
                                R.string.join_link_rule_anyone
                            } else {
                                R.string.join_link_rule_followed
                            },
                        ),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Write `CreateJoinLinkContent.kt`** (the `Loaded(null)` empty-state)

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.JoinRule
import net.kikin.nubecita.feature.chats.impl.R

/**
 * Create-state body: a large tonal glyph over title/explainer, the two settings controls (seeded
 * with permissive-but-vetted defaults Anyone + requireApproval = true), and a Create button that
 * shows an in-button spinner while [creating].
 */
@Composable
internal fun CreateJoinLinkContent(
    creating: Boolean,
    onCreate: (JoinRule, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var joinRule by rememberSaveable { mutableStateOf(JoinRule.Anyone) }
    var requireApproval by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NubecitaIcon(
            name = NubecitaIconName.PersonAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(20.dp)
                    .size(40.dp),
        )
        Text(
            text = stringResource(R.string.join_link_create_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.join_link_create_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        JoinRuleSelector(selected = joinRule, enabled = !creating, onSelected = { joinRule = it })
        RequireApprovalRow(
            checked = requireApproval,
            enabled = !creating,
            onCheckedChange = { requireApproval = it },
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { onCreate(joinRule, requireApproval) },
            enabled = !creating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (creating) {
                // nubecita-allow-raw-progress: in-button micro-spinner with a tuned strokeWidth
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.join_link_create_button))
            }
        }
    }
}

/** Shared label + Switch row for `requireApproval`, with the C2-queue helper text. */
@Composable
internal fun RequireApprovalRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.join_link_require_approval),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        Text(
            text = stringResource(R.string.join_link_require_approval_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.wrapContentSize(Alignment.CenterStart),
        )
    }
}
```

- [ ] **Step 3: Write `JoinLinkCard.kt`** (the `Loaded(link)` body)

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.JoinLinkUi
import net.kikin.nubecita.feature.chats.impl.JoinRule
import net.kikin.nubecita.feature.chats.impl.R

/**
 * The existing-link body. A disabled link is de-emphasized (alpha 0.6, onSurfaceVariant URL,
 * no copy/share); an [JoinRule.Unsupported] link shows the update banner and locks every control.
 * The Enable toggle stays fully opaque/enabled so re-enabling is always reachable.
 */
@Composable
internal fun JoinLinkCard(
    link: JoinLinkUi,
    mutationInFlight: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onJoinRuleChange: (JoinRule) -> Unit,
    onRequireApprovalChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unsupported = link.joinRule == JoinRule.Unsupported
    val controlsEnabled = !mutationInFlight && !unsupported
    val contentAlpha = if (link.enabled) 1f else 0.6f

    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (unsupported) {
            UnsupportedBanner()
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(16.dp).alpha(contentAlpha), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (link.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopy, enabled = link.enabled) {
                        Text(stringResource(R.string.join_link_copy))
                    }
                    FilledTonalButton(onClick = onShare, enabled = link.enabled) {
                        NubecitaIcon(name = NubecitaIconName.IosShare, contentDescription = null)
                        Text(stringResource(R.string.join_link_share), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        // Enable/Disable — stays interactive even when the link is disabled (but not when unsupported).
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.join_link_enabled_toggle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = link.enabled,
                onCheckedChange = { onSetEnabled(it) },
                enabled = !mutationInFlight && !unsupported,
            )
        }
        HorizontalDivider()
        Text(stringResource(R.string.join_link_who_can_join), style = MaterialTheme.typography.titleSmall)
        JoinRuleSelector(
            selected = if (unsupported) JoinRule.Anyone else link.joinRule,
            enabled = controlsEnabled,
            onSelected = onJoinRuleChange,
        )
        RequireApprovalRow(
            checked = link.requireApproval,
            enabled = controlsEnabled,
            onCheckedChange = onRequireApprovalChange,
        )
    }
}

@Composable
private fun UnsupportedBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NubecitaIcon(name = NubecitaIconName.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(R.string.join_link_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
```

- [ ] **Step 4: Write `ManageJoinLinkScreen.kt`** (stateful + stateless content; mirror `GroupJoinRequestsScreen`'s effect collector)

```kotlin
package net.kikin.nubecita.feature.chats.impl

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.ui.CreateJoinLinkContent
import net.kikin.nubecita.feature.chats.impl.ui.JoinLinkCard

@Composable
internal fun ManageJoinLinkScreen(
    viewModel: ManageJoinLinkViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val fullMsg = stringResource(R.string.add_members_error_full)
    val followMsg = stringResource(R.string.add_members_error_follow_required)
    val permMsg = stringResource(R.string.add_members_error_permission)
    val genericMsg = stringResource(R.string.add_members_error_generic)
    val copiedMsg = stringResource(R.string.join_link_copied)

    LaunchedEffect(Unit) {
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is ManageJoinLinkEffect.ShowError -> {
                    val msg =
                        when (effect.error) {
                            ChatError.GroupFull -> fullMsg
                            ChatError.FollowRequiredToAdd -> followMsg
                            ChatError.InsufficientPermission -> permMsg
                            else -> genericMsg
                        }
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            }
        }
    }

    ManageJoinLinkScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onClose = onBack,
        onCopy = { url ->
            scope.launch {
                clipboard.setClipEntry(
                    androidx.compose.ui.platform.ClipEntry(ClipData.newPlainText("invite", url)),
                )
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(copiedMsg)
            }
        },
        onShare = { url ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            context.startActivity(Intent.createChooser(send, null))
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageJoinLinkScreenContent(
    state: ManageJoinLinkViewState,
    onEvent: (ManageJoinLinkEvent) -> Unit,
    onClose: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.join_link_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription = stringResource(R.string.join_link_close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).align(Alignment.TopCenter)) {
                when (val status = state.status) {
                    ManageJoinLinkStatus.Loading ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            NubecitaWavyProgressIndicator()
                        }

                    is ManageJoinLinkStatus.Error ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { onEvent(ManageJoinLinkEvent.Retry) }) {
                                Text(stringResource(R.string.new_chat_retry))
                            }
                        }

                    is ManageJoinLinkStatus.Loaded ->
                        when (val link = status.link) {
                            null ->
                                CreateJoinLinkContent(
                                    creating = state.mutationInFlight,
                                    onCreate = { rule, approval ->
                                        onEvent(ManageJoinLinkEvent.CreateTapped(rule, approval))
                                    },
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                )
                            else ->
                                JoinLinkCard(
                                    link = link,
                                    mutationInFlight = state.mutationInFlight,
                                    onCopy = { onCopy(link.url) },
                                    onShare = { onShare(link.url) },
                                    onSetEnabled = { enable ->
                                        onEvent(
                                            if (enable) ManageJoinLinkEvent.EnableTapped else ManageJoinLinkEvent.DisableTapped,
                                        )
                                    },
                                    onJoinRuleChange = { onEvent(ManageJoinLinkEvent.JoinRuleChanged(it)) },
                                    onRequireApprovalChange = { onEvent(ManageJoinLinkEvent.RequireApprovalChanged(it)) },
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                )
                        }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Compile** (the clipboard API names vary by Compose version — if `LocalClipboard`/`ClipEntry` don't resolve, fall back to `LocalClipboardManager.current.setText(AnnotatedString(url))`, which is stable across versions)

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin`
Expected: BUILD SUCCESSFUL. If clipboard symbols are unresolved, switch to `LocalClipboardManager` + `AnnotatedString` and recompile.

- [ ] **Step 6: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ManageJoinLinkScreen.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/JoinRuleSelector.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/CreateJoinLinkContent.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/JoinLinkCard.kt
git commit -m "feat(chats): manage-join-link screen and components

Refs: nubecita-hwix.8"
```

---

## Task 7: GroupDetails owner "Invite link" row

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupDetailsContract.kt`
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupDetailsViewModel.kt`
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupDetailsScreen.kt`

- [ ] **Step 1: Write the failing VM test** (append to `GroupDetailsViewModelTest`)

```kotlin
    @Test
    fun `invite link tapped navigates to manage join link`() = runTest {
        val model = /* existing GroupDetailsViewModel test factory */ viewModelWithLoadedGroup()
        model.effects.test {
            model.handleEvent(GroupDetailsEvent.InviteLinkTapped)
            assertEquals(
                GroupDetailsEffect.NavigateTo(ManageJoinLink(convoId = "convo-1")),
                awaitItem(),
            )
        }
    }
```
(Use the same construction helper the existing `JoinRequestsTapped` test uses; import `net.kikin.nubecita.feature.chats.api.ManageJoinLink`.)

- [ ] **Step 2: Run it — verify it fails**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*GroupDetailsViewModelTest"`
Expected: FAIL — `InviteLinkTapped` unresolved.

- [ ] **Step 3: Add the event + handler**

In `GroupDetailsContract.kt`, next to `JoinRequestsTapped`:
```kotlin
    data object InviteLinkTapped : GroupDetailsEvent
```
In `GroupDetailsViewModel.kt`, next to the `JoinRequestsTapped` branch:
```kotlin
            GroupDetailsEvent.InviteLinkTapped ->
                sendEffect(GroupDetailsEffect.NavigateTo(ManageJoinLink(convoId)))
```
Add `import net.kikin.nubecita.feature.chats.api.ManageJoinLink` to the VM.

- [ ] **Step 4: Add the owner-only row in `GroupDetailsScreen.kt`** (directly after the `"joinRequests"` `item {}` block, still inside `if (state.viewerRole == GroupRole.Owner)`)

```kotlin
            item(contentType = "inviteLink") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onEvent(GroupDetailsEvent.InviteLinkTapped) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NubecitaIcon(name = NubecitaIconName.IosShare, contentDescription = null)
                    Text(
                        text = stringResource(R.string.group_details_invite_link),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                    NubecitaIcon(
                        name = NubecitaIconName.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
```

- [ ] **Step 5: Run the test — verify it passes**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*GroupDetailsViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupDetailsContract.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupDetailsViewModel.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupDetailsScreen.kt
git commit -m "feat(chats): group-details invite-link entry row

Refs: nubecita-hwix.8"
```

---

## Task 8: Navigation wiring

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ChatsNavigationModule.kt`

- [ ] **Step 1: Register the entry** (after the `entry<GroupJoinRequests>` block)

```kotlin
            // adaptiveDialog(): full-screen on Compact, centered Dialog on Medium / Expanded.
            // Assisted-injected — ManageJoinLinkViewModel takes the ManageJoinLink route via its
            // factory. No result-passing: the link lives on its own screen and GroupDetails does
            // not display link state, so there's nothing to refresh on the way back.
            entry<ManageJoinLink>(metadata = adaptiveDialog()) { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<ManageJoinLinkViewModel, ManageJoinLinkViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                ManageJoinLinkScreen(
                    viewModel = viewModel,
                    onBack = { navState.removeLast() },
                )
            }
```
Add `import net.kikin.nubecita.feature.chats.api.ManageJoinLink`.

- [ ] **Step 2: Build the app to verify DI graph + nav compile** (assisted factory multibinding)

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ChatsNavigationModule.kt
git commit -m "feat(chats): wire manage-join-link route

Refs: nubecita-hwix.8"
```

---

## Task 9: Strings (en/es/pt)

**Files:**
- Modify: `feature/chats/impl/src/main/res/values/strings.xml`, `values-es/strings.xml`, `values-pt/strings.xml`

- [ ] **Step 1: Add to `values/strings.xml`**

```xml
    <string name="group_details_invite_link">Invite link</string>
    <string name="join_link_title">Invite link</string>
    <string name="join_link_close">Close</string>
    <string name="join_link_create_title">Create an invite link</string>
    <string name="join_link_create_body">Share a link so people can join this group.</string>
    <string name="join_link_create_button">Create link</string>
    <string name="join_link_copy">Copy</string>
    <string name="join_link_share">Share</string>
    <string name="join_link_copied">Link copied</string>
    <string name="join_link_enabled_toggle">Link active</string>
    <string name="join_link_who_can_join">Who can join</string>
    <string name="join_link_rule_anyone">Anyone</string>
    <string name="join_link_rule_followed">People you follow</string>
    <string name="join_link_require_approval">Require approval</string>
    <string name="join_link_require_approval_help">When on, people who tap the link appear in Join requests for you to approve.</string>
    <string name="join_link_unsupported">This link uses settings from a newer version of Nubecita. Update to edit.</string>
```

- [ ] **Step 2: Add Spanish to `values-es/strings.xml`**

```xml
    <string name="group_details_invite_link">Enlace de invitación</string>
    <string name="join_link_title">Enlace de invitación</string>
    <string name="join_link_close">Cerrar</string>
    <string name="join_link_create_title">Crear un enlace de invitación</string>
    <string name="join_link_create_body">Comparte un enlace para que otras personas se unan a este grupo.</string>
    <string name="join_link_create_button">Crear enlace</string>
    <string name="join_link_copy">Copiar</string>
    <string name="join_link_share">Compartir</string>
    <string name="join_link_copied">Enlace copiado</string>
    <string name="join_link_enabled_toggle">Enlace activo</string>
    <string name="join_link_who_can_join">Quién puede unirse</string>
    <string name="join_link_rule_anyone">Cualquiera</string>
    <string name="join_link_rule_followed">Personas que sigues</string>
    <string name="join_link_require_approval">Requerir aprobación</string>
    <string name="join_link_require_approval_help">Si está activado, quienes toquen el enlace aparecerán en Solicitudes para que las apruebes.</string>
    <string name="join_link_unsupported">Este enlace usa ajustes de una versión más reciente de Nubecita. Actualiza para editar.</string>
```

- [ ] **Step 3: Add Portuguese to `values-pt/strings.xml`**

```xml
    <string name="group_details_invite_link">Link de convite</string>
    <string name="join_link_title">Link de convite</string>
    <string name="join_link_close">Fechar</string>
    <string name="join_link_create_title">Criar um link de convite</string>
    <string name="join_link_create_body">Compartilhe um link para que outras pessoas entrem neste grupo.</string>
    <string name="join_link_create_button">Criar link</string>
    <string name="join_link_copy">Copiar</string>
    <string name="join_link_share">Compartilhar</string>
    <string name="join_link_copied">Link copiado</string>
    <string name="join_link_enabled_toggle">Link ativo</string>
    <string name="join_link_who_can_join">Quem pode entrar</string>
    <string name="join_link_rule_anyone">Qualquer pessoa</string>
    <string name="join_link_rule_followed">Pessoas que você segue</string>
    <string name="join_link_require_approval">Exigir aprovação</string>
    <string name="join_link_require_approval_help">Quando ativado, quem tocar no link aparecerá em Solicitações para você aprovar.</string>
    <string name="join_link_unsupported">Este link usa configurações de uma versão mais recente do Nubecita. Atualize para editar.</string>
```

- [ ] **Step 4: Compile + lint the resources**

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:lintProductionDebug`
Expected: BUILD SUCCESSFUL, no `MissingTranslation`.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/res/values/strings.xml \
        feature/chats/impl/src/main/res/values-es/strings.xml \
        feature/chats/impl/src/main/res/values-pt/strings.xml
git commit -m "feat(chats): manage-join-link strings (en, es, pt)

Refs: nubecita-hwix.8"
```

---

## Task 10: Screenshot tests + baselines

**Files:**
- Create: `src/screenshotTest/.../ui/JoinLinkCardScreenshotTest.kt`, `src/screenshotTest/.../ui/CreateJoinLinkContentScreenshotTest.kt`

**Context:** These are statically renderable (no Paging). Use `NubecitaCanvasPreviewTheme`, light + dark `@Preview` with `@PreviewTest`, mirroring `JoinRequestRowScreenshotTest`. A fixed `createdAt` keeps output deterministic.

- [ ] **Step 1: Write `JoinLinkCardScreenshotTest.kt`** (enabled, disabled, unsupported × light/dark)

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.chats.impl.JoinLinkUi
import net.kikin.nubecita.feature.chats.impl.JoinRule
import kotlin.time.Instant

private fun link(
    enabled: Boolean = true,
    joinRule: JoinRule = JoinRule.Anyone,
) = JoinLinkUi(
    code = "code-1",
    url = "https://nubecita.app/group/join/code-1",
    enabled = enabled,
    joinRule = joinRule,
    requireApproval = true,
    createdAt = Instant.parse("2026-05-13T12:00:00Z"),
)

@Composable
private fun Fixture(link: JoinLinkUi) {
    NubecitaCanvasPreviewTheme {
        JoinLinkCard(
            link = link,
            mutationInFlight = false,
            onCopy = {},
            onShare = {},
            onSetEnabled = {},
            onJoinRuleChange = {},
            onRequireApprovalChange = {},
        )
    }
}

@PreviewTest
@Preview(name = "enabled-light")
@Preview(name = "enabled-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinLinkCardEnabled() = Fixture(link(enabled = true))

@PreviewTest
@Preview(name = "disabled-light")
@Preview(name = "disabled-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinLinkCardDisabled() = Fixture(link(enabled = false))

@PreviewTest
@Preview(name = "unsupported-light")
@Preview(name = "unsupported-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinLinkCardUnsupported() = Fixture(link(joinRule = JoinRule.Unsupported))
```

- [ ] **Step 2: Write `CreateJoinLinkContentScreenshotTest.kt`** (empty-state × light/dark)

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

@PreviewTest
@Preview(name = "create-light")
@Preview(name = "create-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateJoinLinkContentPreview() {
    NubecitaCanvasPreviewTheme {
        CreateJoinLinkContent(creating = false, onCreate = { _, _ -> })
    }
}
```

- [ ] **Step 3: Generate baselines**

Run: `./gradlew :feature:chats:impl:updateProductionDebugScreenshotTest`
Expected: writes PNGs under `feature/chats/impl/src/screenshotTestProductionDebug/reference/...`.

- [ ] **Step 4: Validate**

Run: `./gradlew :feature:chats:impl:validateProductionDebugScreenshotTest`
Expected: PASS for the new baselines. (Pre-existing unrelated Mac-vs-CI drift is handled by the `update-baselines` PR label — note it, don't fight it locally.)

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/screenshotTest/ feature/chats/impl/src/screenshotTestProductionDebug/
git commit -m "test(chats): screenshot-cover join-link card and create state

Refs: nubecita-hwix.8"
```

---

## Task 11: Full gate + compose-expert review

- [ ] **Step 1: Run the full local gate**

Run:
```bash
./gradlew spotlessCheck lint :app:checkSortDependencies testDebugUnitTest
```
Expected: all PASS. Fix any spotless/ktlint formatting and re-run.

- [ ] **Step 2: Run the progress-indicator guard** (no unmarked raw spinners)

Run: `./scripts/check_progress_indicators.sh`
Expected: PASS — the only raw `CircularProgressIndicator` is the marked in-button create spinner.

- [ ] **Step 3: compose-expert review**

Invoke the compose-expert Skill on the C3a diff (`git diff main...HEAD` over the new/modified `@Composable` files). Apply CRITICAL + Important findings, re-run the gate, commit fixes:
```bash
git commit -am "fix(chats): address compose-expert findings on join-link UI

Refs: nubecita-hwix.8"
```

- [ ] **Step 4: Push + mark PR #566 ready**

```bash
git push
gh pr ready 566
gh pr edit 566 --add-label update-baselines   # CI regenerates feature baselines (Mac-vs-CI drift)
gh workflow run update-screenshot-baselines.yaml --ref feat/nubecita-hwix.8-join-link-management -f branch=feat/nubecita-hwix.8-join-link-management
```

- [ ] **Step 5: Watch CI to green; triage Gemini/reviewer comments**

Run: `gh pr checks 566` until green; fetch + address unresolved review threads (reply + resolve, or fix).

---

## Self-review notes

- **Spec coverage:** repository read+4 mutations (T3) ✓; `JoinRule.Unsupported` fail-closed mapping (T1) + UI lock (T6) + VM backstop (T5) ✓; single-field `AtField` edit (T3 impl + T5 assertion) ✓; copy snackbar (T6) ✓; M3 empty-state tonal icon (T6) ✓; disabled-card alpha 0.6/onSurfaceVariant/no-selection (T6) ✓; TopAppBar-in-dialog kept (T6, per decision note) ✓; owner row (T7) ✓; adaptiveDialog wiring (T8) ✓; strings (T9) ✓; screenshots enabled/disabled/unsupported/create (T10) ✓; gate + compose-expert (T11) ✓.
- **Type consistency:** `ManageJoinLink(convoId)`, `JoinRule {Anyone,FollowedByOwner,Unsupported}`, `JoinLinkUi(code,url,enabled,joinRule,requireApproval,createdAt)`, `editJoinLink(convoId, joinRule?, requireApproval?)` consistent across T1/T3/T5/T6.
- **Known version-sensitivity:** the clipboard API (T6 Step 5) and `SingleChoiceSegmentedButtonRow` experimental opt-in may need an `@OptIn(ExperimentalMaterial3Api::class)` — resolve at compile time, fall back to `LocalClipboardManager` if needed.
