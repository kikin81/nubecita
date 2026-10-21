package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.postinteractions.ui.R
import net.kikin.nubecita.designsystem.component.AvatarFallback
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Docked comment pill anchored to the bottom of the comments sheet.
 *
 * Displays `[Avatar] "Add a comment..." [Send]` pinned to bottom with
 * `.navigationBarsPadding()` and accessibility semantics (`Role.Button`,
 * decorative internal icons with `contentDescription = null` to prevent
 * duplicate TalkBack announcements).
 */
@Composable
fun DockedCommentPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    userAvatarUrl: String? = null,
    avatarFallback: AvatarFallback? = null,
    placeholderText: String = stringResource(R.string.video_comments_docked_pill_placeholder),
) {
    val actionLabel = stringResource(R.string.video_comments_docked_pill_action)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = actionLabel,
                                onClick = onClick,
                            ).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NubecitaAvatar(
                        model = userAvatarUrl,
                        contentDescription = null,
                        size = 32.dp,
                        fallback = avatarFallback,
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )

                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        NubecitaIcon(
                            name = NubecitaIconName.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            opticalSize = 18.dp,
                        )
                    }
                }
            }
        }
    }
}
