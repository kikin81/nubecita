package net.kikin.nubecita.feature.profile.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.moderation.api.ReportSubject
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.data.ImageChange
import net.kikin.nubecita.feature.profile.impl.data.ProfileHeaderWithViewer
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileTabPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Instrumentation pin-down for oftc.3 PR 4 (nubecita-11h1): tapping
 * "Report account" in the ProfileHero overflow menu on an other-user
 * profile emits `ProfileEffect.NavigateTo(Report(...))` from the VM, and
 * the screen's effect collector routes the key out via its
 * `onNavigateTo: (NavKey) -> Unit` callback — the same host hook that
 * `ProfileNavigationModule` wires to `LocalMainShellNavState.current.add(key)`
 * in production. The Report NavKey carries a [ReportSubject.Account] with
 * the loaded header's DID — the shape `ModerationNavigationModule`'s
 * `@MainShell` provider resolves to a Modal Bottom Sheet hosting the
 * report dialog.
 *
 * The test constructs [MainShellNavState] directly via its public primary
 * constructor (per its kdoc's "Direct construction (tests only)" note)
 * and wires `onNavigateTo = { key -> navState.add(key) }` — the exact
 * lambda `ProfileNavigationModule` uses. The assertion target is
 * `navState.backStack`, the same snapshot list the production inner
 * `NavDisplay` reads. The screen stays host-agnostic per the Nav3
 * modular-hilt recipe (screens take callbacks; hosts wire navState).
 *
 * Mirrors `FeedScreenOverflowReportInstrumentationTest` in
 * `:feature:feed:impl` for the symmetric post-side path. Asserting the
 * dialog's actual render is covered by
 * `ReportDialogScreenInstrumentationTest` inside
 * `:feature:moderation:impl`; standing the dialog up here would force a
 * NavDisplay + Hilt-injected EntryProviderInstaller multibinding harness
 * for no incremental contract gain.
 *
 * The VM's three collaborators are built directly (mockk-relaxed for the
 * cache + session, hand-rolled fake for ProfileRepository) so this test
 * doesn't need a Hilt harness — Profile's instrumentation tests cover
 * both Hilt-driven flows (Settings sign-out) and Hilt-free flows
 * (ProfileScreenInstrumentationTest), so picking the lighter setup here
 * matches the established mix.
 */
class ProfileScreenOverflowReportInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun overflowMenu_reportAccountRow_pushesReportNavKeyOntoActiveTabStack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val overflowDescription = context.getString(R.string.profile_action_overflow)
        val reportLabel = context.getString(R.string.profile_action_report)

        val targetDid = "did:plc:bob"
        val header =
            ProfileHeaderUi(
                did = targetDid,
                handle = "bob.bsky.social",
                displayName = "Bob",
                avatarUrl = null,
                bannerUrl = null,
                avatarHue = 217,
                bio = null,
                location = null,
                website = null,
                joinedDisplay = null,
                postsCount = 0L,
                followersCount = 0L,
                followsCount = 0L,
            )
        val fakeRepo =
            object : ProfileRepository {
                override val ownProfileUpdates = MutableSharedFlow<Unit>()

                override suspend fun updateProfile(
                    displayName: String?,
                    description: String?,
                    avatar: ImageChange,
                    banner: ImageChange,
                ): Result<Unit> = Result.success(Unit)

                override suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer> = Result.success(ProfileHeaderWithViewer(header, ViewerRelationship.NotFollowing()))

                override suspend fun fetchTab(
                    actor: String,
                    tab: ProfileTab,
                    cursor: String?,
                    limit: Int,
                ): Result<ProfileTabPage> = Result.success(ProfileTabPage(nextCursor = null))

                override suspend fun follow(subjectDid: String): Result<String> = Result.failure(UnsupportedOperationException("not exercised"))

                override suspend fun unfollow(followUri: String): Result<Unit> = Result.failure(UnsupportedOperationException("not exercised"))
            }
        val sessionProvider =
            mockk<SessionStateProvider>(relaxed = true).also {
                every { it.state } returns
                    MutableStateFlow(
                        SessionState.SignedIn(handle = "viewer.bsky.social", did = "did:plc:viewer"),
                    )
            }
        val cache =
            mockk<PostInteractionsCache>(relaxed = true).also {
                every { it.state } returns MutableStateFlow(persistentMapOf())
                coEvery { it.toggleLike(any(), any()) } returns Result.success(Unit)
                coEvery { it.toggleRepost(any(), any()) } returns Result.success(Unit)
            }
        val viewModel =
            ProfileViewModel(
                route = Profile(handle = header.handle),
                repository = fakeRepo,
                sessionStateProvider = sessionProvider,
                postInteractionsCache = cache,
            )

        // MainShellNavState's primary constructor accepts a startRoute,
        // a top-level key state holder, and a per-tab map of NavBackStacks.
        // Seed with a single Profile tab carrying the route as its base —
        // mirrors `ProfileNavigationModule`'s entry-installer surface so
        // `navState.add(...)` lands on this tab's stack the same way
        // production does.
        val profileRoot: NavKey = Profile(handle = header.handle)
        val navState =
            MainShellNavState(
                startRoute = profileRoot,
                topLevelKeyState = mutableStateOf(profileRoot),
                backStacks = mapOf<NavKey, NavBackStack<NavKey>>(profileRoot to NavBackStack(profileRoot)),
            )

        composeTestRule.setContent {
            // ProfileScreen reads LocalMainShellNavState.current
            // unconditionally to wire its back-handler. The local has no
            // default value (compositionLocalOf { error(...) }), so the
            // test must provide it explicitly — without this wrap the
            // first composition crashes with "MainShellNavState not
            // provided" before the overflow menu ever renders. The
            // host-provided `onNavigateTo` callback still carries the
            // sub-route push contract under test; this provider just
            // satisfies the back-handler read.
            NubecitaTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    ProfileScreen(
                        viewModel = viewModel,
                        onNavigateToPost = {},
                        onNavigateToProfile = {},
                        onNavigateToSettings = {},
                        onNavigateToMessage = {},
                        onNavigateToMediaViewer = { _, _ -> },
                        onNavigateToVideoPlayer = {},
                        onNavigateTo = { key -> navState.add(key) },
                    )
                }
            }
        }

        // Wait for the header load to land so the overflow menu renders
        // on the other-user actions row. Without this, the overflow
        // IconButton isn't in the composition yet.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasContentDescription(overflowDescription))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Sanity: stack starts at just the Profile tab home.
        assertEquals(listOf<NavKey>(profileRoot), navState.backStack.toList())

        // Tap the overflow IconButton, then the "Report" entry in the
        // DropdownMenu it opens.
        composeTestRule.onNodeWithContentDescription(overflowDescription).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(reportLabel).performClick()

        // The screen's effect collector resolves the
        // `ProfileEffect.NavigateTo` branch by invoking the `onNavigateTo`
        // callback wired above — which lands directly on `navState.add`.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            navState.backStack.any { it is Report }
        }

        val pushedReport = navState.backStack.last()
        assertTrue(
            "expected last back-stack key to be a Report sub-route, got $pushedReport",
            pushedReport is Report,
        )
        pushedReport as Report
        val subject = pushedReport.subject
        assertTrue(
            "expected Report subject to be an Account, got $subject",
            subject is ReportSubject.Account,
        )
        subject as ReportSubject.Account
        assertEquals(targetDid, subject.did)
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L
    }
}
