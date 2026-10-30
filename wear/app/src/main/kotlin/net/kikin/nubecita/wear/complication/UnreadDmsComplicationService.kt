package net.kikin.nubecita.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dagger.hilt.android.AndroidEntryPoint
import net.kikin.nubecita.wear.MainActivity
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.data.WatchDmRepository
import javax.inject.Inject

/**
 * The unread-DMs complication (W5): the unread total from the last DM snapshot
 * the phone synced, as SHORT_TEXT (count + icon), or as an icon alone
 * (MONOCHROMATIC_IMAGE / SMALL_IMAGE) that gains a dot while anything is
 * unread — an image slot can't carry a number.
 *
 * Push-only (`UPDATE_PERIOD_SECONDS = 0` in the manifest): the system never
 * polls it; [UnreadDmsComplicationUpdater] asks for an update when synced DMs
 * change. Before DMs ever sync (signed out, or message checking off on the
 * phone) it answers no data. Tapping opens the conversation list.
 */
@AndroidEntryPoint
class UnreadDmsComplicationService : SuspendingComplicationDataSourceService() {
    @Inject
    lateinit var repository: WatchDmRepository

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val unread = repository.latestUnreadCount() ?: return NoDataComplicationData()
        return unreadDmsData(this, request.complicationType, unread, openMessagesIntent(this)) ?: NoDataComplicationData()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = unreadDmsData(this, type, PREVIEW_UNREAD, tapAction = null)

    private companion object {
        const val PREVIEW_UNREAD = 3
    }
}

internal fun unreadDmsLabel(unread: Int): String = if (unread > MAX_SHOWN) "$MAX_SHOWN+" else unread.coerceAtLeast(0).toString()

internal fun unreadDmsIcon(unread: Int): Int = if (unread > 0) R.drawable.ic_messages_unread else R.drawable.ic_messages

private const val MAX_SHOWN = 99

private fun unreadDmsData(
    context: Context,
    type: ComplicationType,
    unread: Int,
    tapAction: PendingIntent?,
): ComplicationData? {
    val description =
        PlainComplicationText
            .Builder(
                if (unread > 0) {
                    context.resources.getQuantityString(R.plurals.wear_complication_unread_dms, unread, unread)
                } else {
                    context.getString(R.string.wear_complication_no_unread_dms)
                },
            ).build()
    val icon = Icon.createWithResource(context, unreadDmsIcon(unread))
    return when (type) {
        ComplicationType.SHORT_TEXT ->
            ShortTextComplicationData
                .Builder(PlainComplicationText.Builder(unreadDmsLabel(unread)).build(), description)
                .setMonochromaticImage(MonochromaticImage.Builder(icon).build())
                .setTapAction(tapAction)
                .build()
        ComplicationType.MONOCHROMATIC_IMAGE ->
            MonochromaticImageComplicationData
                .Builder(MonochromaticImage.Builder(icon).build(), description)
                .setTapAction(tapAction)
                .build()
        ComplicationType.SMALL_IMAGE ->
            SmallImageComplicationData
                .Builder(SmallImage.Builder(icon, SmallImageType.ICON).build(), description)
                .setTapAction(tapAction)
                .build()
        else -> null
    }
}

private fun openMessagesIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_MESSAGES)
            // A fresh task: the tap means "show my messages", not "resume where I was".
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
