package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.R

/**
 * Loading body for the People tab. Three skeleton actor-row
 * placeholders. Hand-rolled (no shared shimmer primitive for actors).
 * The parent Column carries a single contentDescription so TalkBack
 * announces "Searching people" instead of three empty rows.
 */
@Composable
internal fun PeopleLoadingBody(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.search_people_loading_content_description)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
    ) {
        repeat(3) {
            ActorRowSkeleton()
        }
    }
}

@Composable
private fun ActorRowSkeleton() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(placeholderColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = 0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = 0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor),
            )
        }
    }
}

@Preview(name = "PeopleLoadingBody — light", showBackground = true)
@Preview(
    name = "PeopleLoadingBody — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PeopleLoadingBodyPreview() {
    NubecitaTheme {
        PeopleLoadingBody()
    }
}
