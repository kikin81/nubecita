package net.kikin.nubecita.core.wearcontract

import java.util.Base64

/**
 * Wear Data Layer paths shared by the phone and the watch.
 *
 * Every path lives under [PREFIX] so each app's `WearableListenerService` can
 * filter on `pathPrefix="/v1/"` and never wake for unrelated traffic. Snapshot
 * and action paths are scoped by account DID, so a sign-out (or an account
 * switch) can delete exactly one account's items.
 */
object WearPaths {
    /** Common prefix of every Nubecita Data Layer path. */
    const val PREFIX = "/v1/"

    /** Signed-in account of the phone app, or signed out. Not account-scoped. */
    const val SESSION = "/v1/session"

    /** Message the watch sends when its app opens, asking the phone to republish. */
    const val SYNC_REQUEST = "/v1/sync/request"

    /** Root of everything stored for [accountDid] — the prefix to delete on sign-out. */
    fun account(accountDid: String): String = "${PREFIX}${accountDid.requireSegment()}/"

    /** One synced feed's snapshot. [feedKey] comes from [WearFeedKey]. */
    fun feed(
        accountDid: String,
        feedKey: String,
    ): String = "${account(accountDid)}feed/${feedKey.requireSegment()}"

    /** The account's DM snapshot. */
    fun dms(accountDid: String): String = "${account(accountDid)}dms"

    /** An action the watch queued, keyed by its client-generated id. */
    fun action(
        accountDid: String,
        actionId: String,
    ): String = "${account(accountDid)}action/${actionId.requireSegment()}"

    /** The phone's result for the action with [actionId]. */
    fun actionResult(
        accountDid: String,
        actionId: String,
    ): String = "${account(accountDid)}action-result/${actionId.requireSegment()}"

    private fun String.requireSegment(): String {
        require(isNotEmpty() && '/' !in this) { "not a single path segment: '$this'" }
        return this
    }
}

/**
 * Stable, path-safe key for a synced feed.
 *
 * Following has no feed URI, so it gets the fixed key [FOLLOWING]. Every other
 * feed (custom generator, list) is keyed by its AT-URI, URL-safe Base64
 * encoded so the slashes in `at://…` can't split the Data Layer path.
 */
object WearFeedKey {
    const val FOLLOWING = "following"

    private const val URI_PREFIX = "uri-"

    fun forFeedUri(feedUri: String): String {
        require(feedUri.isNotEmpty()) { "feed URI is empty" }
        return URI_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(feedUri.encodeToByteArray())
    }

    /** The AT-URI behind a key from [forFeedUri], or null for [FOLLOWING] / a malformed key. */
    fun feedUriOrNull(feedKey: String): String? {
        if (!feedKey.startsWith(URI_PREFIX)) return null
        return runCatching { Base64.getUrlDecoder().decode(feedKey.removePrefix(URI_PREFIX)).decodeToString() }.getOrNull()
    }
}

/**
 * Wear Data Layer capability names, declared in each app's
 * `res/values/wear.xml` (`android_wear_capabilities`).
 */
object WearCapabilities {
    /** Advertised by the Wear app. The phone publishes only while a node has it. */
    const val WEAR_APP = "nubecita_wear"

    /** Advertised by the phone app. The watch uses it to detect a missing phone app. */
    const val PHONE_APP = "nubecita_phone"
}
