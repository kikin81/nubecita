package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.window.core.layout.WindowSizeClass
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.ReplyAudience

/**
 * Adaptive wrapper around [AudiencePickerContent]. At Compact width uses
 * [ModalBottomSheet]; at Medium / Expanded uses [Popup] over an M3 [Surface]
 * card — the same width-class branching as [LanguagePicker] / [ComposerDiscardDialog],
 * sidestepping the double-scrim when the composer is itself a Compose `Dialog`.
 *
 * Owns the picker's local draft ([draft] audience + [saveAsDefault]); selection
 * taps mutate the draft without dispatching to the VM. Only [onConfirm] commits
 * — it returns the chosen [PostAudience] and whether to persist it as the
 * account default. Drag-down, scrim-tap, back-press, and Cancel map to
 * [onDismiss] without commit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun AudiencePicker(
    initialAudience: PostAudience,
    onConfirm: (audience: PostAudience, saveAsDefault: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialSaveAsDefault: Boolean = false,
) {
    var draft by rememberSaveable(initialAudience, stateSaver = PostAudienceSaver) {
        mutableStateOf(initialAudience)
    }
    var saveAsDefault by rememberSaveable(initialSaveAsDefault) { mutableStateOf(initialSaveAsDefault) }

    val isCompact =
        !currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    // Content is inlined into both branches (rather than hoisted into a shared
    // lambda) to match LanguagePicker. Reset clears the whole picker — audience
    // AND the save-as-default checkbox — so "reset" matches user expectation.
    if (isCompact) {
        val sheetState =
            rememberBottomSheetState(
                SheetValue.Hidden,
                setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
        ) {
            AudiencePickerContent(
                audience = draft,
                saveAsDefault = saveAsDefault,
                onAudienceChange = { draft = it },
                onSaveAsDefaultChange = { saveAsDefault = it },
                onReset = {
                    draft = PostAudience.DEFAULT
                    saveAsDefault = false
                },
                onConfirm = { onConfirm(draft, saveAsDefault) },
                onDismiss = onDismiss,
            )
        }
    } else {
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = onDismiss,
            properties =
                PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
        ) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 480.dp),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    AudiencePickerContent(
                        audience = draft,
                        saveAsDefault = saveAsDefault,
                        onAudienceChange = { draft = it },
                        onSaveAsDefaultChange = { saveAsDefault = it },
                        onReset = {
                            draft = PostAudience.DEFAULT
                            saveAsDefault = false
                        },
                        onConfirm = { onConfirm(draft, saveAsDefault) },
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

private const val REPLY_KIND_EVERYONE = 0
private const val REPLY_KIND_NOBODY = 1
private const val REPLY_KIND_COMBINATION = 2

private fun Boolean.toInt(): Int = if (this) 1 else 0

/**
 * Saves a [PostAudience] across configuration change / process death as a flat
 * `Int` list: `[replyKind, followers, following, mentioned, allowQuotes]`, where
 * `replyKind` is one of the `REPLY_KIND_*` codes.
 */
private val PostAudienceSaver: Saver<PostAudience, Any> =
    listSaver<PostAudience, Int>(
        save = { audience ->
            val reply = audience.reply
            val combination = reply as? ReplyAudience.Combination
            listOf(
                when (reply) {
                    ReplyAudience.Everyone -> REPLY_KIND_EVERYONE
                    ReplyAudience.Nobody -> REPLY_KIND_NOBODY
                    is ReplyAudience.Combination -> REPLY_KIND_COMBINATION
                },
                (combination?.followers == true).toInt(),
                (combination?.following == true).toInt(),
                (combination?.mentioned == true).toInt(),
                audience.allowQuotes.toInt(),
            )
        },
        restore = { v ->
            val reply =
                when (v[0]) {
                    REPLY_KIND_EVERYONE -> ReplyAudience.Everyone
                    REPLY_KIND_NOBODY -> ReplyAudience.Nobody
                    else -> ReplyAudience.Combination(v[1] == 1, v[2] == 1, v[3] == 1)
                }
            PostAudience(reply = reply, allowQuotes = v[4] == 1)
        },
    )
