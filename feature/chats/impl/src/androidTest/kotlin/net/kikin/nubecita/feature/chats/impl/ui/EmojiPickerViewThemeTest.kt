package net.kikin.nubecita.feature.chats.impl.ui

import android.content.Context
import android.util.TypedValue
import androidx.core.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The emoji picker is an AndroidX **View**, so it takes its colours from the
 * XML theme rather than the Compose `MaterialTheme` the dialog sits inside.
 * The app's XML theme is hard-coded `Theme.Material.Light` with no
 * `values-night`, so in dark mode the picker drew near-black category headers
 * on a dark Surface — unreadable (nubecita-io24.4).
 *
 * This asserts the thing a screenshot cannot: what `textColorPrimary` actually
 * resolves to inside the wrapped context. A Compose screenshot test can't see
 * into an `AndroidView`, which is why this is instrumented rather than a
 * baseline.
 */
@RunWith(AndroidJUnit4::class)
class EmojiPickerViewThemeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * `textColorPrimary` resolves either to an inline colour (in [TypedValue.data],
     * with `resourceId == 0`) or to a resource — usually a `ColorStateList`. Reading
     * `getColor(out.resourceId)` unconditionally throws `Resources.NotFoundException`
     * in the inline case, so branch on the type rather than assuming the shape the
     * current platform theme happens to use.
     */
    private fun textColorPrimaryOf(isDark: Boolean): Int {
        val themed = emojiPickerThemedContext(context, isDark)
        val out = TypedValue()
        check(themed.theme.resolveAttribute(android.R.attr.textColorPrimary, out, true)) {
            "textColorPrimary did not resolve in the ${if (isDark) "dark" else "light"} picker theme"
        }
        return if (out.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            out.data
        } else {
            // A ColorStateList resource: getColorStateList gives us its default.
            themed.getColorStateList(out.resourceId).defaultColor
        }
    }

    @Test
    fun darkTheme_resolvesLightPrimaryText() {
        val luminance = textColorPrimaryOf(isDark = true).luminance
        assertTrue(
            "Dark picker must draw LIGHT text; got luminance $luminance. " +
                "A dark-on-dark value is the original bug.",
            luminance > 0.5f,
        )
    }

    @Test
    fun lightTheme_resolvesDarkPrimaryText() {
        val luminance = textColorPrimaryOf(isDark = false).luminance
        assertTrue(
            "Light picker must draw DARK text; got luminance $luminance.",
            luminance < 0.5f,
        )
    }

    @Test
    fun theTwoThemesActuallyDiffer() {
        // Guards the failure where both branches resolve to the same theme —
        // each test above would still pass if the OTHER one were also correct,
        // but a single wrong `if` would make them identical.
        assertTrue(
            "light and dark picker themes resolved to the same text colour",
            textColorPrimaryOf(isDark = true) != textColorPrimaryOf(isDark = false),
        )
    }
}
