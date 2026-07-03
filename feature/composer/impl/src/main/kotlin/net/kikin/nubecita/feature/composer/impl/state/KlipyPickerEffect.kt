package net.kikin.nubecita.feature.composer.impl.state

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.data.models.KlipyMediaUi

internal sealed interface KlipyPickerEffect : UiEffect {
    /** An item was chosen — the host attaches it to the post and closes the picker. */
    data class MediaSelected(
        val media: KlipyMediaUi,
    ) : KlipyPickerEffect

    /** A report finished; [succeeded] drives the acknowledgement vs error snackbar. */
    data class ReportCompleted(
        val succeeded: Boolean,
    ) : KlipyPickerEffect
}
