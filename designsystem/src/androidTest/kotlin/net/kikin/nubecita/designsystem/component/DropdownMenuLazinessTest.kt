package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the one property that makes a per-post condition inside [PostCard]'s
 * overflow menu free: **a collapsed [DropdownMenu] does not compose its
 * content**.
 *
 * The menu already branches per post (Mute / Unmute keyed on the viewer
 * projection) and gains another with Delete, keyed on `isOwnPost`. If the
 * content lambda ran for every card in the feed, each of those conditions
 * would be composition work on the scroll path — and 120hz scrolling is a
 * hard requirement here.
 *
 * This is a library guarantee, not ours, which is exactly why it is pinned:
 * a material3 upgrade that changed it would otherwise turn every post card
 * into hidden per-item work with no failing test to show for it. Asserted
 * rather than assumed — the Gradle cache ships only material3's *samples*
 * sources, so the implementation could not be read.
 */
class DropdownMenuLazinessTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun collapsedMenuDoesNotComposeItsContent() {
        val compositions = AtomicInteger(0)

        composeTestRule.setContent {
            MenuUnderTest(expanded = false, recordComposition = { compositions.incrementAndGet() })
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "a collapsed DropdownMenu must not compose its items — doing so would " +
                "put every per-post menu condition on the scroll path",
            0,
            compositions.get(),
        )
    }

    /**
     * The counter is only trustworthy if it can reach a non-zero value, so the
     * expanded case is asserted too. Without this, a broken counter (or an item
     * that never composes for an unrelated reason) would make the test above
     * pass for the wrong reason.
     */
    @Test
    fun expandedMenuDoesComposeItsContent() {
        val compositions = AtomicInteger(0)

        composeTestRule.setContent {
            MenuUnderTest(expanded = true, recordComposition = { compositions.incrementAndGet() })
        }
        composeTestRule.waitForIdle()

        assert(compositions.get() > 0) {
            "expected the expanded menu to compose its item; the counter used by " +
                "collapsedMenuDoesNotComposeItsContent would otherwise prove nothing"
        }
    }

    @Composable
    private fun MenuUnderTest(
        expanded: Boolean,
        recordComposition: () -> Unit,
    ) {
        DropdownMenu(expanded = expanded, onDismissRequest = {}) {
            DropdownMenuItem(
                text = {
                    recordComposition()
                    Text("delete post")
                },
                onClick = {},
            )
        }
    }
}
