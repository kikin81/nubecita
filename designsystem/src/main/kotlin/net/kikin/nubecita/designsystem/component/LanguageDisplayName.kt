package net.kikin.nubecita.designsystem.component

import java.util.Locale

/**
 * Localized display name for a BCP-47 language [tag], rendered in
 * [locale].
 *
 * [locale] defaults to the JVM's for non-Compose callers, but every
 * composable passes `LocalLocale.current.platformLocale`: the two differ
 * whenever the reader has set a per-app language, and that reader is
 * precisely the one reading language names. Region-qualified tags (`"en-US"`, `"ja-JP"`)
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
fun languageDisplayName(
    tag: String,
    locale: Locale = Locale.getDefault(),
): String =
    Locale
        .forLanguageTag(tag)
        .getDisplayLanguage(locale)
        .replaceFirstChar { it.titlecase(locale) }
