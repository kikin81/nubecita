package net.kikin.nubecita.feature.composer.impl.state

import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.klipy.KlipyReportReason
import net.kikin.nubecita.data.models.KlipyMediaType
import net.kikin.nubecita.data.models.KlipyMediaUi

internal sealed interface KlipyPickerEvent : UiEvent {
    /** The user typed in the "Search KLIPY" field. */
    data class QueryChanged(
        val query: String,
    ) : KlipyPickerEvent

    /** The user switched the GIF / Sticker tab. */
    data class TabSelected(
        val tab: KlipyMediaType,
    ) : KlipyPickerEvent

    /** The user tapped a category chip (Recents / Trending / a server category). */
    data class CategorySelected(
        val category: String,
    ) : KlipyPickerEvent

    /** The user long-pressed an item to preview it (fires a KLIPY view). */
    data class ItemPreviewed(
        val media: KlipyMediaUi,
    ) : KlipyPickerEvent

    /** The preview overlay was dismissed. */
    data object PreviewDismissed : KlipyPickerEvent

    /** The user selected an item to attach (fires a KLIPY share). */
    data class ItemSelected(
        val media: KlipyMediaUi,
    ) : KlipyPickerEvent

    /** The user reported an item from the preview overlay. */
    data class ItemReported(
        val media: KlipyMediaUi,
        val reason: KlipyReportReason,
    ) : KlipyPickerEvent
}
