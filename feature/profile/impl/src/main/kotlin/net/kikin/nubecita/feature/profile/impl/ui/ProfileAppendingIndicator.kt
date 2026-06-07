package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator

@Composable
internal fun ProfileAppendingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        NubecitaWavyProgressIndicator(modifier = Modifier.size(24.dp))
    }
}
