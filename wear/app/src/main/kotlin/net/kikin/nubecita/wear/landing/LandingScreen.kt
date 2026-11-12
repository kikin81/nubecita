package net.kikin.nubecita.wear.landing

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.ButtonGroupScope
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import net.kikin.nubecita.wear.R

/**
 * The watch's start screen: three equal, tall buttons side by side — Home (the
 * synced feed), Messages and New post — in an expressive [ButtonGroup], so the
 * pressed button widens and its neighbours give way.
 *
 * Messages sits in the middle in the contrasting variant colour and shape, and
 * shows the unread count under its icon. A button whose surface isn't ready
 * (DMs not synced yet, posting unavailable) stays in place but disabled, so the
 * three keep their positions. The screen never scrolls: a one-line title and
 * icon-only buttons fit the smallest round watch at the largest font.
 */
@Composable
fun LandingScreen(
    onOpenFeed: () -> Unit,
    modifier: Modifier = Modifier,
    unreadMessages: Int = 0,
    onOpenMessages: (() -> Unit)? = null,
    onNewPost: (() -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current
    // Fractions of the screen, per Wear guidance, so it scales with the watch:
    // the group spans the circle's chord just below the centre.
    val groupHeight = (configuration.screenHeightDp * GROUP_HEIGHT_FRACTION).dp
    val sideInset = (configuration.screenWidthDp * GROUP_SIDE_INSET_FRACTION).dp
    ScreenScaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            ButtonGroup(modifier = Modifier.fillMaxWidth().padding(horizontal = sideInset).height(groupHeight)) {
                LandingButton(
                    iconRes = R.drawable.ic_home,
                    description = stringResource(R.string.wear_landing_home),
                    onClick = onOpenFeed,
                    colors = ButtonDefaults.buttonColors(),
                    shape = PillShape,
                )
                LandingButton(
                    iconRes = if (unreadMessages > 0) R.drawable.ic_messages_unread else R.drawable.ic_messages,
                    description =
                        if (unreadMessages > 0) {
                            stringResource(R.string.wear_dms_open_unread, unreadMessages)
                        } else {
                            stringResource(R.string.wear_dms_open)
                        },
                    onClick = onOpenMessages,
                    colors = ButtonDefaults.filledVariantButtonColors(),
                    shape = MaterialTheme.shapes.medium,
                    badge = unreadMessages.takeIf { it > 0 }?.toString(),
                )
                LandingButton(
                    iconRes = R.drawable.ic_edit,
                    description = stringResource(R.string.wear_post_new),
                    onClick = onNewPost,
                    colors = ButtonDefaults.buttonColors(),
                    shape = PillShape,
                )
            }
        }
    }
}

/**
 * One tall, icon-only segment of the group. A null [onClick] disables it. The
 * icon carries the spoken label; [badge] (the unread count) is visual only,
 * since [description] already says it.
 */
@Composable
private fun ButtonGroupScope.LandingButton(
    @DrawableRes iconRes: Int,
    description: String,
    onClick: (() -> Unit)?,
    colors: ButtonColors,
    shape: Shape,
    badge: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick ?: {},
        modifier = Modifier.weight(1f).fillMaxHeight().animateWidth(interactionSource),
        enabled = onClick != null,
        shape = shape,
        colors = colors,
        // Narrow segments: no side padding, so the icon never gets squeezed.
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = description,
                    modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                )
                if (badge != null) {
                    Text(badge, modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

// Fully rounded ends, like a stadium standing on its end.
private val PillShape = RoundedCornerShape(percent = 50)

private const val GROUP_HEIGHT_FRACTION = 0.4f

// Keeps the outer segments inside the circle at the group's height.
private const val GROUP_SIDE_INSET_FRACTION = 0.08f
