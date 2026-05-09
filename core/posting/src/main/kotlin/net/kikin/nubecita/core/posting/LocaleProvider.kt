package net.kikin.nubecita.core.posting

import java.util.Locale
import javax.inject.Inject

/**
 * Reads the device's primary locale as a BCP-47 language tag.
 *
 * Extracted as an interface so consumers — `DefaultPostingRepository`,
 * `ComposerViewModel`, screen-level UI tests — can inject a fixed
 * locale instead of reading the JVM's `Locale.getDefault()` (which is
 * per-process global mutable state).
 */
interface LocaleProvider {
    /**
     * BCP-47 tag for the device's primary locale (`en-US`, `es-MX`,
     * `ja-JP`, etc). Never null, never empty in practice — the JVM
     * always has a default locale.
     */
    fun primaryLanguageTag(): String
}

internal class JvmLocaleProvider
    @Inject
    constructor() : LocaleProvider {
        override fun primaryLanguageTag(): String = Locale.getDefault().toLanguageTag()
    }
