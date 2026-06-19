package net.kikin.nubecita.feature.profile.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
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
import kotlin.time.Instant
import net.kikin.nubecita.designsystem.R as DesignsystemR

/**
 * Instrumentation pin-down for `nubecita-oftc.3.1` (Profile Posts-tab
 * surface): the PostCard overflow's "Report post" row, tapped on a
 * PostCard rendered inside the Profile screen's Posts tab body, emits
 * `ProfileEffect.NavigateTo(Report(...))` from the VM. The screen's
 * effect collector routes the key out via its
 * `onNavigateTo: (NavKey) -> Unit` callback — the same host hook that
 * `ProfileNavigationModule` wires to
 * `LocalMainShellNavState.current.add(key)` in production. The Report
 * NavKey carries a [ReportSubject.Post] with the tapped post's URI +
 * CID — the same shape `ModerationNavigationModule`'s `@MainShell`
 * provider resolves to a Modal Bottom Sheet hosting the report dialog.
 *
 * Mirrors `FeedScreenOverflowReportInstrumentationTest` in
 * `:feature:feed:impl` for the symmetric path through the Profile
 * Posts tab. The sibling `ProfileScreenOverflowReportInstrumentationTest`
 * pins the account-level (ProfileHero overflow) Report; this test
 * pins the per-post (PostCard overflow in the Posts tab body) Report.
 *
 * Construction mirrors `ProfileScreenOverflowReportInstrumentationTest`:
 * the VM's three collaborators are built directly (mockk for cache +
 * session, hand-rolled fake for ProfileRepository) so this test doesn't
 * need a Hilt harness. The fake's `fetchTab` returns a single PostUi so
 * the Posts tab renders a PostCard with an overflow affordance.
 */
class ProfileScreenPostsTabOverflowReportInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun postsTabPostCardOverflow_reportRow_pushesReportNavKeyOntoActiveTabStack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val moreOptionsCd = context.getString(DesignsystemR.string.postcard_action_more)
        val reportPostLabel = context.getString(DesignsystemR.string.moderation_action_report_post)

        val targetUri = "at://did:plc:author/app.bsky.feed.post/rprt1"
        val targetCid = "bafyreitestreportcid"
        val postText = "Sample post for Posts-tab overflow Report test"
        val targetPost =
            PostUi(
                id = targetUri,
                cid = targetCid,
                author =
                    AuthorUi(
                        did = "did:plc:author",
                        handle = "author.bsky.social",
                        displayName = "Author",
                        avatarUrl = null,
                    ),
                createdAt = Instant.parse("2026-05-19T10:00:00Z"),
                text = postText,
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            )
        val header =
            ProfileHeaderUi(
                did = "did:plc:author",
                handle = "author.bsky.social",
                displayName = "Author",
                avatarUrl = null,
                bannerUrl = null,
                bio = null,
                location = null,
                website = null,
                joinedDisplay = null,
                postsCount = 1L,
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
                ): Result<ProfileTabPage> =
                    when (tab) {
                        // Populate Posts with the target. Replies / Media stay
                        // empty so the screen lands on a single-post Posts tab.
                        ProfileTab.Posts ->
                            Result.success(
                                ProfileTabPage(
                                    items = persistentListOf(TabItemUi.Post(targetPost)),
                                    nextCursor = null,
                                ),
                            )
                        else -> Result.success(ProfileTabPage(nextCursor = null))
                    }

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
        val entitlementRepository =
            mockk<EntitlementRepository>(relaxed = true).also {
                every { it.isPro } returns MutableStateFlow(false)
            }
        val viewModel =
            ProfileViewModel(
                route = Profile(handle = header.handle),
                repository = fakeRepo,
                sessionStateProvider = sessionProvider,
                postInteractionsCache = cache,
                entitlementRepository = entitlementRepository,
            )

        val profileRoot: NavKey = Profile(handle = header.handle)
        val navState =
            MainShellNavState(
                startRoute = profileRoot,
                topLevelKeyState = mutableStateOf(profileRoot),
                backStacks = mapOf<NavKey, NavBackStack<NavKey>>(profileRoot to NavBackStack(profileRoot)),
            )

        composeTestRule.setContent {
            // ProfileScreen reads LocalMainShellNavState unconditionally
            // (back-handler wiring); the local has no default. Provide
            // explicitly so first composition doesn't crash before the
            // Posts tab renders. See ProfileScreenOverflowReportInstrumentationTest
            // for the same setup; sibling test for the hero-overflow path.
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

        // Wait for the Posts-tab PostCard to render — its body text is the
        // signal that the tab body has resolved its `Loaded` status from
        // the fake repo's first emission.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(postText, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Sanity: stack starts at just the Profile tab home.
        assertEquals(listOf<NavKey>(profileRoot), navState.backStack.toList())

        // Tap the PostCard's overflow affordance. `useUnmergedTree`
        // because the icon button's semantics are nested under the
        // action row's merged tree; without it the matcher returns
        // nothing. Mirrors FeedScreenOverflowReportInstrumentationTest.
        composeTestRule
            .onAllNodes(hasContentDescription(moreOptionsCd), useUnmergedTree = true)[0]
            .performClick()
        composeTestRule.waitForIdle()

        // DropdownMenu is now open — tap "Report post".
        composeTestRule.onNodeWithText(reportPostLabel).performClick()

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
            "expected Report subject to be a Post, got $subject",
            subject is ReportSubject.Post,
        )
        subject as ReportSubject.Post
        assertEquals(targetUri, subject.uri)
        assertEquals(targetCid, subject.cid)
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L
    }
}
