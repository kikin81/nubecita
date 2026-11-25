package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.spatial.LocalSpatialMinimumTargetSize
import net.kikin.nubecita.designsystem.spatial.SpatialMinimumTargetSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * Hit-area contract for the post action row: 56dp tall on Android XR (where `MainShell`
 * provides [SpatialMinimumTargetSize]), and the drawn pill size everywhere else, where
 * Compose's own 48dp touch extension covers fingers without changing layout.
 *
 * Measured on the like cell: its toggleable node is the gesture box, found by the
 * icon's merged content description.
 */
class PostActionRowTargetBoundsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val likeLabel: String
        get() = composeTestRule.activity.getString(R.string.postcard_action_like)

    @Test
    fun xr_likeTarget_isAtLeast56dpTall() {
        setCard(minTarget = SpatialMinimumTargetSize)
        val height = likeHeight()
        assertTrue("XR like target is $height, expected >= $SpatialMinimumTargetSize", height >= SpatialMinimumTargetSize)
    }

    @Test
    fun touch_likeTarget_keepsDrawnPillHeight() {
        setCard(minTarget = Dp.Unspecified)
        val height = likeHeight()
        // The pill is an 18dp icon plus 4dp vertical padding: anything near 48dp would
        // mean phones got the XR layout and every feed lost density.
        assertTrue("Phone like target is $height, expected the ~26dp pill", height < 40.dp)
    }

    private fun setCard(minTarget: Dp) {
        composeTestRule.setContent {
            NubecitaTheme {
                CompositionLocalProvider(LocalSpatialMinimumTargetSize provides minTarget) {
                    PostCard(post = POST)
                }
            }
        }
    }

    private fun likeHeight(): Dp {
        val bounds = composeTestRule.onNodeWithContentDescription(likeLabel, useUnmergedTree = false).getUnclippedBoundsInRoot()
        return bounds.bottom - bounds.top
    }

    private companion object {
        val POST =
            PostUi(
                id = "target-bounds",
                cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
                author =
                    AuthorUi(
                        did = "did:plc:fakedid000000000000000",
                        handle = "alice.bsky.social",
                        displayName = "Alice Chen",
                        avatarUrl = null,
                    ),
                createdAt = Instant.parse("2025-10-15T12:00:00Z"),
                text = "Target sizes",
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 3),
                viewer = ViewerStateUi(),
                repostedBy = null,
            )
    }
}
