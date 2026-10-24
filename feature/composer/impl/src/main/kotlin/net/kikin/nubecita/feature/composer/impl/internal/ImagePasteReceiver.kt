package net.kikin.nubecita.feature.composer.impl.internal

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType

/**
 * Takes the image parts of a paste or keyboard insertion out of the composer's
 * text field and hands their `content://` URIs to [onPasteImages]. Everything
 * else (plain text, a link) is returned unconsumed, so the text field still
 * pastes it as text.
 *
 * Attaching `Modifier.contentReceiver` is also what makes the text field
 * advertise image support to the keyboard: without it, Gboard reports that
 * image insertion isn't supported.
 *
 * The second argument to [onPasteImages] is the platform payload carrying the
 * keyboard's `InputContentInfo`. The IME's read grant is revoked when that
 * object is garbage collected, so the receiver must keep it reachable until
 * the URIs have been copied.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ImagePasteReceiver(
    private val onPasteImages: (uris: List<Uri>, grantHolder: Any?) -> Unit,
) : ReceiveContentListener {
    override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
        if (!transferableContent.hasMediaType(MediaType.Image)) return transferableContent
        val uris = mutableListOf<Uri>()
        val remaining =
            transferableContent.consume { item ->
                val uri = item.uri
                if (uri != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    uris += uri
                    true
                } else {
                    false
                }
            }
        if (uris.isNotEmpty()) onPasteImages(uris, transferableContent.platformTransferableContent)
        return remaining
    }
}
