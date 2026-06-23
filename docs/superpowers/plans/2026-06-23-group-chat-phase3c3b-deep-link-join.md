# Group chat Phase 3c-3b — Deep-link Join Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping `https://nubecita.app/group/join/{code}` opens a group preview and lets the user join (or request to join), landing in the group thread or a "request sent" confirmation.

**Architecture:** A `GroupJoinPreview(code)` `@MainShell` adaptiveDialog route reached via a `uriDeepLinkMatcher` (no `MainActivity` change — the existing generic publish branch + buffered `DeepLinkRouter` route it, and MainShell-only-when-signed-in gives auth-gating for free). MVI presenter loads `getGroupPublicInfo` and runs the optimistic `requestJoin`. Joiner-specific errors map via a new `toJoinError()`.

**Tech Stack:** Kotlin 2.3, Compose + Material 3 Expressive, Hilt assisted injection, Navigation 3 deep links, atproto-kotlin 9.4.0, JUnit5 + MockK + Turbine, Compose screenshot tests.

**Spec:** `docs/superpowers/specs/2026-06-23-group-chat-phase3c3b-deep-link-join-design.md`
**bd:** `nubecita-hwix.9` (epic `nubecita-hwix` — **this slice closes the epic**). **Branch:** `feat/nubecita-hwix.9-deep-link-join`. **Draft PR:** #568.

---

## File structure

**Create:**
- `feature/chats/impl/.../GroupJoinPreviewContract.kt` — `GroupPublicInfoUi`, `JoinResult`, MVI state/events/effects.
- `feature/chats/impl/.../data/GroupPublicInfoMapper.kt`
- `feature/chats/impl/src/test/.../data/GroupPublicInfoMapperTest.kt`
- `feature/chats/impl/src/test/.../data/JoinErrorMappingTest.kt`
- `feature/chats/api/.../Chats.kt` — add `GroupJoinPreview` (modify).
- `feature/chats/impl/src/test/.../GroupJoinPreviewNavKeyTest.kt`
- `feature/chats/impl/.../di/GroupJoinDeepLinkModule.kt`
- `feature/chats/impl/.../GroupJoinPreviewViewModel.kt`
- `feature/chats/impl/src/test/.../GroupJoinPreviewViewModelTest.kt`
- `feature/chats/impl/.../GroupJoinPreviewScreen.kt`
- `feature/chats/impl/.../ui/GroupJoinPreviewCard.kt`
- `feature/chats/impl/src/screenshotTest/.../ui/GroupJoinPreviewCardScreenshotTest.kt`

**Modify:**
- `feature/chats/impl/.../ChatContract.kt` — 3 new `ChatError` variants.
- `feature/chats/impl/.../data/ChatsErrorMapping.kt` — `toJoinError()` + markers.
- `feature/chats/impl/.../data/ChatRepository.kt` + `data/DefaultChatRepository.kt` — 2 methods.
- the 4 `ChatRepository` fakes (test/androidTest/bench + inline `ChatsUnreadPollingObserverTest`).
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — `entry<GroupJoinPreview>`.
- `app/src/main/AndroidManifest.xml` — `/group/join/` path on the verified App Links filter.
- `feature/chats/impl/src/main/res/values/strings.xml` + `values-b+es+419/` + `values-pt-rBR/`.

**Conventions:** models live in the feature contract file (mirrors `JoinLinkUi`/`JoinRequestUi`, NOT `:data:models`); NavKey tests go in `:feature:chats:impl/src/test` (NOT `:api/src/test`); standalone loaders use `NubecitaWavyProgressIndicator`; the only raw spinner is the in-button one with the `// nubecita-allow-raw-progress` marker.

---

## Task 1: GroupPublicInfoUi + JoinResult + mapper

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupJoinPreviewContract.kt` (model portion only)
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/GroupPublicInfoMapper.kt`
- Test: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/GroupPublicInfoMapperTest.kt`

- [ ] **Step 1: Write the model file**

`GroupJoinPreviewContract.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable

/** Public preview of a group, shown before joining via an invite link. */
@Immutable
data class GroupPublicInfoUi(
    val name: String,
    val memberCount: Int,
    val ownerDisplayName: String?,
    val ownerHandle: String,
    val ownerAvatarUrl: String?,
    val requireApproval: Boolean,
)

/** Outcome of a `requestJoin` call. */
sealed interface JoinResult {
    /** Joined immediately; [convoId] opens the group thread. */
    data class Joined(val convoId: String) : JoinResult

    /** Request is pending owner approval (the group requires approval). */
    data object Pending : JoinResult
}
```

- [ ] **Step 2: Write the failing mapper test**

`GroupPublicInfoMapperTest.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.chat.bsky.group.GroupPublicView
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GroupPublicInfoMapperTest {
    private fun owner(
        handle: String = "alice.bsky.social",
        displayName: String? = "Alice",
        avatar: String? = "https://cdn/av.jpg",
    ) = ProfileViewBasic(
        did = Did("did:plc:alice"),
        handle = Handle(handle),
        displayName = displayName,
        avatar = avatar?.let { Uri(it) },
    )

    private fun view(
        name: String = "Book Club",
        memberCount: Long = 7,
        requireApproval: Boolean = true,
        owner: ProfileViewBasic = owner(),
    ) = GroupPublicView(
        memberCount = memberCount,
        name = name,
        owner = owner,
        requireApproval = requireApproval,
    )

    @Test
    fun `maps fields`() {
        val ui = view().toGroupPublicInfoUi()
        assertEquals("Book Club", ui.name)
        assertEquals(7, ui.memberCount)
        assertEquals("Alice", ui.ownerDisplayName)
        assertEquals("alice.bsky.social", ui.ownerHandle)
        assertEquals("https://cdn/av.jpg", ui.ownerAvatarUrl)
        assertEquals(true, ui.requireApproval)
    }

    @Test
    fun `blank display name maps to null`() {
        assertNull(view(owner = owner(displayName = "  ")).toGroupPublicInfoUi().ownerDisplayName)
    }

    @Test
    fun `null avatar maps to null`() {
        assertNull(view(owner = owner(avatar = null)).toGroupPublicInfoUi().ownerAvatarUrl)
    }
}
```

- [ ] **Step 3: Run it — verify it fails**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*GroupPublicInfoMapperTest"`
Expected: FAIL — `toGroupPublicInfoUi` unresolved.

> If `ProfileViewBasic`/`Did`/`Handle`/`Uri` constructor params differ from the above, inspect an existing usage (e.g. `feature/chats/impl/.../data/JoinRequestMapper.kt` uses `requestedBy.handle.raw` / `.avatar?.raw`) and adapt the fixture — do not change the mapper contract.

- [ ] **Step 4: Write the mapper**

`GroupPublicInfoMapper.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.group.GroupPublicView
import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi

/** Wire → UI for an invite-link group preview. */
internal fun GroupPublicView.toGroupPublicInfoUi(): GroupPublicInfoUi =
    GroupPublicInfoUi(
        name = name,
        memberCount = memberCount.toInt(),
        ownerDisplayName = owner.displayName?.takeUnless { it.isBlank() },
        ownerHandle = owner.handle.raw,
        ownerAvatarUrl = owner.avatar?.raw,
        requireApproval = requireApproval,
    )
```

- [ ] **Step 5: Run it — verify it passes**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*GroupPublicInfoMapperTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupJoinPreviewContract.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/GroupPublicInfoMapper.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/GroupPublicInfoMapperTest.kt
git commit -m "feat(chats): add group public-info model and mapper

Refs: nubecita-hwix.9"
```

---

## Task 2: Join error variants + toJoinError()

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatContract.kt`
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMapping.kt`
- Test: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/JoinErrorMappingTest.kt`

- [ ] **Step 1: Add the 3 ChatError variants** to the `sealed interface ChatError` in `ChatContract.kt` (next to `GroupFull`/`FollowRequiredToAdd`):
```kotlin
    /** The invite link's code is invalid, expired, or the owner disabled the link. */
    data object InvalidInviteLink : ChatError

    /** The group's join rule requires the owner to follow the joiner. */
    data object FollowRequiredToJoin : ChatError

    /** The joiner was removed from this group and cannot rejoin via a link. */
    data object CannotRejoin : ChatError
```

- [ ] **Step 2: Write the failing toJoinError test**

`JoinErrorMappingTest.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.feature.chats.impl.ChatError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class JoinErrorMappingTest {
    private fun xrpc(name: String) = XrpcError(errorName = name, errorMessage = "")

    @Test
    fun `invalid code and disabled link map to InvalidInviteLink`() {
        assertEquals(ChatError.InvalidInviteLink, xrpc("InvalidCode").toJoinError())
        assertEquals(ChatError.InvalidInviteLink, xrpc("LinkDisabled").toJoinError())
    }

    @Test
    fun `member limit maps to GroupFull`() {
        assertEquals(ChatError.GroupFull, xrpc("MemberLimitReached").toJoinError())
    }

    @Test
    fun `follow required maps to FollowRequiredToJoin`() {
        assertEquals(ChatError.FollowRequiredToJoin, xrpc("FollowRequired").toJoinError())
    }

    @Test
    fun `user kicked maps to CannotRejoin`() {
        assertEquals(ChatError.CannotRejoin, xrpc("UserKicked").toJoinError())
    }

    @Test
    fun `unknown error name falls through to Unknown`() {
        assertEquals(ChatError.Unknown::class, xrpc("ConvoLocked").toJoinError()::class)
    }

    @Test
    fun `io exception maps to Network`() {
        assertEquals(ChatError.Network, IOException("x").toJoinError())
    }
}
```

> Confirm `XrpcError`'s constructor params (`errorName`, `errorMessage`) against its existing use in `ChatsErrorMapping.kt` / `toMemberMgmtError`; adapt the fixture if the names differ.

- [ ] **Step 3: Run it — verify it fails**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*JoinErrorMappingTest"`
Expected: FAIL — `toJoinError` unresolved.

- [ ] **Step 4: Add `toJoinError()`** to `ChatsErrorMapping.kt` (after `toMemberMgmtError`). Add the markers near the existing ones:
```kotlin
private const val INVALID_CODE_MARKER = "invalidcode"
private const val LINK_DISABLED_MARKER = "linkdisabled"
private const val FOLLOW_REQUIRED_MARKER = "followrequired"
private const val USER_KICKED_MARKER = "userkicked"
```
```kotlin
/**
 * Map a `chat.bsky.group.requestJoin` / `getGroupPublicInfo` failure to a [ChatError]. Recognises the
 * joiner-side error codes (distinct UX from the owner-side member-mgmt codes) — notably the
 * invalid/disabled-link cases, which joiners routinely hit because owners can disable a link — then
 * delegates everything else to [toChatError].
 *
 * `FOLLOW_REQUIRED_MARKER` is checked AFTER the member-mgmt `NotFollowedBySender`: here `FollowRequired`
 * means "the owner must follow you to admit you", surfaced as [ChatError.FollowRequiredToJoin].
 */
fun Throwable.toJoinError(): ChatError {
    if (this is XrpcError) {
        val haystack = (errorName + " " + errorMessage).lowercase(Locale.ROOT)
        when {
            INVALID_CODE_MARKER in haystack -> return ChatError.InvalidInviteLink
            LINK_DISABLED_MARKER in haystack -> return ChatError.InvalidInviteLink
            MEMBER_LIMIT_MARKER in haystack -> return ChatError.GroupFull
            FOLLOW_REQUIRED_MARKER in haystack -> return ChatError.FollowRequiredToJoin
            USER_KICKED_MARKER in haystack -> return ChatError.CannotRejoin
        }
    }
    return toChatError()
}
```
> Note `FOLLOW_REQUIRED_MARKER = "followrequired"` is a substring of `NotFollowedBySender`? No — `"followrequired"` does not occur in `"notfollowedbysender"`, so there's no clash. Keep the order as written.

- [ ] **Step 5: Run it — verify it passes**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*JoinErrorMappingTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatContract.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMapping.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/JoinErrorMappingTest.kt
git commit -m "feat(chats): map joiner-side request-join errors

Refs: nubecita-hwix.9"
```

---

## Task 3: GroupJoinPreview NavKey

**Files:**
- Modify: `feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt`
- Test: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/GroupJoinPreviewNavKeyTest.kt`

- [ ] **Step 1: Write the failing NavKey test** (mirror `ManageJoinLinkNavKeyTest`, package `…impl`, importing the api key)

`GroupJoinPreviewNavKeyTest.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl

import kotlinx.serialization.json.Json
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GroupJoinPreviewNavKeyTest {
    @Test
    fun `round-trips through json and carries the code`() {
        val key = GroupJoinPreview(code = "abc123")
        assertEquals("abc123", key.code)
        val json = Json.encodeToString(GroupJoinPreview.serializer(), key)
        assertEquals(key, Json.decodeFromString(GroupJoinPreview.serializer(), json))
    }
}
```

- [ ] **Step 2: Run it — verify it fails**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*GroupJoinPreviewNavKeyTest"`
Expected: FAIL — `GroupJoinPreview` unresolved.

- [ ] **Step 3: Add the NavKey to `Chats.kt`** (next to `ManageJoinLink`):
```kotlin
/**
 * Deep-link landing for a group invite (`https://nubecita.app/group/join/{code}`). Previews the
 * group via `getGroupPublicInfo` and lets the user join / request to join. Rendered as an
 * adaptiveDialog (full-screen on Compact, centered dialog on Medium/Expanded).
 */
@Serializable
data class GroupJoinPreview(
    val code: String,
) : NavKey
```

- [ ] **Step 4: Run it — verify it passes**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*GroupJoinPreviewNavKeyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/GroupJoinPreviewNavKeyTest.kt
git commit -m "feat(chats): add the group-join-preview NavKey

Refs: nubecita-hwix.9"
```

---

## Task 4: Repository — getGroupPublicInfo + requestJoin + fakes

**Files:**
- Modify: `feature/chats/impl/.../data/ChatRepository.kt`, `data/DefaultChatRepository.kt`
- Modify: the 4 fakes (`src/test/.../FakeChatRepository.kt`, `src/androidTest/.../FakeChatRepository.kt`, `src/bench/.../data/BenchFakeChatRepository.kt`, `src/test/.../store/ChatsUnreadPollingObserverTest.kt`)

- [ ] **Step 1: Add interface methods to `ChatRepository.kt`** (after the C3a join-link methods, before the closing `}`):
```kotlin
    /** Public preview of a group identified by an invite-link [code] (`getGroupPublicInfo`). */
    suspend fun getGroupPublicInfo(code: String): Result<GroupPublicInfoUi>

    /**
     * Join (or request to join) the group behind invite-link [code]. Returns [JoinResult.Joined]
     * with the convo id when admitted immediately, or [JoinResult.Pending] when the group requires
     * owner approval.
     */
    suspend fun requestJoin(code: String): Result<JoinResult>
```
Add imports: `import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi`, `import net.kikin.nubecita.feature.chats.impl.JoinResult`.

- [ ] **Step 2: Implement in `DefaultChatRepository.kt`**

Add imports:
```kotlin
import io.github.kikin81.atproto.chat.bsky.group.GetGroupPublicInfoRequest
import io.github.kikin81.atproto.chat.bsky.group.RequestJoinRequest
import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi
import net.kikin.nubecita.feature.chats.impl.JoinResult
```
Add inside the repository class (next to the other group ops). NOTE: the `code` is a capability token — only `javaClass.name` is logged, never the `code`:
```kotlin
        override suspend fun getGroupPublicInfo(code: String): Result<GroupPublicInfoUi> =
            withContext(dispatcher) {
                runCatching {
                    GroupService(xrpcClientProvider.authenticated())
                        .getGroupPublicInfo(GetGroupPublicInfoRequest(code = code))
                        .group
                        .toGroupPublicInfoUi()
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "getGroupPublicInfo failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun requestJoin(code: String): Result<JoinResult> =
            withContext(dispatcher) {
                runCatching {
                    val response =
                        GroupService(xrpcClientProvider.authenticated())
                            .requestJoin(RequestJoinRequest(code = code))
                    val convo = response.convo
                    if (convo != null) JoinResult.Joined(convo.id) else JoinResult.Pending
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "requestJoin failed: %s", throwable.javaClass.name)
                }
            }
```
> `toGroupPublicInfoUi` is same-package (`…impl.data`) — no import needed. Confirm `ConvoView.id` is the convo-id accessor (it is — `getConvo` already reads `convo.id` in this file).

- [ ] **Step 3: Update the JVM test fake** (`src/test/.../FakeChatRepository.kt`) — settable results, call captures, gate:
```kotlin
    var getGroupPublicInfoResult: Result<GroupPublicInfoUi> = Result.success(DEFAULT_GROUP_PUBLIC_INFO)
    var requestJoinResult: Result<JoinResult> = Result.success(JoinResult.Pending)
    val getGroupPublicInfoCalls = mutableListOf<String>()
    val requestJoinCalls = mutableListOf<String>()

    /** Optional gate: when set, `requestJoin` suspends on it before returning (test in-flight states). */
    var requestJoinGate: CompletableDeferred<Unit>? = null

    override suspend fun getGroupPublicInfo(code: String): Result<GroupPublicInfoUi> {
        getGroupPublicInfoCalls += code
        return getGroupPublicInfoResult
    }

    override suspend fun requestJoin(code: String): Result<JoinResult> {
        requestJoinCalls += code
        requestJoinGate?.await()
        return requestJoinResult
    }
```
Add a top-level `private val` fixture (mirror `DEFAULT_JOIN_LINK` from C3a):
```kotlin
private val DEFAULT_GROUP_PUBLIC_INFO = GroupPublicInfoUi(
    name = "Group",
    memberCount = 3,
    ownerDisplayName = "Owner",
    ownerHandle = "owner.bsky.social",
    ownerAvatarUrl = null,
    requireApproval = true,
)
```
Add imports `import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi`, `import net.kikin.nubecita.feature.chats.impl.JoinResult`.

- [ ] **Step 4: Update the other 3 implementors** (androidTest fake, bench fake, inline `ChatsUnreadPollingObserverTest` fake) with inert overrides:
```kotlin
    override suspend fun getGroupPublicInfo(code: String): Result<GroupPublicInfoUi> =
        Result.success(STUB_GROUP_PUBLIC_INFO)
    override suspend fun requestJoin(code: String): Result<JoinResult> = Result.success(JoinResult.Pending)
```
where `STUB_GROUP_PUBLIC_INFO = GroupPublicInfoUi("Group", 1, null, "stub.bsky.social", null, false)` (a local/companion `private val`, with the `// Duplicated per source set — source-set isolation prevents sharing the JVM test fixture.` comment). Add the two imports per file.

- [ ] **Step 5: Compile ALL source sets**

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:compileBenchDebugKotlin :feature:chats:impl:compileDebugUnitTestKotlin :feature:chats:impl:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL — no "must override" errors.

- [ ] **Step 6: Run the chats unit suite**

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
git commit -m "feat(chats): repository group public-info and request-join

Refs: nubecita-hwix.9"
```

---

## Task 5: Deep-link matcher + manifest

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/GroupJoinDeepLinkModule.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create the matcher module** (mirror `ChatDeepLinkModule`):
```kotlin
package net.kikin.nubecita.feature.chats.impl.di

import android.content.Intent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.IntentActionFilter
import net.kikin.nubecita.core.common.navigation.NavKeyDeepLinkMatcher
import net.kikin.nubecita.core.common.navigation.uriDeepLinkMatcher
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview

/**
 * Registers the matcher that turns a tapped group invite link
 * (`https://nubecita.app/group/join/{code}`) into a [GroupJoinPreview] NavKey. The `{code}`
 * placeholder maps directly to the route's only field, so `MainActivity.handleIntent`'s generic
 * `else -> matched` branch publishes it to the `DeepLinkRouter` unchanged — no `MainActivity` edit.
 * `MainShell` is composed only when signed-in, and the router is a buffered-channel singleton, so a
 * link tapped while signed-out is held through login then drained. Mirrors `ChatDeepLinkModule`.
 *
 * `accept` rejects a blank code at the matcher boundary.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object GroupJoinDeepLinkModule {
    @Provides
    @IntoSet
    fun provideGroupJoinDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "https://nubecita.app/group/join/{code}",
            serializer = GroupJoinPreview.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { it.code.isNotBlank() },
        )
}
```

- [ ] **Step 2: Add the manifest path** — add a `<data>` element to the **existing verified** `nubecita.app` App Links `<intent-filter android:autoVerify="true">` block (the one with `pathPrefix="/profile/"`), so it reads:
```xml
                <data
                    android:scheme="https"
                    android:host="nubecita.app"
                    android:pathPrefix="/profile/" />
                <data
                    android:scheme="https"
                    android:host="nubecita.app"
                    android:pathPrefix="/group/join/" />
```
(One filter can carry multiple `<data>` elements; both paths share the verified host. No `assetlinks.json` change — the domain is already verified.)

- [ ] **Step 3: Build the app — verify DI + manifest merge + matcher wiring**

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (The matcher joins the Hilt `Set<NavKeyDeepLinkMatcher>` multibinding; `MainActivity` already iterates it. No matcher unit test — matchers need `android.net.Uri`; `ProfileDeepLinkModule` has none either. Coverage: the NavKey round-trip (Task 3) + the core `uriDeepLinkMatcher`/`DefaultDeepLinkRouter` tests in `:core:common`.)

- [ ] **Step 4: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/GroupJoinDeepLinkModule.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat(chats): deep-link matcher and manifest path for group invites

Refs: nubecita-hwix.9"
```

---

## Task 6: GroupJoinPreview contract (state/events/effects)

**Files:**
- Modify: `feature/chats/impl/.../GroupJoinPreviewContract.kt` (append below the Task-1 model)

- [ ] **Step 1: Append the MVI contract**

Add imports (merge, alphabetical):
```kotlin
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
```
Append after `JoinResult`:
```kotlin
/** Mutually-exclusive lifecycle for the group-join preview screen. */
sealed interface GroupJoinPreviewStatus {
    data object Loading : GroupJoinPreviewStatus

    data class Loaded(val info: GroupPublicInfoUi) : GroupJoinPreviewStatus

    data class Error(val error: ChatError) : GroupJoinPreviewStatus

    /** Pending-approval confirmation, shown after a `requestJoin` that returned [JoinResult.Pending]. */
    data object RequestSent : GroupJoinPreviewStatus
}

@Immutable
data class GroupJoinPreviewViewState(
    val status: GroupJoinPreviewStatus = GroupJoinPreviewStatus.Loading,
    val joinInFlight: Boolean = false,
) : UiState

sealed interface GroupJoinPreviewEvent : UiEvent {
    data object Retry : GroupJoinPreviewEvent

    data object JoinTapped : GroupJoinPreviewEvent
}

sealed interface GroupJoinPreviewEffect : UiEffect {
    data class ShowError(val error: ChatError) : GroupJoinPreviewEffect

    data class NavigateToConvo(val convoId: String) : GroupJoinPreviewEffect
}
```
(`ChatError` is same-package — no import.)

- [ ] **Step 2: Compile**

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupJoinPreviewContract.kt
git commit -m "feat(chats): group-join-preview contract

Refs: nubecita-hwix.9"
```

---

## Task 7: GroupJoinPreviewViewModel + tests

**Files:**
- Create: `feature/chats/impl/.../GroupJoinPreviewViewModel.kt`
- Test: `feature/chats/impl/src/test/.../GroupJoinPreviewViewModelTest.kt`

- [ ] **Step 1: Write the failing VM test** (mirror `ManageJoinLinkViewModelTest`: `@RegisterExtension MainDispatcherExtension` + `runTest(mainDispatcher.dispatcher)` + `advanceUntilIdle()`)

`GroupJoinPreviewViewModelTest.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class GroupJoinPreviewViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val route = GroupJoinPreview(code = "code-1")

    private val info = GroupPublicInfoUi(
        name = "Book Club",
        memberCount = 7,
        ownerDisplayName = "Alice",
        ownerHandle = "alice.bsky.social",
        ownerAvatarUrl = null,
        requireApproval = true,
    )

    private fun vm(fake: FakeChatRepository) = GroupJoinPreviewViewModel(route, fake)

    @Test
    fun `load surfaces the preview`() = runTest(mainDispatcher.dispatcher) {
        val fake = FakeChatRepository().apply { getGroupPublicInfoResult = Result.success(info) }
        val model = vm(fake)
        advanceUntilIdle()
        assertEquals(GroupJoinPreviewStatus.Loaded(info), model.uiState.value.status)
        assertEquals(listOf("code-1"), fake.getGroupPublicInfoCalls)
    }

    @Test
    fun `load failure is sticky error and retry reloads`() = runTest(mainDispatcher.dispatcher) {
        val fake = FakeChatRepository().apply { getGroupPublicInfoResult = Result.failure(RuntimeException("x")) }
        val model = vm(fake)
        advanceUntilIdle()
        assertTrue(model.uiState.value.status is GroupJoinPreviewStatus.Error)
        fake.getGroupPublicInfoResult = Result.success(info)
        model.handleEvent(GroupJoinPreviewEvent.Retry)
        advanceUntilIdle()
        assertEquals(GroupJoinPreviewStatus.Loaded(info), model.uiState.value.status)
    }

    @Test
    fun `join that joins directly emits NavigateToConvo`() = runTest(mainDispatcher.dispatcher) {
        val fake = FakeChatRepository().apply {
            getGroupPublicInfoResult = Result.success(info)
            requestJoinResult = Result.success(JoinResult.Joined("convo-9"))
        }
        val model = vm(fake)
        advanceUntilIdle()
        model.effects.test {
            model.handleEvent(GroupJoinPreviewEvent.JoinTapped)
            advanceUntilIdle()
            assertEquals(GroupJoinPreviewEffect.NavigateToConvo("convo-9"), awaitItem())
        }
        assertFalse(model.uiState.value.joinInFlight)
    }

    @Test
    fun `join that is pending shows RequestSent`() = runTest(mainDispatcher.dispatcher) {
        val fake = FakeChatRepository().apply {
            getGroupPublicInfoResult = Result.success(info)
            requestJoinResult = Result.success(JoinResult.Pending)
        }
        val model = vm(fake)
        advanceUntilIdle()
        model.handleEvent(GroupJoinPreviewEvent.JoinTapped)
        advanceUntilIdle()
        assertEquals(GroupJoinPreviewStatus.RequestSent, model.uiState.value.status)
    }

    @Test
    fun `join failure emits error and stays loaded`() = runTest(mainDispatcher.dispatcher) {
        val fake = FakeChatRepository().apply {
            getGroupPublicInfoResult = Result.success(info)
            requestJoinResult = Result.failure(RuntimeException("x"))
        }
        val model = vm(fake)
        advanceUntilIdle()
        model.effects.test {
            model.handleEvent(GroupJoinPreviewEvent.JoinTapped)
            advanceUntilIdle()
            assertTrue(awaitItem() is GroupJoinPreviewEffect.ShowError)
        }
        assertEquals(GroupJoinPreviewStatus.Loaded(info), model.uiState.value.status)
        assertFalse(model.uiState.value.joinInFlight)
    }

    @Test
    fun `in-flight guard drops a second concurrent join`() = runTest(mainDispatcher.dispatcher) {
        val fake = FakeChatRepository().apply {
            getGroupPublicInfoResult = Result.success(info)
            requestJoinResult = Result.success(JoinResult.Pending)
            requestJoinGate = CompletableDeferred()
        }
        val model = vm(fake)
        advanceUntilIdle()
        model.handleEvent(GroupJoinPreviewEvent.JoinTapped) // suspends on the gate
        advanceUntilIdle()
        model.handleEvent(GroupJoinPreviewEvent.JoinTapped) // dropped by the guard
        fake.requestJoinGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("code-1"), fake.requestJoinCalls)
    }
}
```

- [ ] **Step 2: Run it — verify it fails**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*GroupJoinPreviewViewModelTest"`
Expected: FAIL — `GroupJoinPreviewViewModel` unresolved.

- [ ] **Step 3: Write the ViewModel**

`GroupJoinPreviewViewModel.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toJoinError

/**
 * MVI presenter for the invite-link group preview. Loads the public group info on init; `JoinTapped`
 * runs `requestJoin` (guarded by [GroupJoinPreviewViewState.joinInFlight]) — a direct join emits
 * [GroupJoinPreviewEffect.NavigateToConvo], a pending request transitions to
 * [GroupJoinPreviewStatus.RequestSent], and a failure emits [GroupJoinPreviewEffect.ShowError]. The
 * link `code` is never logged (it's a capability token).
 */
@HiltViewModel(assistedFactory = GroupJoinPreviewViewModel.Factory::class)
class GroupJoinPreviewViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: GroupJoinPreview,
        private val repository: ChatRepository,
    ) : MviViewModel<GroupJoinPreviewViewState, GroupJoinPreviewEvent, GroupJoinPreviewEffect>(
            GroupJoinPreviewViewState(),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: GroupJoinPreview): GroupJoinPreviewViewModel
        }

        private val code: String = route.code

        init {
            load()
        }

        override fun handleEvent(event: GroupJoinPreviewEvent) {
            when (event) {
                GroupJoinPreviewEvent.Retry -> load()
                GroupJoinPreviewEvent.JoinTapped -> join()
            }
        }

        private fun load() {
            setState { copy(status = GroupJoinPreviewStatus.Loading) }
            viewModelScope.launch {
                repository.getGroupPublicInfo(code)
                    .onSuccess { setState { copy(status = GroupJoinPreviewStatus.Loaded(it)) } }
                    .onFailure { setState { copy(status = GroupJoinPreviewStatus.Error(it.toJoinError())) } }
            }
        }

        private fun join() {
            if (uiState.value.joinInFlight) return
            if (uiState.value.status !is GroupJoinPreviewStatus.Loaded) return
            setState { copy(joinInFlight = true) }
            viewModelScope.launch {
                try {
                    repository.requestJoin(code)
                        .onSuccess { result ->
                            when (result) {
                                is JoinResult.Joined -> sendEffect(GroupJoinPreviewEffect.NavigateToConvo(result.convoId))
                                JoinResult.Pending -> setState { copy(status = GroupJoinPreviewStatus.RequestSent) }
                            }
                        }
                        .onFailure { sendEffect(GroupJoinPreviewEffect.ShowError(it.toJoinError())) }
                } finally {
                    setState { copy(joinInFlight = false) }
                }
            }
        }
    }
```

- [ ] **Step 4: Run it — verify it passes**

Run: `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*GroupJoinPreviewViewModelTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupJoinPreviewViewModel.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/GroupJoinPreviewViewModelTest.kt
git commit -m "feat(chats): group-join-preview view model

Refs: nubecita-hwix.9"
```

---

## Task 8: Screen + components + English strings

**Files:**
- Create: `feature/chats/impl/.../ui/GroupJoinPreviewCard.kt`, `…/GroupJoinPreviewScreen.kt`
- Modify: `feature/chats/impl/src/main/res/values/strings.xml`

**Context:** Reuse the existing `chat_group_member_count` plurals for "N members". `NubecitaAvatar(model = info.ownerAvatarUrl, contentDescription = null, size = …, fallback = avatarFallbackFor(info.ownerHandle))` — match the exact `avatarFallbackFor` usage from `feature/chats/impl/.../ui/ConvoListItem.kt` or `GroupMemberRow`. Standalone loader = `NubecitaWavyProgressIndicator`; in-button spinner carries the `// nubecita-allow-raw-progress` marker.

- [ ] **Step 1: Add English strings** to `feature/chats/impl/src/main/res/values/strings.xml`:
```xml
    <string name="group_join_title">Join group</string>
    <string name="group_join_close">Close</string>
    <string name="group_join_approval_note">New members are approved by the group owner.</string>
    <string name="group_join_action_join">Join</string>
    <string name="group_join_action_request">Request to join</string>
    <string name="group_join_sent_title">Request sent</string>
    <string name="group_join_sent_body">The group owner will review your request.</string>
    <string name="group_join_done">Done</string>
    <string name="group_join_error_invalid_link">This invite link is invalid or no longer active.</string>
    <string name="group_join_error_follow_required">Only people the group\'s owner follows can join.</string>
    <string name="group_join_error_cannot_rejoin">You can\'t rejoin a group you were removed from.</string>
    <string name="group_join_error_generic">Couldn\'t join the group. Please try again.</string>
```
(Existing reused: `chat_group_member_count` plurals, `new_chat_retry`.)

- [ ] **Step 2: Write `GroupJoinPreviewCard.kt`** (the `Loaded` preview body — a `surfaceContainerLow` invitation card + action button)
```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi
import net.kikin.nubecita.feature.chats.impl.R

/** The invite-preview body: an invitation card + a Join / Request-to-join action. */
@Composable
internal fun GroupJoinPreviewCard(
    info: GroupPublicInfoUi,
    joinInFlight: Boolean,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.chat_group_member_count, info.memberCount, info.memberCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NubecitaAvatar(
                        model = info.ownerAvatarUrl,
                        contentDescription = null,
                        size = 32.dp,
                        fallback = avatarFallbackFor(info.ownerHandle),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        if (info.ownerDisplayName != null) {
                            Text(info.ownerDisplayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("@${info.ownerHandle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (info.requireApproval) {
            Text(
                text = stringResource(R.string.group_join_approval_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onJoin, enabled = !joinInFlight, modifier = Modifier.fillMaxWidth()) {
            if (joinInFlight) {
                // nubecita-allow-raw-progress: in-button micro-spinner with a tuned strokeWidth
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    stringResource(
                        if (info.requireApproval) R.string.group_join_action_request else R.string.group_join_action_join,
                    ),
                )
            }
        }
    }
}
```
> Confirm `avatarFallbackFor`'s signature against `ConvoListItem.kt`/`GroupMemberRow.kt` (it may take a handle or a (did, name) pair) and adapt the call. If no such helper fits, pass `fallback = null` and rely on the URL.

- [ ] **Step 3: Write `GroupJoinPreviewScreen.kt`** (stateful + stateless; mirror `ManageJoinLinkScreen`'s effect collector / Scaffold shape)
```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.ui.GroupJoinPreviewCard

@Composable
internal fun GroupJoinPreviewScreen(
    viewModel: GroupJoinPreviewViewModel,
    onJoined: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val invalidLinkMsg = stringResource(R.string.group_join_error_invalid_link)
    val followMsg = stringResource(R.string.group_join_error_follow_required)
    val cannotRejoinMsg = stringResource(R.string.group_join_error_cannot_rejoin)
    val fullMsg = stringResource(R.string.add_members_error_full)
    val genericMsg = stringResource(R.string.group_join_error_generic)

    LaunchedEffect(Unit) {
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is GroupJoinPreviewEffect.ShowError -> {
                    val msg = when (effect.error) {
                        ChatError.InvalidInviteLink -> invalidLinkMsg
                        ChatError.FollowRequiredToJoin -> followMsg
                        ChatError.CannotRejoin -> cannotRejoinMsg
                        ChatError.GroupFull -> fullMsg
                        else -> genericMsg
                    }
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(msg)
                    }
                }
                is GroupJoinPreviewEffect.NavigateToConvo -> onJoined(effect.convoId)
            }
        }
    }

    GroupJoinPreviewScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onClose = onClose,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupJoinPreviewScreenContent(
    state: GroupJoinPreviewViewState,
    onEvent: (GroupJoinPreviewEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_join_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(name = NubecitaIconName.Close, contentDescription = stringResource(R.string.group_join_close))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).align(Alignment.TopCenter)) {
                when (val status = state.status) {
                    GroupJoinPreviewStatus.Loading ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NubecitaWavyProgressIndicator() }

                    is GroupJoinPreviewStatus.Error ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { onEvent(GroupJoinPreviewEvent.Retry) }) {
                                Text(stringResource(R.string.new_chat_retry))
                            }
                        }

                    is GroupJoinPreviewStatus.Loaded ->
                        GroupJoinPreviewCard(
                            info = status.info,
                            joinInFlight = state.joinInFlight,
                            onJoin = { onEvent(GroupJoinPreviewEvent.JoinTapped) },
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )

                    GroupJoinPreviewStatus.RequestSent ->
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            NubecitaIcon(
                                name = NubecitaIconName.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(24.dp).size(48.dp),
                            )
                            Text(
                                stringResource(R.string.group_join_sent_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                            Text(
                                stringResource(R.string.group_join_sent_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Button(onClick = onClose, modifier = Modifier.padding(top = 24.dp)) {
                                Text(stringResource(R.string.group_join_done))
                            }
                        }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Compile + progress-indicator guard**

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin` (BUILD SUCCESSFUL) then `./scripts/check_progress_indicators.sh` (PASS — only the marked in-button spinner). If `avatarFallbackFor` / `NubecitaAvatar` signatures differ, adapt per the existing `ConvoListItem`/`GroupMemberRow` call sites.

- [ ] **Step 5: spotless + commit**

```bash
./gradlew :feature:chats:impl:spotlessApply
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/GroupJoinPreviewScreen.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/GroupJoinPreviewCard.kt \
        feature/chats/impl/src/main/res/values/strings.xml
git commit -m "feat(chats): group-join-preview screen and components

Refs: nubecita-hwix.9"
```

---

## Task 9: Navigation wiring

**Files:**
- Modify: `feature/chats/impl/.../di/ChatsNavigationModule.kt`

- [ ] **Step 1: Register the entry** (after `entry<ManageJoinLink>`):
```kotlin
            // adaptiveDialog(): full-screen on Compact, centered Dialog on Medium / Expanded.
            // Reached via the GroupJoinDeepLinkModule matcher (an external invite link), pushed onto
            // the current tab by MainShell's deep-link drain. onJoined swaps the preview for the group
            // thread (mirrors NewGroup's post-create navigation); a pending request stays on-screen.
            entry<GroupJoinPreview>(metadata = adaptiveDialog()) { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<GroupJoinPreviewViewModel, GroupJoinPreviewViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                GroupJoinPreviewScreen(
                    viewModel = viewModel,
                    onJoined = { convoId -> navState.replaceTop(Chat(convoId = convoId)) },
                    onClose = { navState.removeLast() },
                )
            }
```
Add imports `net.kikin.nubecita.feature.chats.api.GroupJoinPreview`, and `net.kikin.nubecita.feature.chats.impl.GroupJoinPreviewViewModel` / `GroupJoinPreviewScreen` if needed (mirror how `ManageJoinLink`/`Chat` are imported). `Chat` is already imported in this file.

- [ ] **Step 2: Build the app (DI + nav)**

Run: `./gradlew :feature:chats:impl:compileProductionDebugKotlin :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ChatsNavigationModule.kt
git commit -m "feat(chats): wire group-join-preview route

Refs: nubecita-hwix.9"
```

---

## Task 10: Translations (es / pt)

**Files:**
- Modify: `feature/chats/impl/src/main/res/values-b+es+419/strings.xml`, `values-pt-rBR/strings.xml`

> The real locale dirs are `values-b+es+419` (Latin-American Spanish) and `values-pt-rBR` (Brazilian Portuguese). The `chat_group_member_count` plurals already exist in both (reused — do not re-add).

- [ ] **Step 1: Add Spanish to `values-b+es+419/strings.xml`** (under a `<!-- Group join (nubecita-hwix.9) -->` comment):
```xml
    <string name="group_join_title">Unirse al grupo</string>
    <string name="group_join_close">Cerrar</string>
    <string name="group_join_approval_note">El propietario del grupo aprueba a los nuevos miembros.</string>
    <string name="group_join_action_join">Unirse</string>
    <string name="group_join_action_request">Solicitar unirse</string>
    <string name="group_join_sent_title">Solicitud enviada</string>
    <string name="group_join_sent_body">El propietario del grupo revisará tu solicitud.</string>
    <string name="group_join_done">Listo</string>
    <string name="group_join_error_invalid_link">Este enlace de invitación no es válido o ya no está activo.</string>
    <string name="group_join_error_follow_required">Solo pueden unirse las personas a las que sigue el propietario del grupo.</string>
    <string name="group_join_error_cannot_rejoin">No puedes volver a unirte a un grupo del que fuiste eliminado.</string>
    <string name="group_join_error_generic">No se pudo unir al grupo. Inténtalo de nuevo.</string>
```

- [ ] **Step 2: Add Portuguese to `values-pt-rBR/strings.xml`**:
```xml
    <string name="group_join_title">Entrar no grupo</string>
    <string name="group_join_close">Fechar</string>
    <string name="group_join_approval_note">O proprietário do grupo aprova os novos membros.</string>
    <string name="group_join_action_join">Entrar</string>
    <string name="group_join_action_request">Solicitar entrada</string>
    <string name="group_join_sent_title">Solicitação enviada</string>
    <string name="group_join_sent_body">O proprietário do grupo vai analisar sua solicitação.</string>
    <string name="group_join_done">Concluído</string>
    <string name="group_join_error_invalid_link">Este link de convite é inválido ou não está mais ativo.</string>
    <string name="group_join_error_follow_required">Só podem entrar pessoas que o proprietário do grupo segue.</string>
    <string name="group_join_error_cannot_rejoin">Você não pode voltar a um grupo do qual foi removido.</string>
    <string name="group_join_error_generic">Não foi possível entrar no grupo. Tente novamente.</string>
```

- [ ] **Step 3: Lint (no MissingTranslation)**

Run: `./gradlew :feature:chats:impl:lintProductionDebug`
Expected: BUILD SUCCESSFUL. Verify counts match: each of `values`, `values-b+es+419`, `values-pt-rBR` has all 12 `group_join_*` keys.

- [ ] **Step 4: Commit**

```bash
git add feature/chats/impl/src/main/res/values-b+es+419/strings.xml feature/chats/impl/src/main/res/values-pt-rBR/strings.xml
git commit -m "feat(chats): group-join strings (es, pt)

Refs: nubecita-hwix.9"
```

---

## Task 11: Screenshot tests + full gate + review

**Files:**
- Create: `feature/chats/impl/src/screenshotTest/.../ui/GroupJoinPreviewCardScreenshotTest.kt`

- [ ] **Step 1: Write the screenshot test** (mirror `JoinLinkCardScreenshotTest` exactly — same `NubecitaTheme(dynamicColor = false) { Surface { … } }` wrapper convention, `@PreviewTest` + light/dark `@Preview`)
```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import androidx.compose.material3.Surface
import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi

private fun info(requireApproval: Boolean) = GroupPublicInfoUi(
    name = "Book Club",
    memberCount = 7,
    ownerDisplayName = "Alice",
    ownerHandle = "alice.bsky.social",
    ownerAvatarUrl = null,
    requireApproval = requireApproval,
)

@Composable
private fun Fixture(requireApproval: Boolean) {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            GroupJoinPreviewCard(info = info(requireApproval), joinInFlight = false, onJoin = {})
        }
    }
}

@PreviewTest
@Preview(name = "join-light")
@Preview(name = "join-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupJoinPreviewJoin() = Fixture(requireApproval = false)

@PreviewTest
@Preview(name = "request-light")
@Preview(name = "request-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupJoinPreviewRequest() = Fixture(requireApproval = true)
```
> Confirm the exact wrapper used by `JoinLinkCardScreenshotTest.kt` and copy it verbatim (it may be `NubecitaTheme(dynamicColor = false) { Surface { … } }`). The `RequestSent` hero lives in the screen (not a standalone component), so it isn't separately screenshot-tested here; the two card variants are the meaningful static surfaces.

- [ ] **Step 2: Generate + validate baselines**

Run: `./gradlew :feature:chats:impl:updateProductionDebugScreenshotTest` then `./gradlew :feature:chats:impl:validateProductionDebugScreenshotTest`
Expected: the 4 new baselines (join/request × light/dark) validate. (Mac-vs-CI drift on other baselines is regenerated by the `update-baselines` PR label — don't fight it locally.) Discard any Mac-regenerated PNGs that aren't the new GroupJoinPreviewCard ones (`git checkout -- …` the drift), commit only the new component baselines.

- [ ] **Step 3: Commit screenshots**

```bash
git add feature/chats/impl/src/screenshotTest/ feature/chats/impl/src/screenshotTestProductionDebug/reference/net/kikin/nubecita/feature/chats/impl/ui/GroupJoinPreviewCardScreenshotTestKt/
git commit -m "test(chats): screenshot-cover the group-join preview card

Refs: nubecita-hwix.9"
```

- [ ] **Step 4: Full local gate**

Run: `./gradlew spotlessCheck lint :app:checkSortDependencies testDebugUnitTest` and `./scripts/check_progress_indicators.sh`
Expected: all PASS. Fix formatting and re-run if needed.

- [ ] **Step 5: compose-expert review**

Invoke the compose-expert Skill on the C3b `@Composable` diff (`GroupJoinPreviewScreen.kt`, `ui/GroupJoinPreviewCard.kt`). Apply CRITICAL + Important findings; commit fixes:
```bash
git commit -am "fix(chats): address compose-expert findings on group-join preview

Refs: nubecita-hwix.9"
```

- [ ] **Step 6: Push, mark ready, regenerate CI baselines, request Gemini review**

```bash
git push
gh pr ready 568
gh pr edit 568 --add-label update-baselines
gh workflow run update-screenshot-baselines.yaml --ref feat/nubecita-hwix.9-deep-link-join -f branch=feat/nubecita-hwix.9-deep-link-join
gh pr comment 568 --body "/gemini review"   # diff now contains the code, so Gemini reviews the implementation (gemini-review-only-on-pr-open learning)
```

- [ ] **Step 7: Watch CI to green; triage Gemini/reviewer comments**

`gh pr checks 568` until green (the screenshot job passes once the baseline bot commits CI-rendered images). Address unresolved review threads (reply + resolve, or fix).

---

## Self-review notes

- **Spec coverage:** model+mapper (T1) ✓; 3 ChatError variants + `toJoinError()` mapping the 6 documented `requestJoin` errors (T2) ✓; repo `getGroupPublicInfo`/`requestJoin` with token-safe logging + the `convo != null` discriminator (T4) ✓; NavKey (T3) ✓; matcher (direct, no MainActivity change) + manifest path (T5) ✓; contract incl. `RequestSent` (T6) ✓; VM load/join/Pending/Joined/failure/in-flight-guard (T7) ✓; screen — `surfaceContainerLow` card, member plurals, owner row, requireApproval note, Join/Request label, in-button spinner, `RequestSent` hero badge, Scaffold+TopAppBar kept (T8) ✓; adaptiveDialog wiring + `replaceTop(Chat)` (T9) ✓; es/pt (T10) ✓; screenshots + gate + compose-expert + `/gemini review` after push (T11) ✓. **"Already a member" needs no code** (no SDK signal; idempotent join handled by `Joined → NavigateToConvo`). **Auth-gating needs no code** (buffered router + signed-in-only MainShell). Both documented in the spec.
- **Type consistency:** `GroupJoinPreview(code)`, `GroupPublicInfoUi(name, memberCount:Int, ownerDisplayName:String?, ownerHandle:String, ownerAvatarUrl:String?, requireApproval)`, `JoinResult {Joined(convoId), Pending}`, `getGroupPublicInfo(code):Result<GroupPublicInfoUi>` / `requestJoin(code):Result<JoinResult>`, `toJoinError()`, the 3 `ChatError` variants, `NavigateToConvo(convoId)` — consistent across T1–T9.
- **Deviation from spec:** dropped the standalone deep-link matcher unit test — matchers need `android.net.Uri` (not pure-JVM testable) and `ProfileDeepLinkModule` has none; covered by the NavKey round-trip + the `:core:common` infra tests + the `:app:assembleDebug` DI/manifest check.
- **Version-sensitivity:** `avatarFallbackFor`/`NubecitaAvatar` signatures and the screenshot wrapper must be matched to the existing call sites at implementation time (flagged inline).
