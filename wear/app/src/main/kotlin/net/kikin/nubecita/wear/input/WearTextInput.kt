package net.kikin.nubecita.wear.input

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import timber.log.Timber

/**
 * The system text input (W6): one screen offering voice, keyboard and emoji,
 * through `RemoteInputIntentHelper`. The system owns dictation, so the watch
 * needs no microphone permission and runs no recognizer of its own.
 *
 * Returns the function that opens it. [onText] gets the trimmed text; a
 * cancelled or empty input calls nothing.
 */
@Composable
fun rememberWearTextInput(
    label: String,
    onText: (String) -> Unit,
): () -> Unit {
    val currentOnText by rememberUpdatedState(onText)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val text =
                result.data
                    ?.let { RemoteInput.getResultsFromIntent(it) }
                    ?.getCharSequence(RESULT_KEY)
                    ?.toString()
                    ?.trim()
            if (!text.isNullOrEmpty()) currentOnText(text)
        }
    return remember(label, launcher) {
        {
            try {
                launcher.launch(textInputIntent(label))
            } catch (error: ActivityNotFoundException) {
                Timber.w(error, "no system text input on this watch")
            }
        }
    }
}

private fun textInputIntent(label: String): Intent {
    val input =
        RemoteInput
            .Builder(RESULT_KEY)
            .setLabel(label)
            .wearableExtender {
                setEmojisAllowed(true)
                setInputActionType(EditorInfo.IME_ACTION_SEND)
            }.build()
    return RemoteInputIntentHelper.createActionRemoteInputIntent().also { RemoteInputIntentHelper.putRemoteInputsExtra(it, listOf(input)) }
}

private const val RESULT_KEY = "nubecita_text"
