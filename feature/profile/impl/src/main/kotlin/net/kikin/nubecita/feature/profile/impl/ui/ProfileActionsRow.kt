package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kikin.nubecita.feature.profile.impl.StubbedAction
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship

/**
 * Hero actions row dispatcher. Delegates to [OwnProfileActionsRow] or
 * [OtherUserActionsRow] based on [ownProfile].
 *
 * The unified callback surface here lets `ProfileHero` stay
 * variant-ignorant: it forwards the full set, and only the picked
 * variant uses the relevant subset. [onEdit] / [onSettings] are
 * own-profile only; [onFollow] / [onMessage] / [onOverflowAction]
 * are other-user only.
 *
 * Real Follow / Edit / Message / Block / Mute / Report writes ship
 * under separate follow-up bd issues (7.3 / 7.4 / 7.5 / 7.7).
 */
@Composable
internal fun ProfileActionsRow(
    ownProfile: Boolean,
    viewerRelationship: ViewerRelationship,
    onEdit: () -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ownProfile) {
        OwnProfileActionsRow(onEdit = onEdit, onSettings = onSettings, modifier = modifier)
    } else {
        OtherUserActionsRow(
            viewerRelationship = viewerRelationship,
            onFollow = onFollow,
            onMessage = onMessage,
            onOverflowAction = onOverflowAction,
            modifier = modifier,
        )
    }
}
