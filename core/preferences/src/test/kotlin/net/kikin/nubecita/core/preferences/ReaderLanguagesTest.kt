package net.kikin.nubecita.core.preferences

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The resolution rules for "what languages does this reader understand".
 *
 * The case that matters most is the bilingual one. A reader who reads English
 * and Spanish must have *both* left alone — `Locale.getDefault().language`
 * can only name one, which is exactly how a bilingual reader ends up being
 * offered a translation of a post they read fine.
 */
internal class ReaderLanguagesTest {
    @Test
    fun `device locales alone resolve when nothing is configured`() =
        runTest {
            val subject = readerLanguages(device = listOf("en-US"))

            subject.languages.test {
                assertEquals(listOf("en"), awaitItem())
            }
        }

    @Test
    fun `a bilingual reader keeps both languages`() =
        runTest {
            // The motivating case: English device, reader also reads Spanish
            // and has said so. Neither may be offered for translation.
            val subject = readerLanguages(device = listOf("en-US"), never = setOf("es"))

            subject.languages.test {
                assertEquals(listOf("en", "es"), awaitItem())
            }
        }

    @Test
    fun `every device locale counts, not just the first`() =
        runTest {
            val subject = readerLanguages(device = listOf("es-419", "en-GB", "pt-BR"))

            subject.languages.test {
                assertEquals(listOf("es", "en", "pt"), awaitItem())
            }
        }

    @Test
    fun `region subtags are reduced to bare languages`() =
        runTest {
            val subject = readerLanguages(device = listOf("es-419"), never = setOf("pt-BR"))

            subject.languages.test {
                assertEquals(listOf("es", "pt"), awaitItem())
            }
        }

    @Test
    fun `a never-translate entry that duplicates a device locale is not repeated`() =
        runTest {
            val subject = readerLanguages(device = listOf("en-US"), never = setOf("en"))

            subject.languages.test {
                assertEquals(listOf("en"), awaitItem())
            }
        }

    @Test
    fun `device locales lead, so the translation target is not changed by never-translate`() =
        runTest {
            // Adding Spanish to "leave me alone" must not make Spanish the
            // language German posts get translated INTO.
            val subject = readerLanguages(device = listOf("en-US"), never = setOf("es"))

            subject.targetLanguage.test {
                assertEquals("en", awaitItem())
            }
        }

    @Test
    fun `the target is the reader's primary device locale`() =
        runTest {
            val subject = readerLanguages(device = listOf("ja-JP", "en-US"))

            subject.targetLanguage.test {
                assertEquals("ja", awaitItem())
            }
        }

    @Test
    fun `the target falls back to English when the device reports nothing usable`() =
        runTest {
            val subject = readerLanguages(device = emptyList())

            subject.targetLanguage.test {
                assertEquals("en", awaitItem())
            }
        }

    @Test
    fun `legacy device codes resolve to their modern equivalents`() =
        runTest {
            // Android still reports `iw` for Hebrew. Left alone, nothing would
            // ever match it and the reader's own language would be offered for
            // translation.
            val subject = readerLanguages(device = listOf("iw-IL"))

            subject.languages.test {
                assertEquals(listOf("he"), awaitItem())
            }
        }

    @Test
    fun `editing the never-translate list re-emits`() =
        runTest {
            val never = MutableStateFlow(emptySet<String>())
            val subject = ReaderLanguages(FakeTranslationPreferences(never = never), FakeDeviceLocales(listOf("en-US")))

            subject.languages.test {
                assertEquals(listOf("en"), awaitItem())
                never.value = setOf("ja")
                assertEquals(listOf("en", "ja"), awaitItem())
            }
        }

    @Test
    fun `a device language change re-targets translations`() =
        runTest {
            // The reader switches their phone from English to Japanese while
            // the app is running. Translations must follow, not keep targeting
            // English until the process dies.
            val device = FakeDeviceLocales(listOf("en-US"))
            val subject = ReaderLanguages(FakeTranslationPreferences(never = MutableStateFlow(emptySet())), device)

            subject.targetLanguage.test {
                assertEquals("en", awaitItem())
                device.locales.value = listOf("ja-JP")
                assertEquals("ja", awaitItem())
            }
        }

    private fun readerLanguages(
        device: List<String>,
        never: Set<String> = emptySet(),
    ) = ReaderLanguages(
        preferences = FakeTranslationPreferences(never = MutableStateFlow(never)),
        deviceLocales = FakeDeviceLocales(device),
    )

    private class FakeDeviceLocales(
        initial: List<String>,
    ) : DeviceLocales {
        override val locales = MutableStateFlow(initial)
    }

    private class FakeTranslationPreferences(
        private val never: MutableStateFlow<Set<String>>,
    ) : TranslationPreferences {
        override val autoTranslateEnabled: Flow<Boolean> = MutableStateFlow(false).asStateFlow()

        override suspend fun setAutoTranslateEnabled(enabled: Boolean) = Unit

        override val autoTranslateLanguages: Flow<Set<String>> = MutableStateFlow(emptySet<String>()).asStateFlow()

        override suspend fun setAutoTranslateLanguages(languages: Set<String>) = Unit

        override val neverTranslateLanguages: Flow<Set<String>> = never.asStateFlow()

        override suspend fun setNeverTranslateLanguages(languages: Set<String>) {
            never.value = languages
        }

        override val autoTranslateOffered: Flow<Set<String>> = MutableStateFlow(emptySet<String>()).asStateFlow()

        override suspend fun setAutoTranslateOffered(languages: Set<String>) = Unit
    }
}
