@file:Suppress("ktlint:compose:modifier-missing-check")

package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.extendedShape

@Composable
fun ShapeBox(
    name: String,
    shape: Shape,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, shape),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
fun ShapeScale(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        val shapes = MaterialTheme.shapes
        ShapeBox("Extra Small (4dp)", shapes.extraSmall)
        ShapeBox("Small (8dp)", shapes.small)
        ShapeBox("Medium (12dp)", shapes.medium)
        ShapeBox("Large (16dp)", shapes.large)
        ShapeBox("Extra Large (28dp)", shapes.extraLarge)
        ShapeBox("2XL (36dp)", MaterialTheme.extendedShape.extraExtraLarge)
        ShapeBox("Pill", MaterialTheme.extendedShape.pill)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ShapeScalePreviewLight() {
    NubecitaTheme(darkTheme = false, dynamicColor = false) {
        Surface {
            ShapeScale()
        }
    }
}
