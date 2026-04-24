package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.extendedTypography

@Composable
fun TypographyScale(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        val typography = MaterialTheme.typography
        Text(text = "Display Large", style = typography.displayLarge)
        Text(text = "Display Medium", style = typography.displayMedium)
        Text(text = "Display Small", style = typography.displaySmall)
        Text(text = "Headline Large", style = typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
        Text(text = "Headline Medium", style = typography.headlineMedium)
        Text(text = "Headline Small", style = typography.headlineSmall)
        Text(text = "Title Large", style = typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(text = "Title Medium", style = typography.titleMedium)
        Text(text = "Title Small", style = typography.titleSmall)
        Text(text = "Body Large (17/26)", style = typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        Text(text = "Body Medium", style = typography.bodyMedium)
        Text(text = "Body Small", style = typography.bodySmall)
        Text(text = "Label Large", style = typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Text(text = "Label Medium", style = typography.labelMedium)
        Text(text = "Label Small", style = typography.labelSmall)
        Text(text = "Mono (JetBrains Mono)", style = MaterialTheme.extendedTypography.mono, modifier = Modifier.padding(top = 16.dp))
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun TypographyScalePreviewLight() {
    NubecitaTheme(darkTheme = false, dynamicColor = false) {
        Surface {
            TypographyScale()
        }
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun TypographyScalePreviewDark() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        Surface {
            TypographyScale()
        }
    }
}
