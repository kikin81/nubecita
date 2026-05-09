package net.kikin.nubecita.feature.composer.impl.internal

import java.util.Locale

/**
 * Localized display name for a BCP-47 language [tag], rendered in the
 * JVM's default locale. Region-qualified tags (`"en-US"`, `"ja-JP"`)
 * collapse to the bare language name (`"English"`, `"Japanese"`) —
 * we use [Locale.getDisplayLanguage] rather than
 * [Locale.getDisplayName] so the chip + picker stay consistent with
 * the design doc's chip-label spec ("English" not "English (United
 * States)") and so the picker rows keyed on bare tags
 * (`BLUESKY_LANGUAGE_TAGS`) line up with the chip's label.
 *
 * The first character is title-cased via the JVM default locale —
 * some Java locales (e.g. older builds for German / French) return
 * the lowercased name, which looks wrong as a button label.
 */
internal fun languageDisplayName(tag: String): String =
    Locale
        .forLanguageTag(tag)
        .getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
