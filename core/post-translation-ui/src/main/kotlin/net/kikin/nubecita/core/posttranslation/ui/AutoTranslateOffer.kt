package net.kikin.nubecita.core.posttranslation.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import java.util.Locale

/**
 * The offer to translate a language automatically, when [handle] has one.
 *
 * Place it once per screen, outside the list: the offer belongs to the screen,
 * and a dialog anchored to a post would vanish when that post scrolled out of
 * composition.
 */
@Composable
fun AutoTranslateOffer(handle: PostTranslationsHandle?) {
    val source = handle?.autoOffer ?: return
    AutoTranslateOfferDialog(
        sourceLanguage = source,
        onAccept = handle::acceptAutoOffer,
        onDecline = handle::declineAutoOffer,
    )
}

/**
 * No download size. The pack isn't on disk when this is asked, which is the
 * point of asking, and nothing can measure one that isn't there, so the copy
 * says how it downloads rather than quoting a number that would drift.
 */
@Composable
private fun AutoTranslateOfferDialog(
    sourceLanguage: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val language =
        remember(sourceLanguage, locale) {
            Locale.forLanguageTag(sourceLanguage).getDisplayLanguage(locale).ifBlank { sourceLanguage }
        }
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.post_translation_auto_offer_title, language)) },
        text = { Text(stringResource(R.string.post_translation_auto_offer_body, language)) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.post_translation_auto_offer_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.post_translation_auto_offer_decline))
            }
        },
    )
}
