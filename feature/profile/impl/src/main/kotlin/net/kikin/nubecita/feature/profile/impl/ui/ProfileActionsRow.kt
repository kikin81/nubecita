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
 * own-profile only; [onFollow] / [onMessage] / [onOverflowAction] /
 * [onReport] are other-user only.
 *
 * [onReport] dispatches the graduated Report row (oftc.3) — the other
 * `StubbedAction`s still flow through [onOverflowAction] until their
 * moderation children land (oftc.4 / oftc.5).
 */
@Composable
internal fun ProfileActionsRow(
    ownProfile: Boolean,
    viewerRelationship: ViewerRelationship,
    canMessage: Boolean,
    onEdit: () -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
    onReport: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ownProfile) {
        OwnProfileActionsRow(onEdit = onEdit, onSettings = onSettings, modifier = modifier)
    } else {
        OtherUserActionsRow(
            viewerRelationship = viewerRelationship,
            canMessage = canMessage,
            onFollow = onFollow,
            onMessage = onMessage,
            onOverflowAction = onOverflowAction,
            onReport = onReport,
            modifier = modifier,
        )
    }
}
