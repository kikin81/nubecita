package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [ThreadReadMore]: the thread line running in from
 * above, out below, neither, and the loading state. The label column must stay
 * where a [PostCard]'s text starts.
 */
@PreviewTest
@Preview(name = "thread-read-more-light", showBackground = true)
@Preview(name = "thread-read-more-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun ThreadReadMoreScreenshot() {
    Column {
        ThreadReadMore(label = "Read earlier posts", onClick = {}, connectBelow = true)
        ThreadReadMore(label = "Read more replies", onClick = {}, connectAbove = true)
        ThreadReadMore(label = "Show more replies", onClick = {})
        ThreadReadMore(label = "Show more replies", onClick = {}, isLoading = true)
    }
}
