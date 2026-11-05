package net.kikin.nubecita.feature.settings.impl

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.preferences.TranslationPreferences
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.translation.TranslationEngine
import net.kikin.nubecita.core.translation.TranslationModelRepository
import net.kikin.nubecita.data.models.DownloadedLanguage
import net.kikin.nubecita.data.models.TranslationModelState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
internal class TranslationViewModelTest {
    @Test
    fun `starts off with nothing configured`() =
        runTest {
            val vm = viewModel()

            runCurrent()
            val state = vm.uiState.value
            assertEquals(false, state.autoTranslateEnabled)
            assertTrue(state.autoTranslate.isEmpty())
            assertTrue(state.neverTranslate.isEmpty())
            assertTrue(state.downloaded.isEmpty())
        }

    @Test
    fun `stored preferences project into state`() =
        runTest {
            val prefs = FakePrefs(initialEnabled = true, initialAuto = setOf("ja", "de"), initialNever = setOf("es"))
            val vm = viewModel(prefs)

            runCurrent()
            val state = vm.uiState.value
            assertEquals(true, state.autoTranslateEnabled)
            assertEquals(listOf("de", "ja"), state.autoTranslate)
            assertEquals(listOf("es"), state.neverTranslate)
        }

    @Test
    fun `toggling the master switch writes it`() =
        runTest {
            val prefs = FakePrefs()
            val vm = viewModel(prefs)
            runCurrent()

            vm.handleEvent(TranslationEvent.AutoTranslateToggled(true))
            runCurrent()

            assertEquals(true, prefs.enabled.value)
        }

    @Test
    fun `re-toggling to the current value does not write`() =
        runTest {
            // NubecitaListItem fires on every tap; the no-op guard lives here
            // so it survives a swap of the control.
            val prefs = FakePrefs()
            val vm = viewModel(prefs)
            runCurrent()

            vm.handleEvent(TranslationEvent.AutoTranslateToggled(false))
            runCurrent()

            assertEquals(0, prefs.writes)
        }

    @Test
    fun `the picker offers every language the engine supports`() =
        runTest {
            val vm = viewModel(supported = listOf("ja", "de", "es"))
            runCurrent()

            // Read from the engine, not a hand-kept list: a picker that offers
            // a language `supports` would reject is a dead end the reader only
            // discovers after choosing it.
            assertEquals(listOf("ja", "de", "es"), vm.uiState.value.availableLanguages)
        }

    @Test
    fun `a language picked for auto-translate is persisted`() =
        runTest {
            // The regression this screen shipped with: the auto list could only
            // ever shrink, so automatic translation could never be switched on
            // at all. Nothing added a language, and the feed gates on this set
            // being non-empty.
            val prefs = FakePrefs()
            val vm = viewModel(prefs)
            runCurrent()

            vm.handleEvent(TranslationEvent.AutoTranslateLanguagesChanged(setOf("de")))
            runCurrent()

            assertEquals(setOf("de"), prefs.auto.value)
        }

    @Test
    fun `a language cannot be in both lists at once`() =
        runTest {
            // "Translate this automatically" and "I read this" are
            // contradictory. Leaving both set would make the feed's behaviour
            // depend on which check happened to run first.
            val prefs = FakePrefs(initialNever = setOf("ja"))
            val vm = viewModel(prefs)
            runCurrent()

            vm.handleEvent(TranslationEvent.AutoTranslateLanguagesChanged(setOf("ja")))
            runCurrent()

            assertEquals(setOf("ja"), prefs.auto.value)
            assertEquals(emptySet<String>(), prefs.never.value)
        }

    @Test
    fun `adding to never-translate removes it from auto-translate`() =
        runTest {
            val prefs = FakePrefs(initialAuto = setOf("ja"))
            val vm = viewModel(prefs)
            runCurrent()

            vm.handleEvent(TranslationEvent.NeverTranslateLanguagesChanged(setOf("ja")))
            runCurrent()

            assertEquals(setOf("ja"), prefs.never.value)
            assertEquals(emptySet<String>(), prefs.auto.value)
        }

    @Test
    fun `deleting a pack also stops translating that language`() =
        runTest {
            // Otherwise the feed keeps asking for a translation whose model is
            // gone, and the reader gets a shimmer that never resolves.
            val prefs = FakePrefs(initialAuto = setOf("ja", "de"))
            val models = FakeModels(mapOf("ja" to 62_000_000L, "de" to 45_600_000L))
            val vm = viewModel(prefs, models)
            runCurrent()

            vm.handleEvent(TranslationEvent.LanguageDeleted("ja"))
            runCurrent()

            assertEquals(listOf("ja"), models.deleted)
            assertEquals(setOf("de"), prefs.auto.value)
        }

    @Test
    fun `the storage total is the sum of the packs`() =
        runTest {
            val models = FakeModels(mapOf("ja" to 62_000_000L, "de" to 45_600_000L))
            val vm = viewModel(models = models)

            runCurrent()
            assertEquals(107_600_000L, vm.uiState.value.totalBytes)
        }

    @Test
    fun `a failed write surfaces a save error`() =
        runTest {
            val prefs = FakePrefs(failWrites = true)
            val vm = viewModel(prefs)
            runCurrent()

            vm.effects.test {
                vm.handleEvent(TranslationEvent.AutoTranslateToggled(true))
                runCurrent()
                assertEquals(TranslationEffect.ShowSaveError, awaitItem())
            }
        }

    private fun viewModel(
        prefs: TranslationPreferences = FakePrefs(),
        models: TranslationModelRepository = FakeModels(),
        supported: List<String> = listOf("ja", "de", "es"),
    ) = TranslationViewModel(prefs, models, FakeEngine(supported))

    /**
     * Only [TranslationEngine.supportedLanguages] matters here — the Translation
     * screen never translates anything, it only lists what could be.
     */
    private class FakeEngine(
        private val supported: List<String>,
    ) : TranslationEngine {
        override fun supportedLanguages(): List<String> = supported

        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String,
        ): Result<String> = Result.success(text)

        override fun supports(
            sourceLanguage: String,
            targetLanguage: String,
        ): Boolean = sourceLanguage in supported && targetLanguage in supported

        override fun release() = Unit
    }

    private class FakePrefs(
        initialEnabled: Boolean = false,
        initialAuto: Set<String> = emptySet(),
        initialNever: Set<String> = emptySet(),
        private val failWrites: Boolean = false,
    ) : TranslationPreferences {
        val enabled = MutableStateFlow(initialEnabled)
        val auto = MutableStateFlow(initialAuto)
        val never = MutableStateFlow(initialNever)
        var writes: Int = 0
            private set

        override val autoTranslateEnabled: Flow<Boolean> = this.enabled.asStateFlow()

        override suspend fun setAutoTranslateEnabled(enabled: Boolean) {
            record()
            this.enabled.value = enabled
        }

        override val autoTranslateLanguages: Flow<Set<String>> = auto.asStateFlow()

        override suspend fun setAutoTranslateLanguages(languages: Set<String>) {
            record()
            this.auto.value = languages
        }

        override val neverTranslateLanguages: Flow<Set<String>> = never.asStateFlow()

        override suspend fun setNeverTranslateLanguages(languages: Set<String>) {
            record()
            this.never.value = languages
        }

        private fun record() {
            writes++
            if (failWrites) error("write failed")
        }
    }

    private class FakeModels(
        packs: Map<String, Long> = emptyMap(),
    ) : TranslationModelRepository {
        private val backing = MutableStateFlow(packs)
        val deleted: MutableList<String> = mutableListOf()

        override val states: Flow<Map<String, TranslationModelState>> =
            backing.map { packs -> packs.mapValues { TranslationModelState.Downloaded(it.value) } }

        override val downloaded: Flow<List<DownloadedLanguage>> =
            backing.map { packs -> packs.map { DownloadedLanguage(it.key, it.value) }.sortedBy { it.languageTag } }

        override suspend fun request(languageTag: String) = Unit

        override suspend fun delete(languageTag: String) {
            deleted += languageTag
            backing.value = backing.value - languageTag
        }

        override suspend fun isDownloaded(languageTag: String): Boolean = languageTag in backing.value
    }
}
