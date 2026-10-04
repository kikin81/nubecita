package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Material 3 Expressive filled icon button with smooth shape morphing on press.
 *
 * Centralizes the M3 Expressive [FilledIconButton] pattern with [IconButtonDefaults.shapes]
 * across the app so caller sites don't need to re-import experimental APIs or hand-wire
 * expressive shape morphing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NubecitaExpressiveIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val semanticsModifier =
        if (contentDescription != null) {
            modifier.semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
        } else {
            modifier
        }

    FilledIconButton(
        onClick = onClick,
        modifier = semanticsModifier,
        enabled = enabled,
        shapes = shapes,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        icon()
    }
}

@Preview(name = "Enabled", showBackground = true)
@Composable
private fun NubecitaExpressiveIconButtonEnabledPreview() {
    NubecitaTheme {
        NubecitaExpressiveIconButton(
            onClick = {},
            icon = { NubecitaIcon(name = NubecitaIconName.Send, contentDescription = null, filled = true) },
            contentDescription = "Send",
        )
    }
}

@Preview(name = "Disabled", showBackground = true)
@Composable
private fun NubecitaExpressiveIconButtonDisabledPreview() {
    NubecitaTheme {
        NubecitaExpressiveIconButton(
            onClick = {},
            icon = { NubecitaIcon(name = NubecitaIconName.Send, contentDescription = null, filled = true) },
            contentDescription = "Send",
            enabled = false,
        )
    }
}
