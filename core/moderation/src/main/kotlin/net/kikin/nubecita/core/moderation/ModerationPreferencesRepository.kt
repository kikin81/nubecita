package net.kikin.nubecita.core.moderation

import io.github.kikin81.atproto.runtime.NoXrpcParams
import io.github.kikin81.atproto.runtime.UnitResponseSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

    /** Toggle the adult-content master gate (read-modify-write of the array). */
    suspend fun setAdultContentEnabled(enabled: Boolean)

    /** Set one category's visibility (read-modify-write of the array). */
    suspend fun setVisibility(
        label: ContentLabel,
        visibility: LabelVisibility,
    )
}

/**
 * Default implementation backed by the AT Protocol preferences array.
 *
 * Both `getPreferences` and `putPreferences` carry `preferences` as an ARRAY of
 * `$type`-tagged objects, but the generated SDK types mis-model the field as a
 * `JsonObject`, so neither typed path can round-trip a real account (see
 * `:core:feeds`'s identical workaround). We therefore decode the raw response
 * object, operate on the `preferences` array ourselves, and write back a
 * hand-built `{"preferences":[…]}` body. Mutations read-modify-write the WHOLE
 * array so foreign preference entries (saved feeds, labeler-scoped label prefs,
 * interests, …) are preserved untouched — only the global adult-gate and the
 * four global content-label prefs we own are replaced.
 */
@Singleton
internal class DefaultModerationPreferencesRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
    ) : ModerationPreferencesRepository {
        private val json = Json { ignoreUnknownKeys = true }

        private val _prefs = MutableStateFlow(ModerationPrefs.DEFAULT)
        override val prefs: StateFlow<ModerationPrefs> = _prefs.asStateFlow()

        override suspend fun refresh() {
            _prefs.value = parseModerationPrefs(fetchPreferencesArray(), json)
        }

        override suspend fun setAdultContentEnabled(enabled: Boolean) = update { it.copy(adultContentEnabled = enabled) }

        override suspend fun setVisibility(
            label: ContentLabel,
            visibility: LabelVisibility,
        ) = update { it.copy(visibilities = it.visibilities + (label to visibility)) }

        /**
         * Read-modify-write: re-read the live array (so we never clobber a
         * change made elsewhere since the last refresh), apply [transform],
         * write the merged array back, then publish the new prefs.
         */
        private suspend fun update(transform: (ModerationPrefs) -> ModerationPrefs) {
            val original = fetchPreferencesArray()
            val updated = transform(parseModerationPrefs(original, json))
            writePreferencesArray(mergeModerationPrefs(original, updated))
            _prefs.value = updated
        }

        private suspend fun fetchPreferencesArray(): JsonArray {
            val response =
                xrpcClientProvider.authenticated().query(
                    GET_PREFERENCES_NSID,
                    NoXrpcParams,
                    NoXrpcParams.serializer(),
                    JsonObject.serializer(),
                )
            return response["preferences"] as? JsonArray ?: JsonArray(emptyList())
        }

        private suspend fun writePreferencesArray(preferences: JsonArray) {
            val body = buildJsonObject { put("preferences", preferences) }
            xrpcClientProvider.authenticated().procedure(
                PUT_PREFERENCES_NSID,
                NoXrpcParams,
                NoXrpcParams.serializer(),
                body,
                JsonObject.serializer(),
                UnitResponseSerializer,
            )
        }
    }

/**
 * Pure projection of the raw `preferences` array into [ModerationPrefs]. Reads
 * the global `adultContentPref.enabled` (default `false` when absent) and each
 * GLOBAL `contentLabelPref` (no `labelerDid`) whose label is one of our four
 * managed categories. Labeler-scoped prefs and unknown categories are ignored;
 * categories with no entry fall back to [ModerationPrefs.DEFAULT] via
 * [ModerationPrefs.visibilityFor]. No I/O — unit-tested in isolation.
 */
internal fun parseModerationPrefs(
    preferences: JsonArray,
    json: Json,
): ModerationPrefs {
    var adultEnabled = false
    val visibilities = mutableMapOf<ContentLabel, LabelVisibility>()

    for (element in preferences) {
        val obj = element as? JsonObject ?: continue
        when (obj["\$type"]?.jsonPrimitive?.content) {
            ADULT_CONTENT_PREF_TYPE ->
                adultEnabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: false

            CONTENT_LABEL_PREF_TYPE -> {
                // Only global prefs (no labelerDid) configure our gate.
                if (obj["labelerDid"] != null && obj["labelerDid"] !is JsonNull) continue
                val category = obj["label"]?.jsonPrimitive?.content?.let(ContentLabel::fromValue) ?: continue
                val visibility = obj["visibility"]?.jsonPrimitive?.content?.let(LabelVisibility::fromWire) ?: continue
                visibilities[category] = visibility
            }
        }
    }
    return ModerationPrefs(adultContentEnabled = adultEnabled, visibilities = visibilities)
}

/**
 * Pure merge: produce a new `preferences` array that drops the entries we own
 * (the global `adultContentPref` and the global `contentLabelPref`s for our
 * four managed labels) from [original] while preserving every other entry in
 * place, then appends fresh entries reflecting [prefs]. All four content-label
 * prefs are written explicitly so the stored set is deterministic. No I/O.
 */
internal fun mergeModerationPrefs(
    original: JsonArray,
    prefs: ModerationPrefs,
): JsonArray {
    val managedLabels = ContentLabel.entries.map { it.value }.toSet()
    val preserved =
        original.filterNot { element ->
            val obj = element as? JsonObject ?: return@filterNot false
            when (obj["\$type"]?.jsonPrimitive?.content) {
                ADULT_CONTENT_PREF_TYPE -> true
                CONTENT_LABEL_PREF_TYPE -> {
                    val isGlobal = obj["labelerDid"] == null || obj["labelerDid"] is JsonNull
                    isGlobal && obj["label"]?.jsonPrimitive?.content in managedLabels
                }
                else -> false
            }
        }

    return buildJsonArray {
        preserved.forEach(::add)
        add(
            buildJsonObject {
                put("\$type", ADULT_CONTENT_PREF_TYPE)
                put("enabled", prefs.adultContentEnabled)
            },
        )
        ContentLabel.entries.forEach { category ->
            add(
                buildJsonObject {
                    put("\$type", CONTENT_LABEL_PREF_TYPE)
                    put("label", category.value)
                    put("visibility", prefs.visibilityFor(category).wireValue)
                },
            )
        }
    }
}

private const val GET_PREFERENCES_NSID = "app.bsky.actor.getPreferences"
private const val PUT_PREFERENCES_NSID = "app.bsky.actor.putPreferences"
private const val ADULT_CONTENT_PREF_TYPE = "app.bsky.actor.defs#adultContentPref"
private const val CONTENT_LABEL_PREF_TYPE = "app.bsky.actor.defs#contentLabelPref"
