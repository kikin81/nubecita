package net.kikin.nubecita.wear.data.sync

/**
 * Told after the synced DM data may have changed: a new snapshot stored, the
 * snapshot deleted, or the session cleared it. Lets glanceable surfaces (the
 * unread-DMs complication) refresh exactly when there is something new, with
 * no polling. Contributed `@IntoSet` by the Wear app.
 */
fun interface DmSyncObserver {
    fun onDmsChanged()
}
