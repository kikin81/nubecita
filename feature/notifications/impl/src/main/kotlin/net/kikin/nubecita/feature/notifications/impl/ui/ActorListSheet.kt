package net.kikin.nubecita.feature.notifications.impl.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.feature.notifications.impl.NotificationsTestTags

/**
 * Modal bottom sheet that lists every actor on an aggregated
 * notification row. Hosted by [net.kikin.nubecita.feature.notifications.impl.NotificationsScreen]
 * when a [net.kikin.nubecita.feature.notifications.impl.NotificationsEffect.ShowActorList]
 * effect lands.
 *
 * Tapping an actor row dispatches [onActorClick] — the host is expected
 * to dismiss the sheet and push the actor's `Profile(handle = did)`
 * onto the active tab's back stack.
 *
 * Layoutlib doesn't render `ModalBottomSheet` reliably in previews —
 * this composable's pixel baseline is captured indirectly via the
 * full-screen `NotificationsScreen` preview (the screen-level fixture
 * stages a sheet-open variant by passing a non-null `actorListSheetActors`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActorListSheet(
    actors: ImmutableList<AuthorUi>,
    onActorClick: (AuthorUi) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(NotificationsTestTags.ACTOR_LIST_SHEET),
    ) {
        LazyColumn {
            items(actors, key = { it.did }) { actor ->
                NotificationActorRow(
                    actor = actor,
                    onClick = { onActorClick(actor) },
                )
            }
        }
    }
}
