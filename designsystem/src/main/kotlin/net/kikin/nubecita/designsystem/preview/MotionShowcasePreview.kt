package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.extendedShape

@Composable
private fun MotionShowcase(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Motion can't be previewed statically.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Run on emulator/device and compare against\nopenspec/references/design-system/preview/spacing-motion.html.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {},
            shape = MaterialTheme.extendedShape.pill,
        ) {
            Text("Pill button — M3 Expressive default shape")
        }
    }
}

@Preview(name = "Motion — Light", showBackground = true)
@Composable
private fun MotionShowcasePreviewLight() {
    NubecitaTheme(darkTheme = false, dynamicColor = false) {
        MotionShowcase()
    }
}

@Preview(name = "Motion — Dark", showBackground = true)
@Composable
private fun MotionShowcasePreviewDark() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        MotionShowcase()
    }
}
