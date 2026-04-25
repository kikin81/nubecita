package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Brand wrapper around Coil 3's [AsyncImage].
 *
 * Centralizes the loading + error visual treatment (a flat Material 3
 * Expressive surface tile) so every image in the app degrades the same
 * way and call sites don't re-implement placeholder/error painters.
 *
 * Reads the global [coil3.SingletonImageLoader] configured by the
 * application — see `app/.../data/CoilModule.kt`.
 */
@Composable
fun NubecitaAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest)
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
    )
}

@Preview(name = "Async image — placeholder", showBackground = true)
@Composable
private fun NubecitaAsyncImagePreview() {
    NubecitaTheme {
        // No model in preview — renders placeholder ColorPainter, which is the
        // visual we ship for offline / error / no-data states.
        NubecitaAsyncImage(
            model = null,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
    }
}
