package net.kikin.nubecita.feature.settings.impl

import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/** One open-source library row — a screenshot-friendly projection of the
 * aboutlibraries `Library` model so the stateless content needs no SDK type.
 * [id] is the library's unique maven coordinate, used as the LazyColumn key
 * ([name] is a display label and is NOT unique — multiple libraries share one,
 * e.g. "Annotation"). */
internal data class LicenseRowUi(
    val id: String,
    val name: String,
    val license: String,
    val url: String?,
)

/**
 * Stateful open-source licenses screen (sub-route of About). Loads the library
 * metadata generated at build time by the aboutlibraries Gradle plugin and
 * renders it with the app's own rows (`libraryRow`-style custom rendering).
 * Tapping a library with a URL opens it in a Custom Tab.
 */
@Composable
internal fun AboutLicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The generated JSON is small and this screen is rarely opened, so building
    // once in remember is acceptable. Load via the explicit `R.raw.aboutlibraries`
    // id (NOT `withContext(context)`, which resolves the resource by name via
    // reflection): a real R reference is tracked by the resource shrinker, so
    // `isShrinkResources` doesn't strip the JSON from minified release builds
    // (which previously crashed the licenses screen — nubecita-jkg8).
    val rows: ImmutableList<LicenseRowUi> =
        remember(context) {
            Libs
                .Builder()
                .withJson(context, R.raw.aboutlibraries)
                .build()
                .libraries
                .map { library ->
                    LicenseRowUi(
                        id = library.uniqueId,
                        name = library.name,
                        license = library.licenses.joinToString { it.name },
                        // Treat a present-but-blank website as no link so the row
                        // stays non-clickable (avoids launching an empty Custom Tab).
                        url = library.website?.takeIf { it.isNotBlank() },
                    )
                }
                // Guarantee unique LazyColumn keys: aboutlibraries can list the
                // same coordinate more than once, and the key MUST be unique.
                .distinctBy { it.id }
                .toImmutableList()
        }

    AboutLicensesContent(
        rows = rows,
        onBack = onBack,
        onRowClick = { url ->
            try {
                CustomTabsIntent
                    .Builder()
                    .setShowTitle(true)
                    .build()
                    .launchUrl(context, Uri.parse(url))
            } catch (_: ActivityNotFoundException) {
                // No browser available — silent no-op.
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutLicensesContent(
    rows: ImmutableList<LicenseRowUi>,
    onBack: () -> Unit,
    onRowClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.about_back_content_desc),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            items(items = rows, key = { it.id }) { row ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (row.url != null) Modifier.clickable { onRowClick(row.url) } else Modifier)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (row.license.isNotBlank()) {
                        Text(
                            text = row.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Licenses — light", showBackground = true, heightDp = 600)
@Preview(name = "Licenses — dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutLicensesContentPreview() {
    NubecitaTheme {
        Surface {
            AboutLicensesContent(
                rows =
                    persistentListOf(
                        LicenseRowUi("io.coil-kt:coil", "Coil", "Apache-2.0", "https://coil-kt.github.io"),
                        LicenseRowUi("androidx.media3:media3", "Media3", "Apache-2.0", "https://developer.android.com/media/media3"),
                        LicenseRowUi("io.github.kikin81.atproto:models", "atproto-kotlin", "MIT", null),
                    ),
                onBack = {},
                onRowClick = {},
            )
        }
    }
}
