package net.kikin.nubecita.feature.notifications.impl.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.feature.notifications.impl.NotificationsTestTags

/**
 * Modal bottom sheet that lists every actor on an aggregated
 * notification row. Hosted by [net.kikin.nubecita.feature.notifications.impl.NotificationsScreen]
 * when `NotificationsState.actorListSheet` is non-null (driven by the
 * `AvatarStackTapped` reducer; cleared by `SheetDismissed`).
 *
 * Tapping an actor row dispatches [onActorClick] — the host dispatches
 * `SheetDismissed` and pushes the actor's `Profile(handle = did)`
 * onto the active tab's back stack.
 *
 * Layoutlib doesn't render `ModalBottomSheet` reliably in previews so
 * this composable has no pixel-baseline coverage today. The screen-level
 * screenshot fixtures drive `NotificationsContent`, which renders the
 * sheet's host but not the sheet itself (the sheet is composed at the
 * Root layer alongside `NotificationsContent`). When layoutlib gains
 * `ModalBottomSheet` support, add a dedicated `@PreviewTest` fixture
 * that drives this composable directly with a fixture actor list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActorListSheet(
    actors: ImmutableList<AuthorUi>,
    onActorClick: (AuthorUi) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
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
