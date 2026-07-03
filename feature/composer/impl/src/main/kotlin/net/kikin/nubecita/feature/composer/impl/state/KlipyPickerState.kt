package net.kikin.nubecita.feature.composer.impl.state

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.KlipyMediaType
import net.kikin.nubecita.data.models.KlipyMediaUi

/**
 * State for the KLIPY picker. The paged grid itself is NOT here — it's a
 * `Flow<PagingData<KlipyMediaUi>>` exposed directly by the ViewModel and
 * collected via `collectAsLazyPagingItems()` (paging owns its own load state).
 * This holds only the chrome around the grid.
 *
 * [preview] is the long-pressed item shown in the preview/report overlay
 * (null = no overlay).
 */
@Immutable
internal data class KlipyPickerState(
    val tab: KlipyMediaType = KlipyMediaType.GIF,
    val query: String = "",
    val categories: ImmutableList<String> = persistentListOf(),
    val selectedCategory: String? = null,
    val preview: KlipyMediaUi? = null,
) : UiState
