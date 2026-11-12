@file:SuppressLint("RestrictedApi") // Remote Compose and Glance Wear APIs are LIBRARY_GROUP-restricted in their alphas.

package net.kikin.nubecita.wear.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.remote.creation.compose.action.pendingIntentAction
import androidx.compose.remote.creation.compose.capture.RemoteImageVector
import androidx.compose.remote.creation.compose.capture.vectorResource
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.fillMaxHeight
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.WearWidgetBrush
import androidx.glance.wear.WearWidgetData
import androidx.glance.wear.WearWidgetDocument
import androidx.glance.wear.color
import androidx.glance.wear.core.WearWidgetParams
import androidx.wear.compose.remote.material3.RemoteIcon
import androidx.wear.compose.remote.material3.RemoteMaterialTheme
import androidx.wear.compose.remote.material3.RemoteText
import androidx.wear.compose.remote.material3.dynamicRemoteColorScheme
import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.launch.WearLaunchTarget
import net.kikin.nubecita.wear.launch.launchIntent
import net.kikin.nubecita.wear.ui.theme.NubecitaWearColors
import timber.log.Timber

/**
 * The Nubecita launcher (nubecita-hzfs): a Messages card with the synced unread
 * count beside Home and New post pills, each opening the app there. Shown as a
 * large widget card where partial-height widgets are supported and inside a
 * tile elsewhere (Pixel Watch, Wear OS 4-6), whose frame the host draws with the
 * app's name. Rendered on each update request from [unreadCount]; it never
 * refreshes on a timer.
 */
class LauncherWidget(
    private val signedIn: suspend () -> Boolean,
    private val unreadCount: suspend () -> Int?,
) : GlanceWearWidget() {
    override suspend fun provideWidgetData(
        context: Context,
        params: WearWidgetParams,
    ): WearWidgetData {
        // The service's scope has no exception handler: a failed read must not crash the
        // watch app. Unknown reads as signed in with no count — the plain Messages card.
        val model =
            launcherWidgetModel(
                signedIn = readOr(fallback = true, read = signedIn),
                unread = readOr(fallback = null, read = unreadCount),
            )
        val labels = LauncherLabels.from(context, model)
        return WearWidgetDocument(background = WearWidgetBrush.color(Color.Black.rc)) {
            LauncherWidgetContent(
                model = model,
                labels = labels,
            )
        }
    }
}

private suspend fun <T> readOr(
    fallback: T,
    read: suspend () -> T,
): T =
    try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Timber.w(error, "Launcher widget read failed; rendering the fallback")
        fallback
    }

/** Every string the layout shows, resolved up front (the document is rendered by the host). */
internal data class LauncherLabels(
    val messagesHeadline: String,
    val messagesCaption: String?,
    val messagesDescription: String,
    val home: String,
    val newPost: String,
    val signIn: String,
) {
    companion object {
        fun from(
            context: Context,
            model: LauncherWidgetModel,
        ): LauncherLabels {
            val res = context.resources
            val count = model.countLabel
            return LauncherLabels(
                messagesHeadline =
                    when {
                        count == null -> context.getString(R.string.wear_dms_open)
                        model.hasUnread -> count
                        else -> context.getString(R.string.wear_widget_no_new_messages)
                    },
                messagesCaption =
                    if (count != null && model.hasUnread) {
                        // "99+" reads as many.
                        res.getQuantityString(R.plurals.wear_widget_new_messages, count.toIntOrNull() ?: Int.MAX_VALUE)
                    } else {
                        null
                    },
                messagesDescription =
                    if (count != null && model.hasUnread) {
                        context.getString(R.string.wear_widget_messages_unread, count)
                    } else {
                        context.getString(R.string.wear_dms_open)
                    },
                home = context.getString(R.string.wear_landing_home),
                newPost = context.getString(R.string.wear_post_new),
                signIn = context.getString(R.string.wear_gate_signed_out_title),
            )
        }
    }
}

@RemoteComposable
@Composable
internal fun LauncherWidgetContent(
    model: LauncherWidgetModel,
    labels: LauncherLabels,
) {
    RemoteMaterialTheme(colorScheme = dynamicRemoteColorScheme(NubecitaWearColors)) {
        RemoteColumn(
            // The host insets the document by the params' padding itself.
            modifier = RemoteModifier.fillMaxSize(),
            horizontalAlignment = RemoteAlignment.CenterHorizontally,
        ) {
            // Gaps are padding on the weighted items, not spacedBy: with spacedBy, a
            // Wear OS 7 emulator's host clipped the pills' outer edges (the weights
            // didn't leave room for the gaps); with padding they render whole.
            if (!model.signedIn) {
                SignInCard(labels.signIn, RemoteModifier.fillMaxWidth().weight(1f))
                return@RemoteColumn
            }
            RemoteRow(modifier = RemoteModifier.fillMaxWidth().weight(1f)) {
                MessagesCard(model, labels, RemoteModifier.weight(MESSAGES_WEIGHT).fillMaxHeight())
                RemoteColumn(modifier = RemoteModifier.weight(1f).fillMaxHeight().padding(start = GAP.rdp)) {
                    Pill(R.drawable.ic_home, labels.home, WearLaunchTarget.Feed, RemoteModifier.weight(1f).fillMaxWidth().padding(bottom = (GAP / 2).rdp))
                    Pill(R.drawable.ic_edit, labels.newPost, WearLaunchTarget.NewPost, RemoteModifier.weight(1f).fillMaxWidth().padding(top = (GAP / 2).rdp))
                }
            }
        }
    }
}

// The tiles are plain clipped boxes, not RemoteButtons: a RemoteButton's 52dp
// minimum height is taller than each pill gets in the host's ~112dp slot, while
// a box takes exactly the space the row gives it.

@RemoteComposable
@Composable
private fun MessagesCard(
    model: LauncherWidgetModel,
    labels: LauncherLabels,
    modifier: RemoteModifier,
) {
    val colors = RemoteMaterialTheme.colorScheme
    RemoteBox(
        modifier =
            modifier
                .clip(RemoteMaterialTheme.shapes.large)
                .background(colors.primaryContainer)
                .clickable(pendingIntentAction { ctx -> launchIntent(ctx, WearLaunchTarget.Messages) }),
        contentAlignment = RemoteAlignment.CenterStart,
    ) {
        // Inset the text, not the card, so the card's colour fills its whole slot.
        RemoteColumn(modifier = RemoteModifier.padding(horizontal = CARD_HORIZONTAL_PADDING.rdp)) {
            RemoteIcon(
                imageVector = RemoteImageVector.vectorResource(if (model.hasUnread) R.drawable.ic_messages_unread else R.drawable.ic_messages),
                contentDescription = labels.messagesDescription.rs,
                modifier = RemoteModifier.size(ICON_SIZE.rdp),
                tint = colors.onPrimaryContainer,
            )
            RemoteText(
                text = labels.messagesHeadline.rs,
                color = colors.onPrimaryContainer,
                style = if (model.hasUnread) RemoteMaterialTheme.typography.displaySmall else RemoteMaterialTheme.typography.labelSmall,
                // "No new messages" wraps; a count is one line.
                maxLines = if (model.hasUnread) 1 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            labels.messagesCaption?.let { caption ->
                RemoteText(
                    text = caption.rs,
                    color = colors.onPrimaryContainer,
                    style = RemoteMaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Signed out (WO-V9): one card prompting sign-in; the app's gate walks through it. */
@RemoteComposable
@Composable
private fun SignInCard(
    label: String,
    modifier: RemoteModifier,
) {
    val colors = RemoteMaterialTheme.colorScheme
    RemoteBox(
        modifier =
            modifier
                .clip(RemoteMaterialTheme.shapes.large)
                .background(colors.primaryContainer)
                .clickable(pendingIntentAction { ctx -> launchIntent(ctx, WearLaunchTarget.Landing) }),
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteText(
            text = label.rs,
            modifier = RemoteModifier.padding(horizontal = CARD_HORIZONTAL_PADDING.rdp),
            color = colors.onPrimaryContainer,
            style = RemoteMaterialTheme.typography.titleSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@RemoteComposable
@Composable
private fun Pill(
    icon: Int,
    label: String,
    target: WearLaunchTarget,
    modifier: RemoteModifier,
) {
    val colors = RemoteMaterialTheme.colorScheme
    // Icon only: the pills are too narrow for a label beside the icon on a round screen.
    RemoteBox(
        modifier =
            modifier
                .clip(RemoteMaterialTheme.shapes.large)
                .background(colors.primary)
                .clickable(pendingIntentAction { ctx -> launchIntent(ctx, target) }),
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteIcon(
            imageVector = RemoteImageVector.vectorResource(icon),
            contentDescription = label.rs,
            modifier = RemoteModifier.size(ICON_SIZE.rdp),
            tint = colors.onPrimary,
        )
    }
}

private const val GAP = 4
private const val CARD_HORIZONTAL_PADDING = 10
private const val ICON_SIZE = 24
private const val MESSAGES_WEIGHT = 1.4f
