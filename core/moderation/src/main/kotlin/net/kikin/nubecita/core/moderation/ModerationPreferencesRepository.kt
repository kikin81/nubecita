package net.kikin.nubecita.core.moderation

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.AdultContentPref
import io.github.kikin81.atproto.app.bsky.actor.ContentLabelPref
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesRequest
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequest
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kikin.nubecita.core.auth.XrpcClientProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The viewer's content-filter preferences, as a single reactive source of
 * truth. Feeds, search, and post-detail all gate on [prefs]; the Settings
 * content-filters screen drives the two mutators.
 *
 * [prefs] starts at [ModerationPrefs.DEFAULT] (adult content **off**) so any
 * reader that observes before the first [refresh] completes fails safe — adult
 * media is hidden, never shown, on a cold cache.
 */
interface ModerationPreferencesRepository {
    /**
     * Hot stream of the resolved preferences. Seeded with
     * [ModerationPrefs.DEFAULT] and updated by [refresh] and the mutators.
     */
    val prefs: StateFlow<ModerationPrefs>

    /** Re-read `app.bsky.actor.getPreferences` and publish to [prefs]. */
    suspend fun refresh()

    /**
     * Reset [prefs] back to the fail-safe [ModerationPrefs.DEFAULT] (adult
     * content **off**). Called on sign-out so a subsequent account never reads
     * the previous account's preferences in the window before its own [refresh]
     * completes — without this, account A's "adult on" would briefly leak to
     * account B (the repo is an app-scoped singleton that outlives the session).
     */
    fun resetToDefault()

    /** Toggle the adult-content master gate (read-modify-write of the array). */
    suspend fun setAdultContentEnabled(enabled: Boolean)

    /** Set one category's visibility (read-modify-write of the array). */
    suspend fun setVisibility(
        label: ContentLabel,
        visibility: LabelVisibility,
    )
}

/**
 * Default implementation backed by the typed `app.bsky.actor.getPreferences` /
 * `putPreferences` array (the `preferences` field became a proper typed
 * `List<union>` in atproto-kotlin 9.2.0; see issue #132). Mutations
 * read-modify-write the WHOLE array so foreign preference entries (saved feeds,
 * labeler-scoped label prefs, interests, unmodeled future kinds carried as the
 * union's `Unknown(type, raw)` member) are preserved untouched — only the global
 * adult-gate and the four global content-label prefs we own are replaced.
 */
@Singleton
internal class DefaultModerationPreferencesRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
    ) : ModerationPreferencesRepository {
        // Serializes the read-modify-write in [update] (and [refresh]'s publish)
        // so concurrent setters can't clobber each other with a stale read.
        private val writeMutex = Mutex()

        private val _prefs = MutableStateFlow(ModerationPrefs.DEFAULT)
        override val prefs: StateFlow<ModerationPrefs> = _prefs.asStateFlow()

        override suspend fun refresh() {
            val parsed = parseModerationPrefs(fetchPreferences())
            writeMutex.withLock { _prefs.value = parsed }
        }

        // A plain StateFlow write — atomic and last-write-wins. The coordinator
        // calls this on SignedOut, after collectLatest has already cancelled any
        // in-flight refresh, so there is no concurrent publish to order against;
        // the next account's refresh re-publishes over this DEFAULT.
        override fun resetToDefault() {
            _prefs.value = ModerationPrefs.DEFAULT
        }

        override suspend fun setAdultContentEnabled(enabled: Boolean) = update { it.copy(adultContentEnabled = enabled) }

        override suspend fun setVisibility(
            label: ContentLabel,
            visibility: LabelVisibility,
        ) = update { it.copy(visibilities = it.visibilities + (label to visibility)) }

        /**
         * Optimistic read-modify-write.
         *
         * Publishes the transformed value to [prefs] IMMEDIATELY — before any
         * network — so the Content filters screen (a pure projection of [prefs])
         * reacts on the next frame instead of waiting ~seconds on the
         * `putPreferences` round-trip (nubecita-twmt.6). Then, under [writeMutex],
         * re-reads the live array (so a change made elsewhere since the last
         * refresh isn't clobbered), applies [transform] to the authoritative
         * server state, writes the merged array back, and republishes that
         * reconciled value (usually identical to the optimistic one).
         *
         * On a real failure it rolls [prefs] back to the prior value — unless a
         * later optimistic change already superseded ours — and rethrows so the
         * caller (the VM) can surface a save-error snackbar over a UI that has
         * already snapped back. Cancellation leaves the optimistic value; the
         * next [refresh] reconciles.
         */
        private suspend fun update(transform: (ModerationPrefs) -> ModerationPrefs) {
            val previous = _prefs.value
            val optimistic = transform(previous)
            _prefs.value = optimistic
            try {
                writeMutex.withLock {
                    val original = fetchPreferences()
                    val reconciled = transform(parseModerationPrefs(original))
                    writePreferences(mergeModerationPrefs(original, reconciled))
                    _prefs.value = reconciled
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (_prefs.value == optimistic) _prefs.value = previous
                throw throwable
            }
        }

        private suspend fun fetchPreferences(): List<GetPreferencesResponsePreferencesUnion> = ActorService(xrpcClientProvider.authenticated()).getPreferences(GetPreferencesRequest()).preferences

        private suspend fun writePreferences(preferences: List<PutPreferencesRequestPreferencesUnion>) {
            ActorService(xrpcClientProvider.authenticated()).putPreferences(PutPreferencesRequest(preferences))
        }
    }

/**
 * Pure projection of the typed `preferences` list into [ModerationPrefs]. Reads
 * the global [AdultContentPref] `enabled` (default `false` when absent) and each
 * GLOBAL [ContentLabelPref] (no `labelerDid`) whose label is one of our four
 * managed categories. Labeler-scoped prefs and unknown categories are ignored;
 * categories with no entry fall back to [ModerationPrefs.DEFAULT] via
 * [ModerationPrefs.visibilityFor]. No I/O — unit-tested in isolation.
 */
internal fun parseModerationPrefs(preferences: List<GetPreferencesResponsePreferencesUnion>): ModerationPrefs {
    // last-wins on the (server-singleton) adult gate — matches the prior loop.
    val adultEnabled = preferences.filterIsInstance<AdultContentPref>().lastOrNull()?.enabled ?: false

    val visibilities = mutableMapOf<ContentLabel, LabelVisibility>()
    preferences
        .filterIsInstance<ContentLabelPref>()
        .filter { it.labelerDid == null } // only global prefs configure our gate
        .forEach { pref ->
            val category = ContentLabel.fromValue(pref.label) ?: return@forEach
            val visibility = LabelVisibility.fromWire(pref.visibility) ?: return@forEach
            visibilities[category] = visibility
        }

    return ModerationPrefs(adultContentEnabled = adultEnabled, visibilities = visibilities)
}

/**
 * Pure merge: produce a new `preferences` list that drops the entries we own
 * (the global [AdultContentPref] and the global [ContentLabelPref]s for our four
 * managed labels) from [original] while preserving every other entry in place
 * (known members pass through; `Unknown` members are remapped verbatim), then
 * appends fresh entries reflecting [prefs]. All four content-label prefs are
 * written explicitly so the stored set is deterministic. No I/O.
 */
internal fun mergeModerationPrefs(
    original: List<GetPreferencesResponsePreferencesUnion>,
    prefs: ModerationPrefs,
): List<PutPreferencesRequestPreferencesUnion> {
    val managedLabels = ContentLabel.entries.map { it.value }.toSet()
    val preserved =
        original
            .filterNot { member ->
                when (member) {
                    is AdultContentPref -> true
                    is ContentLabelPref -> member.labelerDid == null && member.label in managedLabels
                    else -> false
                }
            }.map { it.asPutPreference() }

    val owned =
        buildList<PutPreferencesRequestPreferencesUnion> {
            add(AdultContentPref(prefs.adultContentEnabled))
            ContentLabel.entries.forEach { category ->
                add(ContentLabelPref(label = category.value, visibility = prefs.visibilityFor(category).wireValue))
            }
        }

    return preserved + owned
}
