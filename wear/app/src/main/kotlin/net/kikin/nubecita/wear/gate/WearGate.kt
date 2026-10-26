package net.kikin.nubecita.wear.gate

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import net.kikin.nubecita.wear.R

/**
 * Renders [WearGateState]: [content] once signed in, otherwise the screen that
 * explains what is missing. Stateless, so every status is screenshot-testable.
 */
@Composable
fun WearGate(
    state: WearGateState,
    onEvent: (WearGateEvent) -> Unit,
    content: @Composable (account: WearGateStatus.SignedIn) -> Unit,
) {
    when (val status = state.status) {
        // Brief: the stored session is read locally. Plain black, no spinner.
        WearGateStatus.Loading -> Unit
        is WearGateStatus.SignedIn -> content(status)
        WearGateStatus.PhoneAppMissing ->
            GateMessageScreen(
                title = stringResource(R.string.wear_gate_phone_app_missing_title),
                body = stringResource(R.string.wear_gate_phone_app_missing_body),
                actionLabel = stringResource(R.string.wear_gate_install_on_phone),
                onAction = { onEvent(WearGateEvent.OpenOnPhoneClicked) },
            )
        WearGateStatus.NeedsAndroidPhone ->
            GateMessageScreen(
                title = stringResource(R.string.wear_gate_needs_android_title),
                body = stringResource(R.string.wear_gate_needs_android_body),
            )
        WearGateStatus.SignedOut ->
            GateMessageScreen(
                title = stringResource(R.string.wear_gate_signed_out_title),
                body = stringResource(R.string.wear_gate_signed_out_body),
                actionLabel = stringResource(R.string.wear_gate_open_on_phone),
                onAction = { onEvent(WearGateEvent.OpenOnPhoneClicked) },
            )
        WearGateStatus.UpdateRequired ->
            GateMessageScreen(
                title = stringResource(R.string.wear_gate_update_title),
                body = stringResource(R.string.wear_gate_update_body),
            )
    }
    val text = OpenOnPhoneDialogDefaults.text
    val style = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = state.showOpenOnPhone,
        onDismissRequest = { onEvent(WearGateEvent.OpenOnPhoneDismissed) },
        curvedText = { openOnPhoneDialogCurvedText(text = text, style = style) },
    )
}
