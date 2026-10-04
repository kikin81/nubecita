package net.kikin.nubecita.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Brand send button with Material 3 Expressive styling and shape morphing.
 *
 * Sits next to message and composer inputs, providing a primary-filled circular
 * affordance when active that morphs smoothly on tap.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NubecitaSendButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = stringResource(R.string.designsystem_action_send),
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    colors: IconButtonColors =
        IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        ),
) {
    NubecitaExpressiveIconButton(
        onClick = onClick,
        icon = {
            NubecitaIcon(
                name = NubecitaIconName.Send,
                contentDescription = null,
                filled = true,
            )
        },
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
        shapes = shapes,
        colors = colors,
    )
}

@Preview(name = "Enabled", showBackground = true)
@Composable
private fun NubecitaSendButtonEnabledPreview() {
    NubecitaTheme {
        NubecitaSendButton(
            onClick = {},
            enabled = true,
        )
    }
}

@Preview(name = "Disabled", showBackground = true)
@Composable
private fun NubecitaSendButtonDisabledPreview() {
    NubecitaTheme {
        NubecitaSendButton(
            onClick = {},
            enabled = false,
        )
    }
}
