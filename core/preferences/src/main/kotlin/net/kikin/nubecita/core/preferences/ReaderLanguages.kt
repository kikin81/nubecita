package net.kikin.nubecita.core.preferences

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.common.language.LanguageTags
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The languages this reader understands, and the one a translation should
 * target.
 *
 * It is a *set*, not a language. A reader fluent in English and Spanish wants
 * neither translated, and `Locale.getDefault().language` can only ever name
 * one of them — which is how a bilingual reader ends up being offered a
 * translation of a post they just read perfectly well.
 *
 * **Why the device, and not the account.** The project's standing rule is to
 * prefer atproto state over app-derived state, and the original design for
 * this said to read the account's content-language preference. That
 * preference does not exist: `app.bsky.actor` defines thirteen preference
 * types — adult content, content labels, declared age, feed view, hidden
 * posts, interests, labelers, muted words, personal details, post-interaction
 * settings, saved feeds, thread view, app state — and not one of them is about
 * language. The official client keeps content languages client-local. The rule
 * cannot apply to state the protocol does not model, so the sources here are
 * the device locale list and the reader's own never-translate list.
 */
@Singleton
class ReaderLanguages
    @Inject
    constructor(
        private val preferences: TranslationPreferences,
        private val deviceLocales: DeviceLocales,
    ) {
        /**
         * Every language the reader understands, as bare subtags, ordered with
         * the device's own locales first.
         *
         * Order matters because [targetLanguage] reads the head of this list.
         * The never-translate entries come second: they say "do not translate
         * this", which is not the same as "translate *into* this", and a
         * reader who added Spanish to be left alone should not thereby change
         * what their German posts get translated into.
         */
        val languages: Flow<List<String>> =
            combine(deviceLocales.locales, preferences.neverTranslateLanguages) { device, never ->
                LanguageTags.normalizeAll(device + never)
            }

        /**
         * The language translations are produced in — the reader's primary
         * device locale.
         *
         * Falls back to English only when the device reports nothing usable,
         * which is a state Android does not normally produce; it exists so
         * that a translation target is always nameable rather than nullable at
         * every call site.
         */
        val targetLanguage: Flow<String> =
            languages.map { resolved -> resolved.firstOrNull() ?: FALLBACK_TARGET }

        private companion object {
            const val FALLBACK_TARGET = "en"
        }
    }

/**
 * The device's preferred locales, most-preferred first, as they change.
 *
 * A flow because the reader can change their device language while the app
 * is running. Android announces that as a configuration change; a value read
 * once would keep translating into the old language until the process died,
 * and so would anything app-scoped that outlives the recreated activity.
 *
 * An interface so the resolution rules above can be unit-tested on the JVM:
 * the real implementation reads a process-wide Android singleton that a test
 * cannot meaningfully vary.
 */
interface DeviceLocales {
    /**
     * The reader's configured locales as BCP-47 tags, most-preferred first.
     * Emits the current list on collection and again whenever it changes.
     */
    val locales: Flow<List<String>>
}

/**
 * [DeviceLocales] backed by one app-wide configuration listener.
 *
 * A [StateFlow] held for the process, not a cold `callbackFlow` per
 * collector: callers read it with `.first()` once per post they resolve, and
 * a cold flow would register and unregister a `ComponentCallbacks` on every
 * one of those reads. Here one registration lives for the process and every
 * read is the current value.
 */
@Singleton
internal class SystemDeviceLocales
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) : DeviceLocales {
        override val locales: StateFlow<List<String>> =
            callbackFlow {
                val callbacks =
                    object : ComponentCallbacks {
                        // By the time an application-level callback runs, the
                        // framework has already installed the new default
                        // locale list, so reading it here sees the change.
                        override fun onConfigurationChanged(newConfig: Configuration) {
                            trySend(read())
                        }

                        @Deprecated("Deprecated in API 34; required by the interface.")
                        override fun onLowMemory() = Unit
                    }
                context.registerComponentCallbacks(callbacks)
                awaitClose { context.unregisterComponentCallbacks(callbacks) }
            }.distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, read())

        private fun read(): List<String> {
            // getAdjustedDefault, not getDefault: it reorders the system list
            // against the locales the app actually ships, so the head is a
            // language the reader both prefers and sees the UI in.
            val list = LocaleListCompat.getAdjustedDefault()
            return (0 until list.size()).mapNotNull { index -> list[index]?.language }
        }
    }
