package net.kikin.nubecita.wear.bench

import kotlinx.coroutines.flow.MutableStateFlow
import net.kikin.nubecita.core.wearcontract.WearAccount
import net.kikin.nubecita.wear.data.PhoneAppStatus
import net.kikin.nubecita.wear.data.PhoneSession

/**
 * A named state the bench build can be put into without a phone, so gate,
 * empty and failure screens are reachable on a device and in journeys.
 *
 * Set from the launch intent:
 * `am start -n net.kikin.nubecita/net.kikin.nubecita.wear.MainActivity --es scenario signed_out`.
 * It stays for the life of the process, so the launcher widget and the
 * complication show the same state; launch with `default` (or force-stop) to
 * reset.
 */
internal enum class BenchScenario(
    val wire: String,
) {
    /** Signed in, phone app present, everything supported and instant. */
    Default("default"),
    SignedOut("signed_out"),
    PhoneAppMissing("phone_app_missing"),
    IPhone("iphone"),
    UpdateRequired("update_required"),
    NoFeeds("no_feeds"),
    DmsNotSynced("dms_not_synced"),
    NoConversations("no_conversations"),

    /** The phone runs no likes, reposts or bookmarks: the controls are hidden. */
    NoActions("no_actions"),

    /** The phone doesn't post from the watch: New post is disabled. */
    NoPosting("no_posting"),

    /** A new post ends as "Not posted", and so does every retry. */
    PostFails("post_fails"),

    /** A reply or reaction ends as "Not sent", and so does every retry. */
    DmFails("dm_fails"),

    /** Likes, reposts, bookmarks, replies and reactions stay pending for [SLOW_MS]. */
    SlowActions("slow_actions"),
    ;

    companion object {
        /** The launch extra that selects a scenario. */
        const val EXTRA = "scenario"

        /** How long a slow action stays pending: long enough for a journey step to see it. */
        const val SLOW_MS = 15_000L

        /** How long a failing reply shows as sending before it fails. */
        const val FAIL_MS = 2_000L

        /**
         * The scenario named [wire]. An unknown name throws: falling back to
         * the default would let a journey with a typo pass on the happy path.
         */
        fun of(wire: String): BenchScenario =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown bench scenario '$wire'. Valid: ${entries.joinToString { it.wire }}")
    }
}

/** The process-wide scenario the bench fakes read. */
internal object BenchScenarios {
    val current = MutableStateFlow(BenchScenario.Default)
}

internal val BENCH_ACCOUNT = WearAccount(did = "did:plc:nubecitabench", handle = "nubecita.bsky.social")

/** The phone session a scenario stands for. */
internal fun BenchScenario.session(): PhoneSession =
    when (this) {
        BenchScenario.SignedOut -> PhoneSession.SignedOut
        // The gate only asks about the phone app when no session was ever received.
        BenchScenario.PhoneAppMissing, BenchScenario.IPhone -> PhoneSession.Unknown
        BenchScenario.UpdateRequired -> PhoneSession.UpdateRequired
        else -> PhoneSession.SignedIn(BENCH_ACCOUNT)
    }

/** What the paired phone looks like in a scenario. */
internal fun BenchScenario.phoneAppStatus(): PhoneAppStatus =
    when (this) {
        BenchScenario.PhoneAppMissing -> PhoneAppStatus.MissingOnAndroid
        BenchScenario.IPhone -> PhoneAppStatus.IPhone
        else -> PhoneAppStatus.Installed
    }
