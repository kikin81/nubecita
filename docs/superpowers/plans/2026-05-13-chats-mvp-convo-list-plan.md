# Chats MVP — Convo list (nubecita-nn3.1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `:feature:chats:impl` module and ship the Chats tab home — a Material 3 Expressive list of the viewer's conversations (avatar + name + last-message snippet + timestamp) rendered from `chat.bsky.convo.listConvos`. Replaces the current `:app`-side placeholder.

**Architecture:** New `:feature:chats:impl` module with MVI ChatsViewModel (`UiState` + sealed `ChatsLoadStatus` sum + sealed Event/Effect), inline `ChatRepository` wrapping `ConvoService.listConvos`, ConvoMapper mapping wire types to a `ConvoListItemUi` data class, and a stateless `ChatsScreenContent` composable that consumes the state. The module's `@MainShell EntryProviderInstaller` for the `Chats` NavKey replaces `:app/MainShellPlaceholderModule.provideChatsPlaceholderEntries`. ChatsScreen exposes `onNavigateToChat: (otherUserDid: String) -> Unit` so nn3.2 can wire it without nn3.1 knowing about the (not-yet-defined) `Chat(otherUserDid)` NavKey.

**Tech Stack:** Kotlin + Jetpack Compose + Material 3 Expressive · Hilt · Navigation 3 · atproto-kotlin (`io.github.kikin81.atproto.chat.bsky.convo.*`) · JUnit 5 (Jupiter) for unit tests · MockK + Turbine + AssertK · Android Screenshot Test plugin

---

## Spec reference

Epic-level spec: `docs/superpowers/specs/2026-05-13-chats-mvp-design.md`. Re-read before starting; this plan implements the convo-list child of that spec.

## File structure

| File | Status | Responsibility |
|---|---|---|
| `settings.gradle.kts` | MODIFY | `include(":feature:chats:impl")` |
| `app/build.gradle.kts` | MODIFY | Add `implementation(project(":feature:chats:impl"))` |
| `app/src/main/java/net/kikin/nubecita/shell/MainShellPlaceholderModule.kt` | MODIFY | Drop `provideChatsPlaceholderEntries` |
| `feature/chats/impl/build.gradle.kts` | NEW | Apply `nubecita.android.feature` convention plugin; declare deps |
| `feature/chats/impl/src/main/AndroidManifest.xml` | NEW | Empty manifest with package |
| `feature/chats/impl/src/main/kotlin/.../ChatsContract.kt` | NEW | `ChatsScreenViewState`, `ChatsLoadStatus`, `ChatsError`, `ChatsEvent`, `ChatsEffect`, `ConvoListItemUi` |
| `feature/chats/impl/src/main/kotlin/.../ChatsViewModel.kt` | NEW | MVI VM, single-flight on Refresh, error mapping |
| `feature/chats/impl/src/main/kotlin/.../ChatsScreen.kt` | NEW | Stateful entry; collects state + effects |
| `feature/chats/impl/src/main/kotlin/.../ChatsScreenContent.kt` | NEW | Stateless content for previews + tests |
| `feature/chats/impl/src/main/kotlin/.../ui/ConvoListItem.kt` | NEW | GChat-style row composable |
| `feature/chats/impl/src/main/kotlin/.../data/ChatRepository.kt` | NEW | Interface + `ConvoListPage` data class |
| `feature/chats/impl/src/main/kotlin/.../data/DefaultChatRepository.kt` | NEW | `withContext(IoDispatcher) { runCatching { ConvoService.listConvos(...) }}` |
| `feature/chats/impl/src/main/kotlin/.../data/ConvoMapper.kt` | NEW | `ConvoView → ConvoListItemUi`; `avatarHueFor` inline copy; relative-timestamp formatter |
| `feature/chats/impl/src/main/kotlin/.../data/Throwable.toChatsError.kt` | NEW | Maps `IOException` → Network, the `NotEnrolled` atproto error → NotEnrolled, else → Unknown |
| `feature/chats/impl/src/main/kotlin/.../di/ChatsNavigationModule.kt` | NEW | `@Provides @IntoSet @MainShell EntryProviderInstaller` for `Chats` |
| `feature/chats/impl/src/main/kotlin/.../di/ChatsRepositoryModule.kt` | NEW | `@Binds ChatRepository` |
| `feature/chats/impl/src/main/res/values/strings.xml` | NEW | Title + error copy + empty-state copy + "You: " prefix + "Sent an attachment" subtitle |
| `feature/chats/impl/src/test/kotlin/.../ChatsViewModelTest.kt` | NEW | JUnit 5 state-machine tests with `FakeChatRepository` |
| `feature/chats/impl/src/test/kotlin/.../FakeChatRepository.kt` | NEW | Result-returning fake |
| `feature/chats/impl/src/test/kotlin/.../data/ConvoMapperTest.kt` | NEW | Mapper unit tests with various `ConvoView` fixtures |
| `feature/chats/impl/src/screenshotTest/kotlin/.../ChatsScreenContentScreenshotTest.kt` | NEW | 4 fixtures × 2 themes (loaded, empty, network error, not-enrolled error) |
| `feature/chats/impl/src/screenshotTest/kotlin/.../ui/ConvoListItemScreenshotTest.kt` | NEW | Row variants (with-avatar, fallback-letter, "You:" prefix, attachment italic, long-snippet truncation) × 2 themes |

## Conventions reminder

- **Branch is already created**: `feat/nubecita-nn3.1-feature-chats-impl-chats-tab-home-convo-list-mvp` (current). Epic spec commit `3125267` is already on the branch.
- **Conventional Commits** with `Refs: nubecita-nn3.1` footer. **Never use `Closes:` in commit messages** — that's PR-body-only.
- **Subject after the colon must start with a lowercase letter** (commitlint will reject sentence-case/PascalCase). Acronyms (MVI, M3, MVP) are OK.
- **Pre-commit hooks** gate spotless + ktlint + commitlint. Never `--no-verify`. If a hook fails, fix the underlying issue.
- **JUnit 5** (Jupiter) — `org.junit.jupiter.api.Test` + `org.junit.jupiter.api.Assertions.*`. Not JUnit 4.
- **MVI** per CLAUDE.md: flat UiState fields for independent flags; sealed status sums for mutually-exclusive lifecycle.
- **Stability**: sealed UI types get `@Immutable` (see `ViewerRelationship` + `EmbedUi` precedent).
- **Existing references**:
  - `:feature:profile:impl/data/DefaultProfileRepository.kt` — the data-layer pattern to mirror.
  - `:feature:profile:impl/ProfileViewModel.kt` — MVI VM with single-flight Job tracking.
  - `:feature:profile:impl/SettingsStubScreen.kt` — Scaffold + TopAppBar pattern for a small stateful screen.
  - `:app/shell/MainShellPlaceholderModule.kt` — the entry-provider pattern (we're replacing the Chats provider).

## Risk to validate early

**Chat endpoints require the `atproto-proxy: did:web:api.bsky.chat#bsky_chat` header to route to Bluesky's chat appview.** Standard `XrpcClientProvider.authenticated()` may not set this. **Task 6 verifies the live `listConvos` call works end-to-end on a test device.** If it fails with a routing error (404 or "unsupported method"), the proxy header isn't being sent — file an issue against `kikin81/atproto-kotlin` per the saved `feedback_file_atproto_kotlin_issues.md` memory, and continue the MVP against a fake response until the upstream lands the header support.

---

## Task 1: Scaffold the `:feature:chats:impl` module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/chats/impl/build.gradle.kts`
- Create: `feature/chats/impl/src/main/AndroidManifest.xml`
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/.gitkeep` (empty placeholder so the package dir is committed)
- Create: `feature/chats/impl/src/main/res/values/strings.xml`

- [ ] **Step 1: Include the module in the root settings**

In `settings.gradle.kts`, find the existing `include(":feature:chats:api")` line and add `include(":feature:chats:impl")` immediately after it.

- [ ] **Step 2: Write the impl module's build.gradle.kts**

`feature/chats/impl/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.chats.impl"
}

dependencies {
    api(project(":feature:chats:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":data:models"))
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
```

- [ ] **Step 3: Write the AndroidManifest.xml**

`feature/chats/impl/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 4: Write the strings.xml**

`feature/chats/impl/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Top app bar -->
    <string name="chats_title">Chats</string>

    <!-- Convo list row copy -->
    <string name="chats_row_you_prefix">You: %1$s</string>
    <string name="chats_row_attachment_placeholder">Sent an attachment</string>
    <string name="chats_row_deleted_placeholder">Message deleted</string>

    <!-- Empty + error states -->
    <string name="chats_empty_title">No conversations yet</string>
    <string name="chats_empty_body">Direct messages with other Bluesky users will appear here.</string>
    <string name="chats_error_network_title">Network error</string>
    <string name="chats_error_network_body">Couldn\'t load your conversations. Check your connection and try again.</string>
    <string name="chats_error_not_enrolled_title">Enable direct messages</string>
    <string name="chats_error_not_enrolled_body">Your Bluesky account hasn\'t opted into direct messages. Enable chat in the official Bluesky app\'s settings to start using DMs.</string>
    <string name="chats_error_unknown_title">Something went wrong</string>
    <string name="chats_error_unknown_body">An unexpected error occurred. Try again in a moment.</string>
    <string name="chats_error_retry">Try again</string>
</resources>
```

- [ ] **Step 5: Confirm the empty module builds**

```bash
./gradlew :feature:chats:impl:assembleDebug
```

Expected: **BUILD SUCCESSFUL** (the module has no Kotlin sources yet, but the Gradle skeleton + manifest + resources compile).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts \
        feature/chats/impl/build.gradle.kts \
        feature/chats/impl/src/main/AndroidManifest.xml \
        feature/chats/impl/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
feat(feature/chats): scaffold the :feature:chats:impl module

Add the module entry to settings.gradle.kts, the build.gradle.kts
applying the nubecita.android.feature convention plugin, a minimal
AndroidManifest, and the string resources used by ChatsScreen (title,
empty/error states, "You:" prefix, attachment placeholder). No Kotlin
sources yet — those land in subsequent tasks.

Refs: nubecita-nn3.1
EOF
)"
```

---

## Task 2: ChatsContract types + ConvoListItemUi

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsContract.kt`

- [ ] **Step 1: Write the contract**

`feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsContract.kt`:

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Chats tab home.
 *
 * The screen has a single mutually-exclusive lifecycle (loading /
 * loaded / error) — per `CLAUDE.md`'s MVI conventions that lives in a
 * sealed [ChatsLoadStatus] sum, not in independent boolean flags.
 *
 * `isRefreshing` is the only flag that may coexist with `Loaded` (pull-
 * to-refresh on an already-loaded list); it lives inside the `Loaded`
 * variant because it's only meaningful in that branch.
 */
data class ChatsScreenViewState(
    val status: ChatsLoadStatus = ChatsLoadStatus.Loading,
) : UiState

sealed interface ChatsLoadStatus {
    /** First load in flight; the body renders a loading composable. */
    data object Loading : ChatsLoadStatus

    /** Convos loaded. May be empty. */
    data class Loaded(
        val items: ImmutableList<ConvoListItemUi> = persistentListOf(),
        val isRefreshing: Boolean = false,
    ) : ChatsLoadStatus

    /** Initial load failed. The body renders an error composable. */
    data class InitialError(
        val error: ChatsError,
    ) : ChatsLoadStatus
}

/**
 * Closed sum of error variants the Chats tab distinguishes for rendering.
 * `NotEnrolled` carries a different empty-state body (and no Retry button)
 * because the user has to act outside the app to fix it.
 */
sealed interface ChatsError {
    data object Network : ChatsError

    data object NotEnrolled : ChatsError

    data class Unknown(
        val cause: String?,
    ) : ChatsError
}

/**
 * UI-ready model for a single conversation row.
 *
 * `lastMessageSnippet == null` only when there are no messages in the convo
 * yet (a brand-new conversation surfaced via `listConvos` before any
 * message has been sent). The row renders an em-dash in that case.
 *
 * `lastMessageFromViewer = true` causes the snippet to render with a
 * "You: " prefix. `lastMessageIsAttachment = true` causes the snippet to
 * render in italic (matches GChat's "Sent an image"); the italic style
 * is composed in the UI layer, not encoded in the snippet string.
 */
@Immutable
data class ConvoListItemUi(
    val convoId: String,
    val otherUserDid: String,
    val otherUserHandle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val avatarHue: Int,
    val lastMessageSnippet: String?,
    val lastMessageFromViewer: Boolean,
    val lastMessageIsAttachment: Boolean,
    val timestampRelative: String,
)

sealed interface ChatsEvent : UiEvent {
    data object Refresh : ChatsEvent

    data class ConvoTapped(
        val otherUserDid: String,
    ) : ChatsEvent

    data object RetryClicked : ChatsEvent
}

sealed interface ChatsEffect : UiEffect {
    data class NavigateToChat(
        val otherUserDid: String,
    ) : ChatsEffect

    /**
     * Sticky errors are rendered inline via `ChatsLoadStatus.InitialError`,
     * not via snackbar. This effect surface is reserved for refresh-time
     * errors that should be transient (Loaded → refresh fails → snackbar).
     */
    data class ShowRefreshError(
        val error: ChatsError,
    ) : ChatsEffect
}
```

- [ ] **Step 2: Compile-only check**

```bash
./gradlew :feature:chats:impl:compileDebugKotlin
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 3: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsContract.kt
git commit -m "$(cat <<'EOF'
feat(feature/chats): add ChatsContract + ConvoListItemUi types

MVI state/event/effect for the Chats tab home. Flat UiState carries a
single sealed ChatsLoadStatus sum (Loading / Loaded / InitialError)
per the project's MVI conventions — mutually-exclusive lifecycles are
sealed sums, not boolean tuples. ConvoListItemUi is @Immutable and
carries primitive fields only (no atproto runtime types leak out of
the data layer).

Refs: nubecita-nn3.1
EOF
)"
```

---

## Task 3: ConvoMapper + avatarHueFor inline + relative-timestamp formatter (TDD)

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ConvoMapper.kt`
- Create: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/ConvoMapperTest.kt`

- [ ] **Step 1: Write the failing tests**

`feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/ConvoMapperTest.kt`:

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileAssociated
import io.github.kikin81.atproto.app.bsky.actor.VerificationState
import io.github.kikin81.atproto.app.bsky.actor.ViewerState
import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.chat.bsky.convo.ConvoView
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageViewSender
import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class ConvoMapperTest {
    @Test
    fun `picks the other member as the row's identity`() {
        val view =
            sampleConvoView(
                members =
                    listOf(
                        sampleMember(did = VIEWER_DID, handle = "me.bsky.social", displayName = "Me"),
                        sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice"),
                    ),
            )

        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)

        assertEquals("did:plc:alice", ui.otherUserDid)
        assertEquals("alice.bsky.social", ui.otherUserHandle)
        assertEquals("Alice", ui.displayName)
    }

    @Test
    fun `null displayName falls back to null and the row will display the handle upstream`() {
        val view = sampleConvoView(otherDisplayName = null)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertNull(ui.displayName)
    }

    @Test
    fun `blank displayName treated as null`() {
        val view = sampleConvoView(otherDisplayName = "   ")
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertNull(ui.displayName)
    }

    @Test
    fun `text MessageView populates the snippet and is not marked as attachment`() {
        val view = sampleConvoView(lastMessage = sampleMessage(text = "hey there", senderDid = "did:plc:alice"))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)

        assertEquals("hey there", ui.lastMessageSnippet)
        assertFalse(ui.lastMessageFromViewer)
        assertFalse(ui.lastMessageIsAttachment)
    }

    @Test
    fun `sender == viewer flips lastMessageFromViewer to true`() {
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = VIEWER_DID))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertTrue(ui.lastMessageFromViewer)
    }

    @Test
    fun `DeletedMessageView yields a 'Message deleted' snippet and isAttachment false`() {
        val view = sampleConvoView(lastMessage = sampleDeleted(senderDid = "did:plc:alice"))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)

        // Sentinel value the UI layer interprets via the chats_row_deleted_placeholder string resource.
        assertEquals("__deleted__", ui.lastMessageSnippet)
        assertFalse(ui.lastMessageIsAttachment)
    }

    @Test
    fun `null lastMessage yields null snippet`() {
        val view = sampleConvoView(lastMessage = null)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertNull(ui.lastMessageSnippet)
    }

    @Test
    fun `avatarHue is deterministic per (did, handle)`() {
        val a = sampleConvoView(otherDid = "did:plc:alice", otherHandle = "alice.bsky.social").toConvoListItemUi(VIEWER_DID, NOW)
        val b = sampleConvoView(otherDid = "did:plc:alice", otherHandle = "alice.bsky.social").toConvoListItemUi(VIEWER_DID, NOW)
        assertEquals(a.avatarHue, b.avatarHue)
        assertTrue(a.avatarHue in 0..359, "hue ${a.avatarHue} MUST be in [0, 359]")
    }

    @Test
    fun `messages sent in the last hour render relative minutes`() {
        val tenMinAgo = NOW.minus(kotlin.time.Duration.parse("10m"))
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = "did:plc:alice", sentAt = tenMinAgo))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertEquals("10m", ui.timestampRelative)
    }

    @Test
    fun `messages from earlier today render relative hours`() {
        val threeHoursAgo = NOW.minus(kotlin.time.Duration.parse("3h"))
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = "did:plc:alice", sentAt = threeHoursAgo))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertEquals("3h", ui.timestampRelative)
    }

    @Test
    fun `messages from yesterday render 'Yesterday'`() {
        // 28 hours ago — guaranteed yesterday in any locale where the previous calendar day exists.
        val yesterday = NOW.minus(kotlin.time.Duration.parse("28h"))
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = "did:plc:alice", sentAt = yesterday))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertEquals("Yesterday", ui.timestampRelative)
    }

    @Test
    fun `null lastMessage yields empty timestamp`() {
        val view = sampleConvoView(lastMessage = null)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertEquals("", ui.timestampRelative)
    }

    private companion object {
        const val VIEWER_DID = "did:plc:viewer123"
        val NOW: Instant = Instant.parse("2026-05-13T18:00:00Z")
    }

    private fun sampleConvoView(
        id: String = "convo-id-1",
        members: List<ProfileViewBasic> =
            listOf(
                sampleMember(did = VIEWER_DID, handle = "me.bsky.social", displayName = "Me"),
                sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice"),
            ),
        otherDid: String? = null,
        otherHandle: String? = null,
        otherDisplayName: String? = "Alice",
        lastMessage: io.github.kikin81.atproto.chat.bsky.convo.ConvoViewLastMessageUnion? = sampleMessage(),
    ): ConvoView {
        val adjustedMembers =
            if (otherDid != null || otherHandle != null) {
                listOf(
                    sampleMember(did = VIEWER_DID, handle = "me.bsky.social", displayName = "Me"),
                    sampleMember(
                        did = otherDid ?: "did:plc:alice",
                        handle = otherHandle ?: "alice.bsky.social",
                        displayName = otherDisplayName,
                    ),
                )
            } else {
                listOf(
                    members[0],
                    sampleMember(
                        did = (members[1].did).raw,
                        handle = (members[1].handle).raw,
                        displayName = otherDisplayName,
                    ),
                )
            }
        return ConvoView(
            id = id,
            members = adjustedMembers,
            muted = false,
            rev = "0",
            unreadCount = 0L,
            lastMessage = lastMessage,
        )
    }

    private fun sampleMember(
        did: String,
        handle: String,
        displayName: String?,
    ): ProfileViewBasic =
        ProfileViewBasic(
            did = Did(did),
            handle = Handle(handle),
            displayName = displayName,
            avatar = null,
            associated = null,
            chatDisabled = null,
            createdAt = null,
            labels = null,
            verification = null,
            viewer = null,
        )

    private fun sampleMessage(
        text: String = "hello",
        senderDid: String = "did:plc:alice",
        sentAt: Instant = Instant.parse("2026-05-13T17:50:00Z"),
    ): MessageView =
        MessageView(
            id = "msg-id-1",
            rev = "0",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime(sentAt.toString()),
            text = text,
            embed = null,
            facets = null,
            reactions = null,
        )

    private fun sampleDeleted(senderDid: String = "did:plc:alice"): DeletedMessageView =
        DeletedMessageView(
            id = "msg-id-deleted",
            rev = "0",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime("2026-05-13T17:50:00Z"),
        )
}
```

- [ ] **Step 2: Run the test, confirm compile failure**

```bash
./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.data.ConvoMapperTest"
```

Expected: **FAIL** with unresolved reference `toConvoListItemUi`.

- [ ] **Step 3: Implement ConvoMapper**

`feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ConvoMapper.kt`:

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.chat.bsky.convo.ConvoView
import io.github.kikin81.atproto.chat.bsky.convo.ConvoViewLastMessageUnion
import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Sentinel snippet emitted when the convo's last message is a
 * [DeletedMessageView]. The UI layer detects this exact value and
 * renders the `chats_row_deleted_placeholder` string in italic.
 *
 * String constant (not enum / sealed sum) because [ConvoListItemUi]
 * is `@Immutable` and we want to keep its surface primitive-only.
 */
internal const val DELETED_MESSAGE_SNIPPET: String = "__deleted__"

/**
 * Maps a wire [ConvoView] to the UI-ready [ConvoListItemUi].
 *
 * Boundary contract: this file is the only place in `:feature:chats:impl`
 * that touches `io.github.kikin81.atproto.chat.bsky.convo.*` runtime
 * types. Everything downstream sees [ConvoListItemUi] with primitive
 * fields.
 *
 * @param viewerDid The current authenticated user's DID, used to pick
 *   the "other member" out of the convo's members list and to determine
 *   whether the last message was sent by the viewer.
 * @param now The current time, injected so tests can assert relative-
 *   timestamp rendering deterministically.
 */
internal fun ConvoView.toConvoListItemUi(
    viewerDid: String,
    now: Instant,
): ConvoListItemUi {
    val other =
        members.firstOrNull { it.did.raw != viewerDid }
            ?: members.first() // Solo-member edge case — degenerate but never crash.
    return ConvoListItemUi(
        convoId = id,
        otherUserDid = other.did.raw,
        otherUserHandle = other.handle.raw,
        displayName = other.displayName?.takeUnless { it.isBlank() },
        avatarUrl = other.avatar?.raw,
        avatarHue = avatarHueFor(did = other.did.raw, handle = other.handle.raw),
        lastMessageSnippet = lastMessage?.snippet(),
        lastMessageFromViewer = lastMessage?.senderDid() == viewerDid,
        lastMessageIsAttachment = lastMessage.isAttachmentOnly(),
        timestampRelative = lastMessage?.sentAt()?.let { sent -> relativeTimestamp(sent, now) } ?: "",
    )
}

/**
 * Deterministic hue in `0..359` from `(did, handle)`. Inline copy of the
 * helper currently living in `:feature:profile:impl/data/AuthorProfileMapper.kt`.
 * Per the spec's YAGNI clause we keep the duplicate copy until a third
 * consumer warrants extraction to `:designsystem`. The two copies MUST
 * stay byte-identical until extraction — diverging would re-paint
 * avatars differently for the same DID.
 */
internal fun avatarHueFor(
    did: String,
    handle: String,
): Int {
    val seed = did + (handle.firstOrNull()?.toString() ?: "")
    return Math.floorMod(seed.hashCode(), 360)
}

private fun ConvoViewLastMessageUnion.snippet(): String? =
    when (this) {
        is MessageView -> text
        is DeletedMessageView -> DELETED_MESSAGE_SNIPPET
        else -> null // SystemMessageView + Unknown — surface no snippet for the MVP.
    }

private fun ConvoViewLastMessageUnion.senderDid(): String? =
    when (this) {
        is MessageView -> sender.did.raw
        is DeletedMessageView -> sender.did.raw
        else -> null
    }

private fun ConvoViewLastMessageUnion?.isAttachmentOnly(): Boolean {
    // V1: there's no MessageView path that's exclusively an attachment without text in the
    // current Bluesky chat schema — `text` is non-nullable. When attachment-only support lands
    // in the lexicon, extend this. Until then: always false.
    val _unused = this
    return false
}

private fun ConvoViewLastMessageUnion.sentAt(): Instant? =
    when (this) {
        is MessageView -> Instant.parse(sentAt.raw)
        is DeletedMessageView -> Instant.parse(sentAt.raw)
        else -> null
    }

/**
 * Renders a wire timestamp into the row's relative-time label:
 *
 * - `< 1 min ago` → `"now"`
 * - `< 1 hour ago` → `"{N}m"`
 * - same calendar day → `"{N}h"`
 * - previous calendar day → `"Yesterday"`
 * - within the last 7 days → short weekday name (e.g. `"Sun"`)
 * - older → `"MMM d"` (e.g. `"Apr 25"`)
 *
 * Calendar comparisons use the system default zone — locally relative,
 * not UTC-relative.
 */
private fun relativeTimestamp(
    sent: Instant,
    now: Instant,
): String {
    val deltaMin = (now - sent).inWholeMinutes
    if (deltaMin < 1) return "now"
    if (deltaMin < 60) return "${deltaMin}m"

    val zone = ZoneId.systemDefault()
    val sentZoned = ZonedDateTime.ofInstant(java.time.Instant.parse(sent.toString()), zone)
    val nowZoned = ZonedDateTime.ofInstant(java.time.Instant.parse(now.toString()), zone)
    val sentDate = sentZoned.toLocalDate()
    val nowDate = nowZoned.toLocalDate()

    if (sentDate == nowDate) {
        val deltaHr = (now - sent).inWholeHours
        return "${deltaHr}h"
    }
    if (sentDate == nowDate.minusDays(1)) return "Yesterday"
    if (sentDate.isAfter(nowDate.minusDays(7))) {
        return sentZoned.format(WEEKDAY_FORMATTER)
    }
    return sentZoned.format(MONTH_DAY_FORMATTER)
}

private val WEEKDAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

private val MONTH_DAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
```

Note: `kotlin.time.Instant` is the Kotlin 2.x experimental Instant type. If the build complains, use `kotlinx.datetime.Instant` instead (depends on which is in the project's classpath — the existing `feature/profile/impl/ProfileViewModelTest.kt` imports `kotlin.time.Instant`, so we follow suit).

- [ ] **Step 4: Run the tests; confirm all pass**

```bash
./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.data.ConvoMapperTest"
```

Expected: **PASS** — 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ConvoMapper.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/ConvoMapperTest.kt
git commit -m "$(cat <<'EOF'
feat(feature/chats): add ConvoMapper for wire → ConvoListItemUi

Maps ConvoView to the UI-ready row model. Picks the other member by
filtering out the viewer's DID. Resolves the last-message snippet from
the lastMessage open union: MessageView → text, DeletedMessageView →
sentinel value the UI maps to a localized "Message deleted" string,
other variants → null. avatarHueFor is inlined (byte-identical to the
copy in :feature:profile:impl per the spec's YAGNI clause). Relative-
timestamp formatter renders "Nm", "Nh", "Yesterday", short weekday,
or "MMM d" depending on calendar distance.

Refs: nubecita-nn3.1
EOF
)"
```

---

## Task 4: ChatRepository interface + DefaultChatRepository.listConvos + ChatsError mapping

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatRepository.kt`
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/DefaultChatRepository.kt`
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMapping.kt`

- [ ] **Step 1: Write ChatRepository.kt (interface + ConvoListPage)**

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi

/**
 * `chat.bsky.convo.*` fetch surface scoped to `:feature:chats:impl`.
 *
 * `nn3.2` extends this interface with `resolveConvo` + `getMessages`
 * additively. No breaking changes between PRs.
 */
internal interface ChatRepository {
    suspend fun listConvos(
        cursor: String? = null,
        limit: Int = LIST_CONVOS_PAGE_LIMIT,
    ): Result<ConvoListPage>
}

internal data class ConvoListPage(
    val items: ImmutableList<ConvoListItemUi> = persistentListOf(),
    val nextCursor: String? = null,
)

internal const val LIST_CONVOS_PAGE_LIMIT: Int = 30
```

- [ ] **Step 2: Write ChatsErrorMapping.kt**

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.feature.chats.impl.ChatsError
import java.io.IOException

/**
 * The `chat.bsky.convo.*` endpoints surface a specific error when the
 * authenticated user hasn't opted into direct messages on Bluesky.
 * The error code as returned by the appview is `"InvalidRequest"` with
 * message text containing the substring `"not enrolled"`. We match
 * on the message substring because the appview's error code is
 * generic — multiple distinct conditions all return InvalidRequest.
 *
 * If Bluesky changes the wording, this match needs to update; we treat
 * any miss as a generic `Unknown` error, which is a safe failure mode
 * (Retry button still rendered).
 */
private const val NOT_ENROLLED_MARKER = "not enrolled"

internal fun Throwable.toChatsError(): ChatsError =
    when (this) {
        is IOException -> ChatsError.Network
        is NoSessionException -> ChatsError.Unknown("not-signed-in")
        is XrpcError -> {
            val msg = message.orEmpty().lowercase()
            if (NOT_ENROLLED_MARKER in msg) ChatsError.NotEnrolled else ChatsError.Unknown(javaClass.simpleName)
        }
        else -> ChatsError.Unknown(javaClass.simpleName)
    }
```

- [ ] **Step 3: Write DefaultChatRepository.kt**

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.ConvoService
import io.github.kikin81.atproto.chat.bsky.convo.ListConvosRequest
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

internal class DefaultChatRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ChatRepository {
        override suspend fun listConvos(
            cursor: String?,
            limit: Int,
        ): Result<ConvoListPage> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).listConvos(
                            ListConvosRequest(
                                cursor = cursor,
                                limit = limit.toLong(),
                            ),
                        )
                    val now = Clock.System.now()
                    ConvoListPage(
                        items =
                            response.convos
                                .map { it.toConvoListItemUi(viewerDid = viewerDid, now = now) }
                                .toImmutableList(),
                        nextCursor = response.cursor,
                    )
                }.onFailure { throwable ->
                    // `cursor` is opaque appview state; the viewer's DID is PII. Log
                    // only the error identity per :core:auth's redaction policy.
                    Timber.tag(TAG).e(throwable, "listConvos failed: %s", throwable.javaClass.name)
                }
            }

        private fun currentViewerDid(): String {
            val signedIn =
                sessionStateProvider.state.value as? SessionState.SignedIn
                    ?: throw NoSessionException()
            return signedIn.did
        }

        private companion object {
            const val TAG = "ChatRepository"
        }
    }
```

- [ ] **Step 4: Compile-only check**

```bash
./gradlew :feature:chats:impl:compileDebugKotlin
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/
git commit -m "$(cat <<'EOF'
feat(feature/chats): add ChatRepository + listConvos default impl

Internal interface scoped to :feature:chats:impl (nn3.2 extends it
additively with resolveConvo + getMessages). DefaultChatRepository
mirrors DefaultProfileRepository: withContext(IoDispatcher) +
runCatching + Timber error-identity logging with the cursor and DID
redacted. Throwable.toChatsError() distinguishes Network (IOException)
/ NotEnrolled (XrpcError whose lowercased message contains "not
enrolled") / Unknown.

Refs: nubecita-nn3.1
EOF
)"
```

---

## Task 5: ChatsViewModel + FakeChatRepository (TDD)

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsViewModel.kt`
- Create: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/FakeChatRepository.kt`
- Create: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsViewModelTest.kt`

- [ ] **Step 1: Write FakeChatRepository.kt**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoListPage
import java.util.concurrent.atomic.AtomicInteger

internal class FakeChatRepository(
    var nextResult: Result<ConvoListPage> = Result.success(ConvoListPage(items = persistentListOf())),
) : ChatRepository {
    val listCalls = AtomicInteger(0)
    var lastCursor: String? = null

    override suspend fun listConvos(
        cursor: String?,
        limit: Int,
    ): Result<ConvoListPage> {
        listCalls.incrementAndGet()
        lastCursor = cursor
        return nextResult
    }
}
```

- [ ] **Step 2: Write the failing ChatsViewModel tests**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.impl.data.ConvoListPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `init kicks off listConvos`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            ChatsViewModel(repository = repo)
            advanceUntilIdle()
            assertEquals(1, repo.listCalls.get())
        }

    @Test
    fun `success commits Loaded with the wire items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c1")))),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertEquals(1, (status as ChatsLoadStatus.Loaded).items.size)
            assertEquals("c1", status.items[0].convoId)
        }

    @Test
    fun `empty result yields Loaded with no items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.success(ConvoListPage(items = persistentListOf())))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertTrue((status as ChatsLoadStatus.Loaded).items.isEmpty())
        }

    @Test
    fun `IOException maps to InitialError(Network)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.failure(IOException("net down")))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.InitialError)
            assertEquals(ChatsError.Network, (status as ChatsLoadStatus.InitialError).error)
        }

    @Test
    fun `ConvoTapped emits NavigateToChat with the same DID`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.effects.test {
                vm.handleEvent(ChatsEvent.ConvoTapped(otherUserDid = "did:plc:alice"))
                val effect = awaitItem()
                assertEquals(ChatsEffect.NavigateToChat("did:plc:alice"), effect)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `RetryClicked re-issues listConvos`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.failure(IOException("net down")))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val priorCalls = repo.listCalls.get()
            repo.nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c1"))))
            vm.handleEvent(ChatsEvent.RetryClicked)
            advanceUntilIdle()
            assertEquals(priorCalls + 1, repo.listCalls.get())
            assertTrue(vm.uiState.value.status is ChatsLoadStatus.Loaded)
        }

    @Test
    fun `Refresh on Loaded sets isRefreshing then commits new items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c1")))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            repo.nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c2"))))
            vm.handleEvent(ChatsEvent.Refresh)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertEquals("c2", (status as ChatsLoadStatus.Loaded).items[0].convoId)
        }

    @Test
    fun `double-Refresh is single-flighted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c1")))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val priorCalls = repo.listCalls.get()
            vm.handleEvent(ChatsEvent.Refresh)
            vm.handleEvent(ChatsEvent.Refresh)
            advanceUntilIdle()
            // First Refresh issues a call; the second is dropped because the first is in flight.
            assertEquals(priorCalls + 1, repo.listCalls.get())
        }

    private fun sampleItem(convoId: String): ConvoListItemUi =
        ConvoListItemUi(
            convoId = convoId,
            otherUserDid = "did:plc:alice",
            otherUserHandle = "alice.bsky.social",
            displayName = "Alice",
            avatarUrl = null,
            avatarHue = 217,
            lastMessageSnippet = "hello",
            lastMessageFromViewer = false,
            lastMessageIsAttachment = false,
            timestampRelative = "10m",
        )
}
```

- [ ] **Step 3: Run the test, confirm compile failure**

```bash
./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.ChatsViewModelTest"
```

Expected: **FAIL** with unresolved reference `ChatsViewModel`.

- [ ] **Step 4: Implement ChatsViewModel**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toChatsError
import javax.inject.Inject

/**
 * Presenter for the Chats tab home.
 *
 * On construction, kicks off a `listConvos` call. Subsequent Refresh
 * and RetryClicked events re-issue the call. Refresh is single-flighted
 * (a second Refresh while the first is in flight is dropped, not
 * queued) so rapid pull-to-refresh gestures can't fan out into
 * multiple concurrent requests.
 *
 * `ConvoTapped` emits a `NavigateToChat(otherUserDid)` effect that the
 * screen collector translates into a `MainShellNavState.add(Chat(did))`
 * call. The Chat NavKey itself lands in nn3.2 — this VM doesn't depend
 * on it.
 */
@HiltViewModel
internal class ChatsViewModel
    @Inject
    constructor(
        private val repository: ChatRepository,
    ) : MviViewModel<ChatsScreenViewState, ChatsEvent, ChatsEffect>(ChatsScreenViewState()) {
        private var inFlightLoad: Job? = null

        init {
            launchLoad()
        }

        override fun handleEvent(event: ChatsEvent) {
            when (event) {
                ChatsEvent.Refresh -> launchLoad()
                ChatsEvent.RetryClicked -> launchLoad()
                is ChatsEvent.ConvoTapped -> sendEffect(ChatsEffect.NavigateToChat(event.otherUserDid))
            }
        }

        private fun launchLoad() {
            if (inFlightLoad?.isActive == true) return // single-flight on rapid Refresh.
            val current = uiState.value.status
            // Preserve the Loaded items + flip isRefreshing so the screen can render the spinner over the
            // existing list rather than blanking. The initial Loading state stays unchanged otherwise.
            if (current is ChatsLoadStatus.Loaded) {
                setState { copy(status = current.copy(isRefreshing = true)) }
            }
            inFlightLoad =
                viewModelScope.launch {
                    repository
                        .listConvos()
                        .onSuccess { page ->
                            setState { copy(status = ChatsLoadStatus.Loaded(items = page.items)) }
                        }.onFailure { throwable ->
                            val error = throwable.toChatsError()
                            val prior = uiState.value.status
                            if (prior is ChatsLoadStatus.Loaded) {
                                // Refresh-time failure: keep existing items visible, drop the
                                // refresh indicator, surface a transient snackbar.
                                setState { copy(status = prior.copy(isRefreshing = false)) }
                                sendEffect(ChatsEffect.ShowRefreshError(error))
                            } else {
                                setState { copy(status = ChatsLoadStatus.InitialError(error)) }
                            }
                        }
                    inFlightLoad = null
                }
        }
    }
```

- [ ] **Step 5: Run the tests; confirm all pass**

```bash
./gradlew :feature:chats:impl:testDebugUnitTest
```

Expected: **PASS** — all VM tests (8) + mapper tests (12) green.

- [ ] **Step 6: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsViewModel.kt \
        feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/
git commit -m "$(cat <<'EOF'
feat(feature/chats): add ChatsViewModel with single-flight Refresh

Mirrors ProfileViewModel's pattern: HiltViewModel-injected, kicks off
the initial listConvos on construction, Refresh + RetryClicked re-
issue the call, single-flight via an inFlightLoad Job (a second
Refresh while the first is in flight is dropped, not queued). Refresh
failures keep existing items visible and surface a transient
ShowRefreshError snackbar; initial-load failures flip status to
InitialError so the screen renders the sticky error composable. 8
ViewModel state-machine tests with a FakeChatRepository fake.

Refs: nubecita-nn3.1
EOF
)"
```

---

## Task 6: Smoke-test the live `listConvos` call (proxy-header validation)

**Risk gate.** Before building the UI, verify the data layer actually talks to Bluesky. The chat endpoints require the `atproto-proxy: did:web:api.bsky.chat#bsky_chat` header, and the standard `XrpcClientProvider.authenticated()` may not send it.

**Files:** (no commits in this task — diagnostic only.)

- [ ] **Step 1: Wire the impl module into the app (temporarily, just to exercise the data layer)**

In `app/build.gradle.kts`, add `implementation(project(":feature:chats:impl"))` to the dependencies block. This is a permanent change that Task 9 also requires; doing it now lets us launch and test.

- [ ] **Step 2: Build + install on a real test device**

```bash
./gradlew :app:installDebug
```

Open the app, sign in if needed, and ensure the Chats tab is reachable (it's still the `:app`-side placeholder at this point — Task 9 replaces it).

- [ ] **Step 3: Add a temporary log-only trigger**

In `app/src/main/java/net/kikin/nubecita/MainActivity.kt`, add a one-time `LaunchedEffect(Unit)` inside the activity's setContent that injects `ChatRepository` (via Hilt's `EntryPoint`) and calls `listConvos()`, logging the result. Sample sketch:

```kotlin
// TEMPORARY — remove after Task 6 confirms the call works.
LaunchedEffect(Unit) {
    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        this@MainActivity.applicationContext,
        ChatSmokeTestEntryPoint::class.java,
    )
    val result = entryPoint.chatRepository().listConvos()
    Timber.tag("ChatSmokeTest").i("listConvos result: $result")
}
```

Add the EntryPoint interface as a private file:

```kotlin
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ChatSmokeTestEntryPoint {
    fun chatRepository(): ChatRepository
}
```

(This is throwaway code, not committed. Remove before continuing to Task 7.)

- [ ] **Step 4: Run the app + inspect logcat**

```bash
adb -s <device> logcat -s ChatSmokeTest:V ChatRepository:V
```

Expected outcomes:

- **Success path**: `listConvos result: Success(ConvoListPage(items=[...]))` with non-empty items if the test account has any DMs. ✅ Continue to Task 7.
- **Failure path A** (proxy-header issue): An XrpcError 404 / "method not found" / similar. The atproto-kotlin runtime isn't sending the proxy header. File an upstream issue per `feedback_file_atproto_kotlin_issues.md`:

  ```bash
  gh issue create --repo kikin81/atproto-kotlin \
    --title "Chat endpoints fail without atproto-proxy header" \
    --body "..."
  ```

  Then either: (a) wait for the upstream fix before continuing — block the bd, OR (b) stub the data layer with a fake that returns canned items, complete the UI work, and unblock when upstream lands. The plan continues to assume (a) lands first.
- **Failure path B** (account not enrolled): XrpcError with "not enrolled" in the message. The `Throwable.toChatsError()` mapping handles this; continue to Task 7. The Chats tab will render the `NotEnrolled` error state when the impl module is wired in.
- **Failure path C** (auth): NoSessionException or a 401. Confirm the test account is signed in. Re-run.

- [ ] **Step 5: Remove the throwaway smoke-test code**

Revert the changes to `MainActivity.kt`. Keep the `app/build.gradle.kts` dependency addition (Task 9 needs it).

- [ ] **Step 6: Verify the app still builds clean**

```bash
./gradlew :app:assembleDebug
```

Expected: **BUILD SUCCESSFUL**.

No commit in this task. The dependency addition in `app/build.gradle.kts` is committed in Task 9 with the rest of the wiring.

---

## Task 7: ConvoListItem composable + screenshot baselines

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/ConvoListItem.kt`
- Create: `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ui/ConvoListItemScreenshotTest.kt`

- [ ] **Step 1: Write ConvoListItem.kt**

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.R
import net.kikin.nubecita.feature.chats.impl.data.DELETED_MESSAGE_SNIPPET

/**
 * GChat-style convo list row.
 *
 * 64dp minimum height for touch target. Tapping the row invokes
 * [onTap] with the other-user's DID — the screen's effect collector
 * translates that into a `MainShellNavState.add(Chat(did))` push.
 *
 * Snippet rendering rules:
 * - `lastMessageSnippet == null` → em-dash.
 * - `lastMessageSnippet == DELETED_MESSAGE_SNIPPET` → italicized
 *   localized "Message deleted" string.
 * - `lastMessageFromViewer == true` → prefix with localized "You: ".
 * - `lastMessageIsAttachment == true` → italicized (attachments
 *   render as the localized "Sent an attachment" placeholder upstream
 *   in the mapper when V2 lands).
 */
@Composable
internal fun ConvoListItem(
    item: ConvoListItemUi,
    onTap: (otherUserDid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clickable(
                    role = Role.Button,
                    onClick = { onTap(item.otherUserDid) },
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item = item, modifier = Modifier.size(40.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TitleRow(item = item)
            SubtitleLine(item = item)
        }
    }
}

@Composable
private fun Avatar(
    item: ConvoListItemUi,
    modifier: Modifier = Modifier,
) {
    val hueColor = Color.hsv(item.avatarHue.toFloat(), saturation = 0.5f, value = 0.55f)
    val initials =
        (item.displayName ?: item.otherUserHandle)
            .firstOrNull { it.isLetterOrDigit() }
            ?.uppercase()
            ?: "?"
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(hueColor),
        contentAlignment = Alignment.Center,
    ) {
        if (item.avatarUrl != null) {
            NubecitaAsyncImage(
                model = item.avatarUrl,
                contentDescription = null,
                modifier = modifier.clip(CircleShape),
            )
        } else {
            Text(
                text = initials,
                color = if (hueColor.luminance() > 0.5f) Color.Black else Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TitleRow(item: ConvoListItemUi) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.displayName ?: item.otherUserHandle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = item.timestampRelative,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubtitleLine(item: ConvoListItemUi) {
    val snippet = item.lastMessageSnippet
    val (text, italic) =
        when {
            snippet == null -> "—" to false
            snippet == DELETED_MESSAGE_SNIPPET -> stringResource(R.string.chats_row_deleted_placeholder) to true
            item.lastMessageIsAttachment -> stringResource(R.string.chats_row_attachment_placeholder) to true
            item.lastMessageFromViewer -> stringResource(R.string.chats_row_you_prefix, snippet) to false
            else -> snippet to false
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
```

- [ ] **Step 2: Write screenshot tests**

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.data.DELETED_MESSAGE_SNIPPET

private val SAMPLE_WITH_AVATAR =
    ConvoListItemUi(
        convoId = "c1",
        otherUserDid = "did:plc:alice",
        otherUserHandle = "alice.bsky.social",
        displayName = "Alice Liddell",
        avatarUrl = "https://cdn.example.com/avatars/alice.jpg",
        avatarHue = 217,
        lastMessageSnippet = "I would love a copy if you have one to spare.",
        lastMessageFromViewer = false,
        lastMessageIsAttachment = false,
        timestampRelative = "10m",
    )

private val SAMPLE_FALLBACK_LETTER =
    SAMPLE_WITH_AVATAR.copy(
        otherUserHandle = "bob.bsky.social",
        displayName = "Bob",
        avatarUrl = null,
        avatarHue = 42,
        lastMessageSnippet = "ok",
        timestampRelative = "Yesterday",
    )

private val SAMPLE_YOU_PREFIX =
    SAMPLE_WITH_AVATAR.copy(
        lastMessageSnippet = "On my way",
        lastMessageFromViewer = true,
        timestampRelative = "3h",
    )

private val SAMPLE_DELETED =
    SAMPLE_WITH_AVATAR.copy(
        lastMessageSnippet = DELETED_MESSAGE_SNIPPET,
        lastMessageFromViewer = false,
        timestampRelative = "Mon",
    )

private val SAMPLE_LONG_SNIPPET =
    SAMPLE_WITH_AVATAR.copy(
        displayName = "Dr. Reginald Wellington-Smythe III",
        lastMessageSnippet =
            "This is a deliberately long message snippet that should wrap to two lines and then ellipsize because the third line of body content would push the row well past 64dp and break the visual rhythm of the list.",
        timestampRelative = "Apr 25",
    )

@PreviewTest
@Preview(name = "convo-with-avatar-light", showBackground = true)
@Preview(name = "convo-with-avatar-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemWithAvatarScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_WITH_AVATAR, onTap = {}) }
    }
}

@PreviewTest
@Preview(name = "convo-fallback-letter-light", showBackground = true)
@Preview(name = "convo-fallback-letter-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemFallbackLetterScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_FALLBACK_LETTER, onTap = {}) }
    }
}

@PreviewTest
@Preview(name = "convo-you-prefix-light", showBackground = true)
@Preview(name = "convo-you-prefix-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemYouPrefixScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_YOU_PREFIX, onTap = {}) }
    }
}

@PreviewTest
@Preview(name = "convo-deleted-light", showBackground = true)
@Preview(name = "convo-deleted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemDeletedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_DELETED, onTap = {}) }
    }
}

@PreviewTest
@Preview(name = "convo-long-snippet-light", showBackground = true)
@Preview(name = "convo-long-snippet-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemLongSnippetScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_LONG_SNIPPET, onTap = {}) }
    }
}
```

- [ ] **Step 3: Record the baselines**

```bash
./gradlew :feature:chats:impl:updateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL**. 10 new PNGs (5 fixtures × 2 themes).

- [ ] **Step 4: Visually inspect 2 baselines via the Read tool**

Spot-check the with-avatar light variant and the you-prefix light variant. Confirm:
- Avatar circle is the correct hue + initial when no URL.
- Title row has name + right-aligned timestamp.
- Subtitle has the right snippet, with "You: " prefix where expected, italic where deleted, ellipsized where long.

- [ ] **Step 5: Validate**

```bash
./gradlew :feature:chats:impl:validateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 6: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/ConvoListItem.kt \
        feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ui/ConvoListItemScreenshotTest.kt \
        feature/chats/impl/src/screenshotTestDebug/reference/
git commit -m "$(cat <<'EOF'
feat(feature/chats): add ConvoListItem composable with GChat-style row

40dp circle avatar (real image when present, deterministic-hue + first-
letter fallback) + title row (display name + right-aligned relative
timestamp) + subtitle line (last-message snippet with "You:" prefix
when the viewer sent it, italic "Message deleted" / "Sent an
attachment" placeholders otherwise). 64dp minimum row height for
touch target. 10 screenshot baselines covering with-avatar / fallback-
letter / you-prefix / deleted / long-snippet variants × light + dark.

Refs: nubecita-nn3.1
EOF
)"
```

---

## Task 8: ChatsScreenContent + screenshot baselines

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsScreenContent.kt`
- Create: `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsScreenContentScreenshotTest.kt`

- [ ] **Step 1: Write ChatsScreenContent.kt**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.chats.impl.ui.ConvoListItem

/**
 * Stateless content for the Chats tab home. The stateful entry
 * [ChatsScreen] hosts the ViewModel; previews + screenshot tests
 * render this composable directly with fixture inputs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatsScreenContent(
    state: ChatsScreenViewState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ChatsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.chats_title)) })
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val status = state.status) {
                ChatsLoadStatus.Loading -> LoadingBody()
                is ChatsLoadStatus.Loaded ->
                    if (status.items.isEmpty()) {
                        EmptyBody()
                    } else {
                        LoadedBody(items = status.items, onTap = { did -> onEvent(ChatsEvent.ConvoTapped(did)) })
                    }
                is ChatsLoadStatus.InitialError -> ErrorBody(error = status.error, onRetry = { onEvent(ChatsEvent.RetryClicked) })
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBody() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chats_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.chats_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LoadedBody(
    items: kotlinx.collections.immutable.ImmutableList<ConvoListItemUi>,
    onTap: (otherUserDid: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = items, key = { it.convoId }, contentType = { "convo-row" }) { item ->
            ConvoListItem(item = item, onTap = onTap)
        }
    }
}

@Composable
private fun ErrorBody(
    error: ChatsError,
    onRetry: () -> Unit,
) {
    val (titleRes, bodyRes, showRetry) =
        when (error) {
            ChatsError.Network -> Triple(R.string.chats_error_network_title, R.string.chats_error_network_body, true)
            ChatsError.NotEnrolled -> Triple(R.string.chats_error_not_enrolled_title, R.string.chats_error_not_enrolled_body, false)
            is ChatsError.Unknown -> Triple(R.string.chats_error_unknown_title, R.string.chats_error_unknown_body, true)
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (showRetry) {
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.chats_error_retry))
            }
        }
    }
}
```

- [ ] **Step 2: Write screenshot tests**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme

private fun sampleItem(convoId: String, displayName: String?, snippet: String, ts: String): ConvoListItemUi =
    ConvoListItemUi(
        convoId = convoId,
        otherUserDid = "did:plc:$convoId",
        otherUserHandle = "$convoId.bsky.social",
        displayName = displayName,
        avatarUrl = null,
        avatarHue = (convoId.hashCode() and 0x7fffffff) % 360,
        lastMessageSnippet = snippet,
        lastMessageFromViewer = false,
        lastMessageIsAttachment = false,
        timestampRelative = ts,
    )

private val LOADED_STATE =
    ChatsScreenViewState(
        status =
            ChatsLoadStatus.Loaded(
                items =
                    persistentListOf(
                        sampleItem("alice", "Alice Liddell", "I would love a copy.", "10m"),
                        sampleItem("bob", "Bob", "ok", "Yesterday"),
                        sampleItem("carol", null, "see you soon", "Mon"),
                    ),
            ),
    )

private val EMPTY_STATE = ChatsScreenViewState(status = ChatsLoadStatus.Loaded(items = persistentListOf()))
private val NETWORK_ERROR_STATE = ChatsScreenViewState(status = ChatsLoadStatus.InitialError(ChatsError.Network))
private val NOT_ENROLLED_STATE = ChatsScreenViewState(status = ChatsLoadStatus.InitialError(ChatsError.NotEnrolled))
private val LOADING_STATE = ChatsScreenViewState(status = ChatsLoadStatus.Loading)

@PreviewTest
@Preview(name = "chats-loaded-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loaded-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = LOADED_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-empty-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-empty-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = EMPTY_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-loading-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loading-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = LOADING_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-network-error-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-network-error-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenNetworkErrorScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = NETWORK_ERROR_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-not-enrolled-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-not-enrolled-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenNotEnrolledScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = NOT_ENROLLED_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}
```

- [ ] **Step 3: Record + validate**

```bash
./gradlew :feature:chats:impl:updateDebugScreenshotTest
./gradlew :feature:chats:impl:validateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL** on both. 10 new PNGs.

- [ ] **Step 4: Spot-check baselines via the Read tool**

Open the `chats-loaded-light` and `chats-not-enrolled-light` baselines. Confirm:
- Loaded shows the M3 TopAppBar with "Chats" title + 3 rows below.
- Not-enrolled shows the title + body + NO retry button (the button is suppressed for this variant).

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsScreenContent.kt \
        feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsScreenContentScreenshotTest.kt \
        feature/chats/impl/src/screenshotTestDebug/reference/
git commit -m "$(cat <<'EOF'
feat(feature/chats): add ChatsScreenContent stateless screen

Scaffold + TopAppBar("Chats") wrapping a body that switches on
ChatsLoadStatus: Loading → centered CircularProgressIndicator;
Loaded(empty) → centered empty-state composable; Loaded(non-empty)
→ LazyColumn of ConvoListItems; InitialError → centered error
composable with Retry button (suppressed for NotEnrolled since the
user has to act outside the app to fix it). 10 screenshot baselines
covering all five state variants × light + dark.

Refs: nubecita-nn3.1
EOF
)"
```

---

## Task 9: ChatsScreen stateful entry + Hilt wiring + replace `:app` placeholder

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsScreen.kt`
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ChatsNavigationModule.kt`
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ChatsRepositoryModule.kt`
- Modify: `app/build.gradle.kts` (add dep — already done in Task 6)
- Modify: `app/src/main/java/net/kikin/nubecita/shell/MainShellPlaceholderModule.kt` (drop the Chats provider)

- [ ] **Step 1: Write ChatsScreen.kt**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful Chats tab-home entry. Owns the [ChatsViewModel] + effect
 * collector + snackbar host. Delegates rendering to [ChatsScreenContent].
 *
 * [onNavigateToChat] is invoked when the user taps a convo row or the
 * VM emits `NavigateToChat`. The caller (the `:feature:chats:impl/di`
 * EntryProviderInstaller for nn3.1; nn3.2's entry provider when that
 * lands) is responsible for translating the DID into a real navigation
 * call (e.g. `MainShellNavState.add(Chat(did))` once the Chat NavKey
 * exists).
 */
@Composable
internal fun ChatsScreen(
    onNavigateToChat: (otherUserDid: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshErrorMsg = stringResource(R.string.chats_error_network_body)
    val currentOnNavigateToChat by rememberUpdatedState(onNavigateToChat)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatsEffect.NavigateToChat -> currentOnNavigateToChat(effect.otherUserDid)
                is ChatsEffect.ShowRefreshError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(refreshErrorMsg)
                }
            }
        }
    }

    ChatsScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Write ChatsRepositoryModule.kt**

```kotlin
package net.kikin.nubecita.feature.chats.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.DefaultChatRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface ChatsRepositoryModule {
    @Binds
    fun bindChatRepository(impl: DefaultChatRepository): ChatRepository
}
```

- [ ] **Step 3: Write ChatsNavigationModule.kt**

```kotlin
package net.kikin.nubecita.feature.chats.impl.di

import androidx.navigation3.runtime.entry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.impl.ChatsScreen
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
internal object ChatsNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideChatsEntries(): EntryProviderInstaller =
        {
            entry<Chats> {
                // nn3.2 replaces this lambda with: navState.add(Chat(otherUserDid)).
                // Until then, log + no-op so taps are visible in instrumentation but don't crash.
                ChatsScreen(
                    onNavigateToChat = { did ->
                        Timber.tag("ChatsNavigation").i("Tap convo did=%s (Chat NavKey lands in nn3.2)", did)
                    },
                )
            }
        }
}
```

- [ ] **Step 4: Drop the `:app`-side Chats placeholder**

In `app/src/main/java/net/kikin/nubecita/shell/MainShellPlaceholderModule.kt`, remove the `provideChatsPlaceholderEntries` function entirely (the Search placeholder stays). Also drop the import of `net.kikin.nubecita.feature.chats.api.Chats` if it becomes unused.

Final shape of `MainShellPlaceholderModule.kt`:

```kotlin
package net.kikin.nubecita.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.R
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.search.api.Search

/**
 * Provides `@MainShell`-qualified placeholder entries for the
 * top-level destinations whose `:feature:*:impl` modules don't exist
 * yet (Search).
 *
 * Each entry renders a [PlaceholderScreen] labelled "<Destination> —
 * coming soon". When a destination's real `:impl` module ships (under
 * its own feature epic), the corresponding `@Provides` here is removed
 * and the new module's `@MainShell EntryProviderInstaller` takes over —
 * a clean module boundary, no bridging artifacts.
 *
 * `:feature:feed:impl` provides the Feed entry,
 * `:feature:profile:impl` provides the Profile + Settings entries, and
 * `:feature:chats:impl` provides the Chats entry — none are placeholdered here.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object MainShellPlaceholderModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideSearchPlaceholderEntries(): EntryProviderInstaller =
        {
            entry<Search> {
                PlaceholderScreen(label = stringResource(R.string.main_shell_tab_search))
            }
        }
}

@Composable
private fun PlaceholderScreen(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.main_shell_placeholder_coming_soon, label))
    }
}
```

- [ ] **Step 5: Ensure `app/build.gradle.kts` has the dep**

In `app/build.gradle.kts`, confirm `implementation(project(":feature:chats:impl"))` is in the dependencies block (added in Task 6's smoke-test step). If not, add it now, alphabetically placed between `:feature:chats:api` and `:feature:composer:api`.

- [ ] **Step 6: Build + run repo-wide unit tests**

```bash
./gradlew :app:assembleDebug :feature:chats:impl:testDebugUnitTest
```

Expected: **BUILD SUCCESSFUL** on both.

- [ ] **Step 7: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsScreen.kt \
        feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ \
        app/build.gradle.kts \
        app/src/main/java/net/kikin/nubecita/shell/MainShellPlaceholderModule.kt
git commit -m "$(cat <<'EOF'
feat(feature/chats): wire :feature:chats:impl into the app shell

Hilt-injected ChatsScreen with effect collector that translates the
VM's NavigateToChat effect into the onNavigateToChat callback. The
@MainShell EntryProviderInstaller for Chats is now owned by
:feature:chats:impl/di/ChatsNavigationModule; the corresponding
provider is removed from :app/MainShellPlaceholderModule. The
onNavigateToChat callback inside the EntryProviderInstaller is a
Timber log for now — nn3.2 replaces it with the real
MainShellNavState.add(Chat(did)) push once the Chat NavKey exists.

Refs: nubecita-nn3.1
EOF
)"
```

---

## Task 10: Final integration check + push

**Files:** none (verification + push only).

- [ ] **Step 1: Repo-wide unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 2: Repo-wide spotless + lint**

```bash
./gradlew spotlessCheck lint
```

Expected: **BUILD SUCCESSFUL** (pre-existing baseline-filtered warnings are fine).

- [ ] **Step 3: App assembles**

```bash
./gradlew :app:assembleDebug
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 4: Validate screenshots**

```bash
./gradlew :feature:chats:impl:validateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: On-device smoke**

```bash
./gradlew :app:installDebug
adb shell monkey -p net.kikin.nubecita -c android.intent.category.LAUNCHER 1
```

Manually:
1. Tap the Chats bottom-nav tab.
2. Verify the M3 TopAppBar "Chats" + the convo list rendered.
3. Verify a row tap logs the tapped DID to logcat (no crash; the no-op is expected pending nn3.2).
4. If the account isn't enrolled in DMs, verify the NotEnrolled error state renders with title + body + no Retry button.

- [ ] **Step 6: Push the branch**

```bash
git push -u origin feat/nubecita-nn3.1-feature-chats-impl-chats-tab-home-convo-list-mvp
```

If push fails over SSH (the saved `gh-https-fallback` memory), fall back to:

```bash
git -c credential.helper='!gh auth git-credential' push https://github.com/kikin81/nubecita.git feat/nubecita-nn3.1-feature-chats-impl-chats-tab-home-convo-list-mvp
```

- [ ] **Step 7: Open the PR**

```bash
gh pr create --base main \
  --title "feat(feature/chats): convo list MVP — :feature:chats:impl module + Chats tab home" \
  --body "$(cat <<'EOF'
## Summary

First child of the Chats MVP epic (nubecita-nn3). Creates :feature:chats:impl and replaces the :app-side Chats placeholder with a real Material 3 Expressive convo list: 40dp circle avatars (with deterministic-hue fallback), display name + right-aligned relative timestamp, last-message snippet with "You:" prefix when the viewer sent it. Driven by chat.bsky.convo.listConvos.

**Spec:** docs/superpowers/specs/2026-05-13-chats-mvp-design.md
**Plan:** docs/superpowers/plans/2026-05-13-chats-mvp-convo-list-plan.md

## Out of scope (deferred to sibling bds)

- Tap-a-row navigation to a chat thread (nubecita-nn3.2 — the row tap currently Timber-logs).
- Tap-Message-from-profile routing (nubecita-a7a).
- Pagination beyond the first page (V2 if necessary).

## Test plan

- [x] Unit: ChatsViewModel + ConvoMapper state-machine tests (20 cases total)
- [x] Screenshot: ChatsScreenContent (5 variants) + ConvoListItem (5 variants), light + dark
- [x] Repo-wide: testDebugUnitTest + spotlessCheck + lint + :app:assembleDebug
- [ ] On-device smoke: Pixel 10 Pro XL — verify list renders for an account with DMs enabled; verify NotEnrolled state for an account that hasn't opted in

Closes: nubecita-nn3.1

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Return the PR URL when done.

---

## Self-review pass

After all tasks complete:

**Spec coverage** (from `docs/superpowers/specs/2026-05-13-chats-mvp-design.md`, nn3.1 scope):

- ✅ `:feature:chats:impl` module created — Task 1.
- ✅ `ChatRepository.listConvos` — Task 4.
- ✅ `ChatsViewModel` MVI with single-flight Refresh — Task 5.
- ✅ `ConvoListItem` GChat-style row — Task 7.
- ✅ `ChatsScreenContent` Scaffold + body switch — Task 8.
- ✅ `ChatsNavigationModule` provides `@MainShell` entry — Task 9.
- ✅ `:app/MainShellPlaceholderModule.provideChatsPlaceholderEntries` dropped — Task 9.
- ✅ Avatar fallback inline (`avatarHueFor` copy of `:feature:profile:impl`) — Task 3.
- ✅ Spec's `NotEnrolled` error path — Task 4 (`Throwable.toChatsError`) + Task 8 (suppress Retry button).
- ✅ Spec's "no row dividers" decision — Task 7 (`ConvoListItem` uses padding, not `HorizontalDivider`).
- ✅ Spec's "no composer in V1" — `ChatsScreenContent` has no composer composable.

No spec gaps for nn3.1.

**Type / API consistency:**

- `ConvoListItemUi` defined in Task 2, consumed in Tasks 3, 5, 7, 8 — signatures match.
- `ChatRepository.listConvos(cursor, limit)` defined in Task 4, called from `ChatsViewModel.launchLoad()` in Task 5 with default args — signatures match.
- `ChatsEvent.ConvoTapped(otherUserDid: String)` defined in Task 2, dispatched from `ConvoListItem.onTap` in Task 7 and handled in `ChatsViewModel.handleEvent` in Task 5 — string parameter consistent.
- `ChatsEffect.NavigateToChat(otherUserDid: String)` defined in Task 2, emitted by VM in Task 5, collected by `ChatsScreen` in Task 9 and translated to `onNavigateToChat(did)` callback — `String` DID parameter consistent throughout.

No type drift.

**Placeholder scan:**

- No `TBD` / `TODO` / `implement later` markers.
- Each step has either runnable commands with expected output or complete code blocks.
- The smoke-test step (Task 6) is the closest thing to "manual" but includes exact commands and expected logcat lines.

No placeholders to fix.
