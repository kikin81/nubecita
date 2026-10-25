package net.kikin.nubecita.core.analytics

/**
 * Intent-extra contract that lets a notification's tap intent tell
 * `MainActivity` it came from a notification, so it can log [NotificationTap].
 *
 * The notification builders put [SOURCE] and [TYPE] (the enums' `wire`
 * values) on the tap intent; the activity reads them back with [parse].
 */
object NotificationTapExtras {
    const val SOURCE: String = "net.kikin.nubecita.extra.NOTIFICATION_SOURCE"
    const val TYPE: String = "net.kikin.nubecita.extra.NOTIFICATION_TYPE"

    /**
     * The tap event for these extras, or `null` when there's no recognised
     * source. `MainActivity` is exported, so the extras are untrusted: only
     * known wire values become an event, and an unknown type reads as
     * [NotificationType.Other].
     */
    fun parse(
        source: String?,
        type: String?,
    ): NotificationTap? {
        val tapSource = NotificationSource.entries.firstOrNull { it.wire == source } ?: return null
        val tapType = NotificationType.entries.firstOrNull { it.wire == type } ?: NotificationType.Other
        return NotificationTap(source = tapSource, type = tapType)
    }
}
