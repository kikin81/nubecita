package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.data.DateFilter
import net.kikin.nubecita.feature.search.impl.data.MediaFilter
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.ReplyFilter
import java.time.LocalDate
import java.time.ZoneOffset

// A fixed list instead of the device's locales, so the baseline does not depend on the host.
private val languages =
    persistentListOf(
        LanguageChoice(code = "en", label = "English"),
        LanguageChoice(code = "es", label = "Spanish"),
    )

private val customRange = DateFilter.Custom(from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 8))

@Composable
private fun SheetFixture(
    filters: PostSearchFilters,
    hitsTotal: Long?,
) {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostFiltersSheetContent(
                filters = filters,
                hitsTotal = hitsTotal,
                languages = languages,
                onFiltersChange = {},
                onReset = {},
                onDone = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "post-filters-sheet-default-light", showBackground = true, heightDp = 900)
@Preview(name = "post-filters-sheet-default-dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostFiltersSheetDefaultScreenshot() {
    SheetFixture(filters = PostSearchFilters(), hitsTotal = 1_240L)
}

@PreviewTest
@Preview(name = "post-filters-sheet-custom-range-light", showBackground = true, heightDp = 900)
@Composable
private fun PostFiltersSheetCustomRangeScreenshot() {
    SheetFixture(
        filters =
            PostSearchFilters(
                followingOnly = true,
                media = MediaFilter.HAS_MEDIA,
                replies = ReplyFilter.HIDE,
                language = "en",
                date = customRange,
            ),
        hitsTotal = 86L,
    )
}

@PreviewTest
@Preview(name = "post-filters-sheet-no-count-light", showBackground = true, heightDp = 900)
@Composable
private fun PostFiltersSheetNoCountScreenshot() {
    SheetFixture(filters = PostSearchFilters(), hitsTotal = null)
}

@PreviewTest
@Preview(name = "post-filters-sheet-es419", showBackground = true, heightDp = 900, locale = "b+es+419")
@Composable
private fun PostFiltersSheetEs419Screenshot() {
    SheetFixture(filters = PostSearchFilters(followingOnly = true, replies = ReplyFilter.ONLY), hitsTotal = 1_240L)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerFixture(
    start: LocalDate?,
    end: LocalDate?,
) {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            RangePickerFullScreenContent(
                state =
                    rememberDateRangePickerState(
                        initialSelectedStartDateMillis = start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
                        initialSelectedEndDateMillis = end?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
                        initialDisplayedMonthMillis =
                            LocalDate
                                .of(2024, 3, 1)
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli(),
                        // A fixed past year: the picker rings today's date, so a month that can
                        // contain today would change the image every day.
                        yearRange = 2024..2024,
                    ),
                onClose = {},
                onConfirm = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "range-picker-full-screen-chosen-light", showBackground = true, heightDp = 800)
@Composable
private fun RangePickerFullScreenChosenScreenshot() {
    RangePickerFixture(start = LocalDate.of(2024, 3, 3), end = LocalDate.of(2024, 3, 10))
}

@PreviewTest
@Preview(name = "range-picker-full-screen-empty-light", showBackground = true, heightDp = 800)
@Composable
private fun RangePickerFullScreenEmptyScreenshot() {
    RangePickerFixture(start = null, end = null)
}
