package net.kikin.nubecita.wear.feed

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogContent
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.Text
import net.kikin.nubecita.wear.R

/**
 * Like, repost and bookmark for one post. A toggle whose last action has no
 * answer from the phone yet is disabled, so a double tap can't queue twice.
 * Reposting asks first (it's public, and watch taps misfire); undoing a
 * repost, liking and bookmarking take one tap.
 */
@Composable
fun PostActionRow(
    post: WatchPostUi,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRepost by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        // Small (48dp) buttons: as the list's last item the row sits near the
        // bottom edge, where a small round screen is too narrow for three
        // default-size ones.
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        ActionToggle(
            checked = post.liked,
            enabled = !post.likePending,
            onClick = onToggleLike,
            iconOn = R.drawable.ic_action_liked,
            iconOff = R.drawable.ic_action_like,
            label = R.string.wear_post_action_like,
            stateOn = R.string.wear_post_state_liked,
            stateOff = R.string.wear_post_state_not_liked,
        )
        ActionToggle(
            checked = post.reposted,
            enabled = !post.repostPending,
            onClick = { if (post.reposted) onToggleRepost() else confirmRepost = true },
            iconOn = R.drawable.ic_action_repost,
            iconOff = R.drawable.ic_action_repost,
            label = R.string.wear_post_action_repost,
            stateOn = R.string.wear_post_state_reposted,
            stateOff = R.string.wear_post_state_not_reposted,
        )
        ActionToggle(
            checked = post.bookmarked,
            enabled = !post.bookmarkPending,
            onClick = onToggleBookmark,
            iconOn = R.drawable.ic_action_bookmarked,
            iconOff = R.drawable.ic_action_bookmark,
            label = R.string.wear_post_action_bookmark,
            stateOn = R.string.wear_post_state_bookmarked,
            stateOff = R.string.wear_post_state_not_bookmarked,
        )
    }
    AlertDialog(
        visible = confirmRepost,
        onDismissRequest = { confirmRepost = false },
        title = { RepostConfirmTitle() },
        text = { RepostConfirmBody() },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    confirmRepost = false
                    onToggleRepost()
                },
            )
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = { confirmRepost = false }) },
    )
}

/** The repost confirmation without its dialog window, for screenshot tests. */
@Composable
internal fun RepostConfirmContent(modifier: Modifier = Modifier) {
    AlertDialogContent(
        modifier = modifier,
        title = { RepostConfirmTitle() },
        text = { RepostConfirmBody() },
        confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = {}) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = {}) },
    )
}

@Composable
private fun RepostConfirmTitle() {
    Text(stringResource(R.string.wear_post_repost_confirm_title), textAlign = TextAlign.Center)
}

@Composable
private fun RepostConfirmBody() {
    Text(stringResource(R.string.wear_post_repost_confirm_body), textAlign = TextAlign.Center)
}

@Composable
private fun ActionToggle(
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    @DrawableRes iconOn: Int,
    @DrawableRes iconOff: Int,
    @StringRes label: Int,
    @StringRes stateOn: Int,
    @StringRes stateOff: Int,
) {
    val state = stringResource(if (checked) stateOn else stateOff)
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shapes = IconToggleButtonDefaults.animatedShapes(),
        modifier = Modifier.size(IconToggleButtonDefaults.SmallSize).semantics { stateDescription = state },
    ) {
        Icon(painter = painterResource(if (checked) iconOn else iconOff), contentDescription = stringResource(label))
    }
}
