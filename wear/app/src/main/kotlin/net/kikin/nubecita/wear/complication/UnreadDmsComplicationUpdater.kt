package net.kikin.nubecita.wear.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dagger.hilt.android.qualifiers.ApplicationContext
import net.kikin.nubecita.wear.data.sync.DmSyncObserver
import javax.inject.Inject

/** Refreshes every placed unread-DMs complication when synced DMs change. */
internal class UnreadDmsComplicationUpdater
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DmSyncObserver {
        override fun onDmsChanged() {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, UnreadDmsComplicationService::class.java))
                .requestUpdateAll()
        }
    }
